#!/usr/bin/env bash
set -e

SSRS_USER=ssrs

if [[ -f "/home/$SSRS_USER/application.pid" ]]; then
  sudo kill -15 $(cat /home/$SSRS_USER/application.pid)
fi