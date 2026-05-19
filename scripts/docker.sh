STARTING_DIR=$PWD
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

source $SCRIPT_DIR/init-context-aws.sh

cd $SSRS_CLIENT_DIR
ng build --base-href $BASE_HREF
if [ -n "$(ls -A $SSRS_SERVER_DIR/src/main/resources/static/ 2>/dev/null)" ]
then
  rm -rf $SSRS_SERVER_DIR/src/main/resources/static/*
fi
cp -r $SSRS_CLIENT_DIR/dist/ssrs/browser/* $SSRS_SERVER_DIR/src/main/resources/static/
rm -rf $SSRS_CLIENT_DIR/dist/

cd $SSRS_SERVER_DIR
./gradlew build -Pbuild-aws-local

docker stop ssrs-docker-app
docker rm ssrs-docker-app

docker build -f ./docker/ssrs.dockerfile -t ssrs-docker-app:latest .

docker run -d --name ssrs-docker-app -p 8443:8443 ssrs-docker-app:latest

cd $STARTING_DIR