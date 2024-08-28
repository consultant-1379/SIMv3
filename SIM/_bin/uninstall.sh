#!/bin/bash
# ****************************************************************************************
# Ericsson Radio Systems AB							SCRIPT
# ****************************************************************************************
#
# (c) Ericsson Radio Systems AB 2019 - All rights reserved.
#
# The copyright to the computer program(s) herein is the property
# of Ericsson Radio Systems AB, Sweden. The programs may be used
# and/or copied only with the written permission from Ericsson Radio
# Systems AB or in accordance with the terms and conditions stipulated
# in the agreement/contract under which the program(s) have been
# supplied.
#
#
# ****************************************************************************************
# Name    : SIM Service Enabler
# Purpose : To enable SIM service on RHEL
# Usage   : ./install.sh
#
# ****************************************************************************************

# Before anything, check if we have correct OS
OS_type=$(uname -s)
if [ "$OS_type" == "Linux" ]; then
  ECHO='/usr/bin/echo -e'
else
  echo "Only Linux based OS are supported"
  exit 1
fi

# Setup variables
DEFAULT_USER=root
APP_NAME="SIM"
SERVICE_NAME=sim.service
PRG="$0"
PRGDIR=$(dirname "$PRG")
APP_HOME=$(
  cd "$PRGDIR/.." || exit
  pwd
)

BIN_DIR="$APP_HOME"/bin
ETC_DIR="$APP_HOME"/etc
SERVICE_FILE="$ETC_DIR"/"$SERVICE_NAME"

#
# Script must be executed as root user script
#
check_id() {
  _check_id_=$(whoami)
  if [ "$_check_id_" != "$1" ]; then
    _err_msg_="Script must be executed as $1"
    $ECHO "${_err_msg_}"
    exit 1
  fi
}

####################
### Script logic ###
####################

# Check user
check_id $DEFAULT_USER

# Check current state
chk_svc=$(systemctl list-units --type service | grep -i $APP_NAME)
if [ ! -n "${chk_svc}" ]; then
  $ECHO "$APP_NAME service not found"
  exit 1
fi

$ECHO "Stopping $APP_NAME"
systemctl stop $SERVICE_NAME

$ECHO "Disabling $APP_NAME"
systemctl disable $SERVICE_NAME

if [[ $(/usr/bin/rm /etc/systemd/system/"$SERVICE_NAME") -eq 0 ]]; then
  $ECHO "$APP_NAME deleted"
fi

systemctl daemon-reload
$ECHO "Daemon reloaded"

systemctl reset-failed
$ECHO "Uninstall completed!"