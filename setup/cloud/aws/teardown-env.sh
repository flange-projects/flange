#!/bin/bash
set -eu
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

awsProfileFile=$SCRIPT_DIR/aws-profile #optional file; contains name of AWS profile to use

if [[ $# -ne 1 ]]; then
  echo 'Tears down the infrastructure for an environment such as dev or prod.' >&2
  echo 'Usage: teardown-env <env>' >&2
  echo 'Example: teardown-env dev' >&2
  echo 'The AWS profile specified in the `aws-profile` file will be used, if present.' >&2
  echo 'Warning: Before tearing down an environment you must first manually empty any created buckets.' >&2
  exit 1
fi

env=$1

if [[ -f $awsProfileFile ]]; then awsProfileOption="--profile $(< $awsProfileFile)"; else awsProfileOption=""; fi
stackName=flange-$env
aws cloudformation delete-stack $awsProfileOption --stack-name $stackName
