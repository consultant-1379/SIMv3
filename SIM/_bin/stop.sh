#!/bin/bash

APP_NAME=com.ericsson.sim.engine.Main
TIMEOUT=15

#check pid
PID=$(ps -ef | grep java | grep $APP_NAME | grep -v grep | awk '{print $2}')

#if it exists, send SIGTERM
if [[ -n $PID ]]; then
  echo "$APP_NAME sending SIGTERM"
  kill -15 "$PID"
else
  echo "$APP_NAME already stopped"
  exit 0
fi

#wait for timeout on PID
timeout $TIMEOUT tail --pid="$PID" -f /dev/null

#check again for process, and this time kill for good with SIGKILL
PID=$(ps -ef | grep java | grep $APP_NAME | grep -v grep | awk '{print $2}')
if [[ -n $PID ]]; then
  echo "$APP_NAME is still running, sending SIGKILL"
  kill -9 "$PID"
fi
