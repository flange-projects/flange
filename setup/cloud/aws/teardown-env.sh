#!/bin/bash
set -u
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

awsProfileFile=aws-profile #optional file, relative to working directory; contains name of AWS profile to use

usage() {
  echo 'Tears down the infrastructure for an environment such as dev or prod.' >&2
  echo 'Usage: teardown-env <env>' >&2
  echo 'Example: teardown-env dev' >&2
  echo 'The AWS profile specified in the `aws-profile` file will be used, if present.' >&2
  echo 'Warning: Before tearing down an environment you must first manually empty any created buckets.' >&2
}

if (( $# != 1 )); then
  echo "$(tput bold)Incorrect number of arguments: $#$(tput sgr0)" >&2
  usage
  exit 1
fi

env=$1

if [[ -f $awsProfileFile ]]; then
  awsProfile="$(< $awsProfileFile)"
  awsProfileOption="--profile $awsProfile"
else
  awsProfile=""
  awsProfileOption=""
fi

echo "Tearing down environment $(tput bold)$env$(tput sgr0) ..."
if [[ -n $awsProfile ]]; then
  echo
  echo "AWS Profile: $awsProfile"
fi

stackName=flange-$env
aws cloudformation delete-stack \
    $awsProfileOption \
    --stack-name $stackName
