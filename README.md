Initial Setup
--
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

Updating the version
--
- Update the 'version' property in build.gradle
- Update the 'VERSION' variable in /scripts/start.sh

Generating a new self-signed key
--
1. Generate a new key store using the appropriate command (replacing the passwords):

**Dev**: `keytool -genkey -alias selfsigned_ssrs_dev -keyalg RSA -keysize 2048 -keypass {key_password} -storepass {key_store_password} -keystore ssrs-dev-server.jks`

**Prod**: `keytool -genkey -alias selfsigned_ssrs_prod -keyalg RSA -keysize 2048 -keypass {key_password} -storepass {key_store_password} -keystore ssrs-prod-server.jks`

2. Copy the generated jks file to the `src/main/resources/jks` folder
3. Update the `server.ssl.key-store-password` setting if necessary