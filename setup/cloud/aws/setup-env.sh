#!/bin/bash
set -u
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

awsProfileFile=aws-profile #optional file, relative to working directory; contains name of AWS profile to use

usage() {
  echo 'Sets up the infrastructure for an environment such as dev or prod.' >&2
  echo 'Usage: setup-env <env> <cidr-block-16-prefix> [--stage {dev|qa|prod}] [--profiles-active <profiles>]' >&2
  echo 'Example: setup-env dev 10.1 --profiles-active dev,test-data' >&2
  echo 'Example: setup-env prod 10.0 --stage prod --profiles-active prod,example-data' >&2
  echo 'The stage may determine special behavior of the deployed stack; in particular `prod` may retain resources and/or prevent resources from easily being deleted.' >&2
  echo 'The stage defaults to `dev` unless the environment name is `prod`, in which case the stage defaults to `prod`.' >&2
  echo 'If no active profiles are specified, appropriate defaults will be provided based upon the determined stage.' >&2
  echo 'The AWS profile specified in the `aws-profile` file will be used, if present.' >&2
  echo 'Note: Stack name will be in the form `flange-<env>`.' >&2
}

args=()
stage=""
profilesActive=""
while (( $# > 0 )); do
  case "$1" in
    --profiles-active) profilesActive="$2"; shift; shift;;
    --stage) stage="$2"; shift; shift;;
    --help) usage; exit 0;;
    --) shift; while (( $# > 0 )); do args+=("$1"); shift; done; break;;
    --*)
      echo "$(tput bold)Bad parameter: $1$(tput sgr0)" >&2
      usage; exit 1;;
    *) args+=("$1"); shift;;
  esac
done

if (( ${#args[@]} != 2 )); then
  echo "$(tput bold)Incorrect number of arguments: ${#args[@]}$(tput sgr0)" >&2
  usage
  exit 1
fi

env=${args[0]}
vpcCidrBlock16Prefix=${args[1]}

if [[ -z $stage ]]; then
  if [[ $env == "prod" ]]; then
    stage="prod"
  else
    stage="dev"
  fi
fi

if [[ -z $profilesActive ]]; then
  case $stage in
    dev) profilesActive=dev ;;
    qa) profilesActive=qa ;;
    prod) profilesActive=prod ;;
  esac
fi

if [[ -f $awsProfileFile ]]; then
  awsProfile="$(< $awsProfileFile)"
  awsProfileOption="--profile $awsProfile"
else
  awsProfile=""
  awsProfileOption=""
fi

echo "Setting up environment $(tput bold)$env$(tput sgr0) ..."
echo
echo "Stage: $stage"
echo "Active profiles: $profilesActive"
if [[ -n $awsProfile ]]; then
  echo "AWS Profile: $awsProfile"
fi

stackName=flange-$env
aws cloudformation deploy \
    $awsProfileOption \
    --stack-name $stackName \
    --template-file $SCRIPT_DIR/env.cloudformation.yaml \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides FlangeEnv=$env FlangeStage=$stage FlangeProfilesActive=$profilesActive VpcCidrBlock16Prefix=$vpcCidrBlock16Prefix
