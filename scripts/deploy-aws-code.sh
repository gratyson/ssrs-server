STARTING_DIR=$PWD
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

source $SCRIPT_DIR/init-context-aws.sh

cd $SSRS_CLIENT_DIR

if aws --profile $CODE_DEPLOY_PROFILE_NAME cloudformation describe-stacks --stack-name=ssrs-resources-$STAGE > /dev/null 2>&1; then
  if [ $STAGE == "dev" ]; then
    # S3 deploy always uses environment.ts, so copy over the dev settings if the deploying the dev stage
    BACKUP_CONFIG_FILE_NAME=$SSRS_CLIENT_DIR/src/environments/environment.ts.backup
    cp $SSRS_CLIENT_DIR/src/environments/environment.ts $BACKUP_CONFIG_FILE_NAME
    cp $SSRS_CLIENT_DIR/src/environments/environment.development.ts $SSRS_CLIENT_DIR/src/environments/environment.ts
  fi

  # Always use production for when generating files to copy into S3
  ng build --configuration=production --base-href /$STAGE/
  aws --profile $CODE_DEPLOY_PROFILE_NAME s3 sync ./dist/ssrs/browser s3://$S3_FRONTEND_BUCKET_NAME --delete

  if [ $STAGE == "dev" ]; then
    # Restore the original config
    cp $BACKUP_CONFIG_FILE_NAME $SSRS_CLIENT_DIR/src/environments/environment.ts
    rm $BACKUP_CONFIG_FILE_NAME
  fi
else
  echo "Unable to find CloudFormation stack. Skipping client deploy."
fi

cd $SSRS_SERVER_DIR

# Delete any static resources on the server directory that may be left over from a local deploy
if [ -n "$(ls -A $SSRS_SERVER_DIR/src/main/resources/static/ 2>/dev/null)" ]
then
  rm -rf $SSRS_SERVER_DIR/src/main/resources/static/*
fi

# Build, upload, and deploy the server files into the Lambda function
if ./gradlew clean package -Pbuild-aws -Pspring.profiles.active=$STAGE; then
  aws --profile $CODE_DEPLOY_PROFILE_NAME s3 cp ./build/distributions/ssrs-$VERSION.zip s3://$S3_DEPLOY_BUCKET_NAME/ssrs.zip
  aws --profile $CODE_DEPLOY_PROFILE_NAME lambda update-function-code --function-name $LAMBDA_FUNCTION_NAME --s3-bucket=$S3_DEPLOY_BUCKET_NAME --s3-key=ssrs.zip
fi

cd $STARTING_DIR