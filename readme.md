# Flange

## Reference

### Configuration

* `flange.env`: The environment such as `dev`, `dev3`, or `dev-jdoe` which the application is using and/or on which the application is deployed.
* `flange.platform`: The platform on which the application is running, such as `aws`; used to load platform-specific dependencies, for example.
* `flange.profiles`: Indicates which Flange application profiles are in effect (i.e. active).
* `flange.stage`: The deployment stage, such as `dev`, `qa`, or `prod`; essentially a category of environment, and usually defined as part of the Flange environment.

### AWS-Specific Configuration

* `flange.aws.profile`: Indicates an AWS [named profile](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html#cli-configure-files-using-profiles) to use when running locally.
