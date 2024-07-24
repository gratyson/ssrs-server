#!/usr/bin/env bash
set -e

VERSION=1.0
SSRS_USER=ssrs
BASE_HREF=/ssrs/

STARTING_DIR=$PWD

cd /home/ssrs
sudo su -c "java -jar -Dspring.profiles.active=prod -Dserver.servlet.context-path=$BASE_HREF /home/$SSRS_USER/lib/ssrs-$VERSION.jar > /dev/null 2>&1 &" $SSRS_USER

cd $STARTING_DIR