#!/bin/bash
# Flange CLI (Bash version)
# Copyright © 2023 GlobalMentor, Inc.
# Requires `org.apache.maven.plugins:maven-help-plugin:3.1.0` or later.

set -u
shopt -s nullglob
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

usage() {
  echo 'Flange CLI (Bash version)' >&2
  echo
  echo 'Deploys cloud projects to an environment such as dev or prod.' >&2
  echo "Usage: $(tput bold)flange cloud deploy <env> [--dry-run]$(tput sgr0)" >&2
  echo 'Example: flange cloud deploy' >&2
  echo 'The AWS profile specified in the `aws-profile` file will be used, if present.' >&2
  echo 'Depends on the `flange-<env>` CloudFormation stack.' >&2
  echo
  echo 'Executes an application' >&2
  echo "Usage: $(tput bold)flange exec <main-class> [<args>...]$(tput sgr0)" >&2
  echo 'Example: flange exec org.example.package.Application' >&2
  echo 'Single quotes are not currently supported in arguments.' >&2
}

args=()
dryRun=0
while (( $# > 0 )); do
  case "$1" in
    --dry-run) dryRun=1; shift;;
    --help) usage; exit 0;;
    --) shift; while (( $# > 0 )); do args+=("$1"); shift; done; break;;
    --*)
      echo "$(tput bold)Bad parameter: $1$(tput sgr0)" >&2
      usage; exit 1;;
    *) args+=("$1"); shift;;
  esac
done

if (( ${#args[@]} <1)); then
  echo "$(tput bold)Incorrect number of arguments: ${#args[@]}$(tput sgr0)" >&2
  usage
  exit 1
fi

command=${args[0]}

# TODO check for AWS profile if `--platform aws` is indicated
awsProfileFile=aws-profile #optional file, relative to working directory; contains name of AWS profile to use
if [[ -f $awsProfileFile ]]; then
  awsProfile="$(< $awsProfileFile)"
  awsProfileOption="--profile $awsProfile"
else
  awsProfile=""
  awsProfileOption=""
fi

## `flange cloud`

cloud() {
  if (( ${#args[@]} <2 )); then
    echo "$(tput bold)Incorrect number of arguments: ${#args[@]}$(tput sgr0)" >&2
    usage
    exit 1
  fi

  subcommand=${args[1]}
  case $subcommand in
    deploy) cloudDeploy;;
    *)
      echo "$(tput bold)Unknown subcommand: $subcommand$(tput sgr0)" >&2
      usage
      exit 1
      ;;
  esac
}

### `flange cloud deploy`

cloudDeploy() {
  if (( ${#args[@]} <3 )); then
    echo "$(tput bold)Missing cloud deployment environment identifier.$(tput sgr0)" >&2
    usage
    exit 1
  fi

  echo -n "$(tput bold)Flange$(tput sgr0)"
  if (( $dryRun )); then
    echo -n " $(tput rev)(dry run)$(tput sgr0)"
  fi
  echo

  env=${args[2]}
  echo "Deploying projects to $(tput bold)$env$(tput sgr0) environment ..."
  if [[ -n $awsProfile ]]; then
    echo
    echo "AWS Profile: $awsProfile"
  fi
  envStackName=flange-$env
  stagingBucketName=$(aws cloudformation describe-stacks $awsProfileOption --stack-name $envStackName --query 'Stacks[0].Outputs[?OutputKey==`StagingBucketName`].OutputValue' --output text) || return
  cloudDeployTraverse .
}

#/*
# * Recursively performs deployment of a directory level.
# * Only considers Maven project directories.
# * @param $1 The current directory level being traversed.
# */
cloudDeployTraverse() {
  path=$1
  if [[ -d $path && -f $path/pom.xml ]]; then # only traverse Maven project directories

    # upload AWS Lambda Zip files to staging
    for awsLambdaZipFile in "$path"/target/*-aws-lambda.zip; do
      # upload AWS Lambda ZIP as needed
      artifactName=$(basename "$awsLambdaZipFile")
      artifactPath=$awsLambdaZipFile
      s3ObjectName=$artifactName
      echo "Uploading $(tput bold)$artifactName$(tput sgr0) to staging bucket $(tput bold)$stagingBucketName$(tput sgr0) ..."
      if (( !$dryRun )); then
        aws s3 cp "$artifactPath" s3://${stagingBucketName}/"${s3ObjectName}" $awsProfileOption || return
      fi
    done

    samTemplateFile="$path"/target/generated-sources/annotations/sam.yaml
    if [[ -f $samTemplateFile ]]; then
      projectName=$(mvn help:evaluate -Dexpression="project.artifactId" --quiet -DforceStdout -f "$path")
      stackName="flange-$env-$projectName"
      echo "Deploying CloudFormation stack $(tput bold)$stackName$(tput sgr0) for project $(tput bold)$projectName$(tput sgr0) ..."
      if (( !$dryRun )); then
        sam deploy \
            --profile $awsProfile \
            --template-file "$samTemplateFile" \
            --stack-name $stackName \
            --s3-bucket $stagingBucketName \
            --capabilities CAPABILITY_NAMED_IAM \
            --parameter-overrides FlangeEnv=$env
      fi
    fi

    for subpath in "$path"/*; do
      cloudDeployTraverse "$subpath"
    done
  fi
}

## `flange exec`

exec() {
  if (( ${#args[@]} < 2 )); then
    echo "$(tput bold)Incorrect number of arguments: ${#args[@]}$(tput sgr0)" >&2
    usage
    exit 1
  fi
  execMainClass=${args[1]}
  execArgs=("${args[@]:2}")
  # Maven Exec Plugin seems to support either single or double quotes as the argument delimiter, which no escaping.
  # This approach uses a single quote as a delimiter; thus the arguments themselves must not contain single quotes.
  mvn exec:java -Dexec.mainClass="$execMainClass" -Dexec.args="${execArgs[*]@Q}" --quiet
}

case $command in
  cloud) cloud;;
  exec) exec;;
  *)
    echo "$(tput bold)Unknown command: $command$(tput sgr0)" >&2
    usage
    exit 1
    ;;
esac
