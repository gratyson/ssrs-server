SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source $SCRIPT_DIR/init-context-aws.sh

if [ -z $RESOURCE_DEPLOY_PROFILE_NAME ]; then
  echo "No AWS CLI profile set for resource deployment. Using logged in credentials from 'aws configure'"
  AWS_PRF=""
else
  AWS_PRF="--profile $RESOURCE_DEPLOY_PROFILE_NAME"
fi

if [ -z $GLOBAL_UNIQUE_ID ]; then
  echo "Global Unique Identifier Required";
else
  if aws $AWS_PRF s3 sync $SSRS_SERVER_DIR/cloudformation/resources s3://$S3_DEPLOY_BUCKET_NAME/cf-templates --delete; then
    if aws $AWS_PRF cloudformation describe-stacks --stack-name=$STACK_NAME > /dev/null 2>&1; then
      aws $AWS_PRF cloudformation update-stack --stack-name $STACK_NAME --template-url https://$S3_DEPLOY_BUCKET_NAME.s3.$REGION.amazonaws.com/cf-templates/ssrs-template.yaml --parameters ParameterKey=Stage,ParameterValue=$STAGE ParameterKey=UniqueId,ParameterValue=$GLOBAL_UNIQUE_ID ParameterKey=TemplateLocation,ParameterValue=https://$S3_DEPLOY_BUCKET_NAME.s3.$REGION.amazonaws.com/cf-templates --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND
    else
      aws $AWS_PRF cloudformation create-stack --stack-name $STACK_NAME --template-url https://$S3_DEPLOY_BUCKET_NAME.s3.$REGION.amazonaws.com/cf-templates/ssrs-template.yaml --parameters ParameterKey=Stage,ParameterValue=$STAGE ParameterKey=UniqueId,ParameterValue=$GLOBAL_UNIQUE_ID ParameterKey=TemplateLocation,ParameterValue=https://$S3_DEPLOY_BUCKET_NAME.s3.$REGION.amazonaws.com/cf-templates --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND
    fi
  fi
fi