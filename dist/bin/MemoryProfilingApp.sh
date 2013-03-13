#! /usr/bin/env sh

SCRIPT_HOME=$0

if [ -L $SCRIPT_HOME ]; 
then
  SCRIPT_HOME=`readlink $0`  
fi

SCRIPT_HOME=`dirname $SCRIPT_HOME`

java -server -Xms25m -Xmx25m -cp "${SCRIPT_HOME}/../lib/*" \
-Djava.util.logging.config.file="${SCRIPT_HOME}/../config/MemoryProfilingApp.logging.properties" \
fm.websockets.samples.memprof.MemoryProfilingApp $*

