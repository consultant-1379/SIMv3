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

#
# Update service file
#
update_service() {
  START_SCRIPT="$BIN_DIR"/start.sh
  STOP_SCRIPT="$BIN_DIR"/stop.sh

  export START_SCRIPT
  export STOP_SCRIPT

  TEMP_FILE=$(mktemp)
  /usr/bin/cp "$SERVICE_FILE" "$TEMP_FILE"
  envsubst <"$TEMP_FILE" >"$SERVICE_FILE"
  $ECHO "Service description updated with paths"
}

#
# Setup SIM service
#
set_service() {
  chown root:root "$SERVICE_FILE"
  if [[ $(/usr/bin/cp -f "$SERVICE_FILE" /etc/systemd/system/) -eq 0 ]]; then
    $ECHO "Successfully copied service file"
  else
    $ECHO "Failed to copy service file to /etc/systemd/system/ path"
    exit 1
  fi
  systemctl daemon-reload
  $ECHO "Daemon reloaded"
}

#
# Enable and start SIM service
#
svc_enable() {
  $ECHO "Enabling service..."
  systemctl enable $SERVICE_NAME
  $ECHO "Service enabled. Start service by executing command 'service'"
  #$ECHO "Starting service..."
  #systemctl start $SERVICE_NAME

  #rmi_status=$(systemctl status $SERVICE_NAME | grep -i running)
  #if [ -z "${rmi_status}" ]; then
  #  $ECHO "Failed to start $APP_NAME service"
  #else
  #  $ECHO "$APP_NAME service started"
  #fi
}

####################
### Script logic ###
####################

# Check user
check_id $DEFAULT_USER

# Check current state
chk_svc=$(systemctl list-units --type service | awk '{ print $1 }' | grep -i $APP_NAME | grep -v "eniq")
#chk_svc=$(systemctl list-units --type service | grep -i $APP_NAME)
if [ -n "${chk_svc}" ]; then
  $ECHO "$APP_NAME service enabled and running"
  exit 1
fi
update_service
set_service
svc_enable

$ECHO "$APP_NAME installed successfully!"
