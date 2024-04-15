#!/usr/bin/env bash
set -e

STARTING_DIR=$PWD
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
SSRS_SERVER_DIR=$SCRIPT_DIR/..
SSRS_CLIENT_DIR=$SCRIPT_DIR/../../ssrs-client
SSRS_USER=ssrs

$SCRIPT_DIR/stop.sh

cd $SSRS_CLIENT_DIR
ng build
if [ -n "$(ls -A $SSRS_SERVER_DIR/src/main/resources/static/ 2>/dev/null)" ]
then
  rm -rf $SSRS_SERVER_DIR/src/main/resources/static/*
fi
cp -r $SSRS_CLIENT_DIR/dist/ssrs/browser/* $SSRS_SERVER_DIR/src/main/resources/static/
rm -rf $SSRS_CLIENT_DIR/dist/

cd $SSRS_SERVER_DIR
./gradlew build

if [ -n "$(ls -A /home/$SSRS_USER/scripts 2>/dev/null)" ]
then
  sudo rm -r /home/$SSRS_USER/scripts
fi
sudo mkdir /home/$SSRS_USER/scripts
sudo cp $SSRS_SERVER_DIR/scripts/start.sh /home/$SSRS_USER/scripts/start.sh
sudo cp $SSRS_SERVER_DIR/scripts/stop.sh /home/$SSRS_USER/scripts/stop.sh
sudo cp $SSRS_SERVER_DIR/scripts/backup.sh /home/$SSRS_USER/scripts/backup.sh
sudo chown -R $SSRS_USER:$SSRS_USER /home/$SSRS_USER/scripts

if [ -d "/home/$SSRS_USER/lib" ]; then
  if [ -d "$(ls -A /home/$SSRS_USER/lib 2>/dev/null)" ]; then
    sudo rm -rf /home/$SSRS_USER/lib/*
  fi
else
  sudo mkdir /home/$SSRS_USER/lib/
fi
sudo cp $SSRS_SERVER_DIR/build/libs/* /home/$SSRS_USER/lib
sudo chown -R $SSRS_USER:$SSRS_USER /home/$SSRS_USER/lib

$SCRIPT_DIR/start.sh

cd $STARTING_DIR
