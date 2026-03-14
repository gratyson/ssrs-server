Build Type
==
The project supports a total of three different build types:
1. Postgres
   * Runs a server locally backed by a Postgres database
   * Builds this way by default, e.g. `./gradlew clean build`
2. AWS Serverless
   * Serves application via AWS resources, backend runs inside lambda function
   * Uses 'build-aws' build property, e.g. `./gradlew clean build -Pbuild-aws`
3. AWS Local
   * Runs the backend locally but integrates AWS resources
   * Intended primarily for quicker testing cycles during development, not production deployments
   * Uses 'build-aws-local' build-property, e.g. `./gradlew clean bootRun -Pbuild-aws-local`

Postgres Build Setup
==
1. Setup Postgres:
   * Run the `database-init.sql` script to create the necessary tables, etc. Update the schema name on the first line before running if another schema name is desired.
   * Run the language scripts (e.g. `japanese.sql`) to initialize the language data. 
   * Configure the `ssrs.datasource.postgres.url`, `ssrs.datasource.postgres.username`, and `ssrs.datasource.postgres.password` settings with appropriate information. The url needs to contain the schema (i.e. something like `jdbc:postgresql://localhost:5432/ssrs?currentSchema=my_schema`)
   * Enable levenshtein: `CREATE EXTENSION fuzzystrmatch SCHEMA public` (requires `postgresql-contrib` to be installed)
2. Create JWT key:
   * Generate a random key and base64 encode the key value
   * Set the encoded key value to `server.jwt.secret`
3. Configure TLS:
   * Generate a self-signed certificate using `keytool` or another tool
   * Copy the generated certificate file to `src/main/resources/jks/`
   * Configure alias, password, filename, provider, and type under the `server.ssl.` settings.
4. Deployment:
   * Create a run-as user and the user's home directory.
   * Configure the scripts' `SSRS_USER` property with the created username.
   * This is unnecessary if only doing development. Use `./gradlew bootRun` for running in development instead.
5. Backups
   * Configure the backup directory property in the `backup.sh` script.
   * Schedule the script to run with `cron` or other tool of choice.

Scripts
--
* Running `deploy.sh` from the source code directory will build and copy both the client and server code to the run-as user home directory. The script assumes that the client code lives under a folder named `ssrs-client` that is adjacent to the server source code.
* `start.sh` and `stop.sh` can be run from the `scripts` directory under the run-as user's home directory to start or stop the running service.


AWS Build Setup
==
1. If not already installed, install the AWS CLI so it is available to the deploy script  
1. In `init-context-aws.sh`, set the stage you want to deploy (i.e., `dev` or `prod`) as well as a unique alphanumeric string as `GLOBAL_UNIQUE_ID`. The `GLOBAL_UNIQUE_ID` is included in S3 bucket names to make sure all s3 buckets created are globally unique
1. Using the AWS console, run the `ssrs-bootstrap.yaml` CloudFormation template. The `UniqueId` input parameter needs to match the value used for `GLOBAL_UNIQUE_ID`. This script creates the s3 bucket and user used for the full deploy scripts.
1. In the console, create an access key for the newly created IAM user `deploy-user`. Configure that access key to be used by the CLI using `aws configure --profile deploy-user`. Additionally, configure credentials for an admin user that can deploy CloudFormation. This user can optionally be configured as a profile in the cli and set into `RESOURCE_DEPLOY_PROFILE_NAME` in `init-context-aws.sh`.
   * IAM Users have a maximum policy size, making it impossible to create a policy that can deploy the full set of resources needed while following the principle of least privilege.
1. Run the command `./scripts/deploy-aws-resources.sh` to run the CloudFormation template to deploy all needed AWS resources.
   * If a profile was not created for the resource deploy, log in with `aws configure` prior to running the script.
   * The CloudFormation template will create a deployment for the API Gateway API created within the template. However, it will not automatically re-deploy if updates are made to the API within the CloudFormation template. In this case, the API will need to be manually deployed after updates are made to it. 
1. Run the command `./scripts/deploy-aws-code.sh` to deploy both the client and server code

Once the resources and code are deployed, the frontend can be accessed by base path of the API created as part of the CloudFormation template. This url can be found either in the AWS console under the API Gateway deployment or by running `aws --profile deploy-user cloudformation describe-stacks --stack-name ssrs-resources-${Stage} --query "Stacks[0].Outputs[?OutputKey=='RestEndpoint'].OutputValue" --output text` (substitute `dev` or `prod` as appropriate in place of `${Stage}` in the command).  



Updating the version
==
- Update the 'version' property in build.gradle
- Update the 'VERSION' variable in /scripts/start.sh

Generating a new self-signed key
==
1. Generate a new key store using the appropriate command (replacing the passwords):

**Dev**: `keytool -genkey -alias selfsigned_ssrs_dev -keyalg RSA -keysize 2048 -keypass {key_password} -storepass {key_store_password} -keystore ssrs-dev-server.jks`

**Prod**: `keytool -genkey -alias selfsigned_ssrs_prod -keyalg RSA -keysize 2048 -keypass {key_password} -storepass {key_store_password} -keystore ssrs-prod-server.jks`

2. Copy the generated jks file to the `src/main/resources/jks` folder
3. Update the `server.ssl.key-store-password` setting if necessary