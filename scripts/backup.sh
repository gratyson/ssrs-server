#!/usr/bin/env bash
set -e

# Running this script requires a .pgpass file in the user directory to exist for
# authentication (see https://www.postgresql.org/docs/current/libpq-pgpass.html
# for details). Additionally, an appropriate user must exist with the pg_read_all_data
# role assigned to allow dump data to be read.
#
# Blob data is not outputted with this backup and should be backed up separately as
# needed.

SSRS_USER=ssrs
SSRS_BACKUP_DIR=/media/usb-drive/ssrs-backup
SSRS_SCHEMA=ssrs_prod
BACKUP_FILE_NAME=$SSRS_BACKUP_DIR/ssrs_backup_$(date -I).dump.gz
BACKUPS_TO_KEEP_CNT=3

if [ -d $SSRS_BACKUP_DIR ]; then
  pg_dump --role=pg_read_all_data --schema=$SSRS_SCHEMA -O -Fc | gzip > $BACKUP_FILE_NAME

  CUR_DIR=$PWD
  cd $SSRS_BACKUP_DIR
  ls -tp | grep -v '/$' | tail -n +$[BACKUPS_TO_KEEP_CNT + 1] | xargs -d '\n' -r rm --
  cd $CUR_DIR
  echo "$BACKUP_FILE_NAME file created."
else
  echo "Backup directory does not exist. Skipping backup."
fi


