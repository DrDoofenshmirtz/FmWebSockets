#! /usr/bin/env sh

SCRIPT_HOME=$0

if [ -L $SCRIPT_HOME ]; 
then
  SCRIPT_HOME=`readlink $0`  
fi

SCRIPT_HOME=`dirname $SCRIPT_HOME`

java -server -Xms250m -Xmx500m -cp "${SCRIPT_HOME}/lib/*" fm.websockets.samples.counter.CounterApp $*

