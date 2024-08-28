#!/bin/bash

#FIXME: Only and only for testing, remove later
JAVA_HOME=/eniq/sw/runtime/java
export JAVA_HOME

# Setup variable
PRG="$0"
PRGDIR=$(dirname "$PRG")
APP_HOME=$(
  cd "$PRGDIR/.."
  pwd
)
BINDIR="$APP_HOME"/bin
ETCDIR="$APP_HOME"/etc
LIBDIR="$APP_HOME"/lib
EXTLIBDIR="$APP_HOME"/extlib
POLICYDIR="$APP_HOME"/policy
PROTOCOLDIR="$APP_HOME"/protocols

CONFIG_DEF="$ETCDIR"/config.json

# Check java
OUTPUT=$(type -p java)
if [[ -n "$OUTPUT" ]] && [[ -f "$OUTPUT" ]]; then
  #echo Found java executable in PATH
  JAVA=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
  #echo Found java executable in JAVA_HOME
  JAVA="$JAVA_HOME/bin/java"
else
  echo "Java executable not found in either PATH or JAVA_HOME. Set either PATH or JAVA_HOME to start the program"
  exit
fi

if [[ "$JAVA" ]]; then
  version=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}')
  #echo Jave version found: "$version"
  if [[ "$version" < "1.8" ]]; then
    echo Java 1.8 or higher is required to run the program.
    exit
  fi
fi

# Start execution
$JAVA -Duser.dir=$APP_HOME -Dapp.home=$APP_HOME -Dlog4j.configurationFile=file:$ETCDIR/log4j2.xml -Dconfig=$CONFIG_DEF -cp "$LIBDIR/*:$EXTLIBDIR/*:$POLICYDIR/*:$PROTOCOLDIR/*" com.ericsson.sim.engine.VerifyFS "$@"
