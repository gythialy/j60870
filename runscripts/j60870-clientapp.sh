#!/bin/bash

# Set APP_HOME
# gradle start up script:
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/.." >&-
APP_HOME="`pwd -P`"
cd "$SAVED" >&-


CLASSPATH=$(JARS=("$APP_HOME"/build/libsdeps/*.jar); IFS=:; echo "${JARS[*]}")

SYSPROPS=""
for i in $@; do 
    if [[ $i == -D* ]]; then
	SYSPROPS="$SYSPROPS $i";
    else
	PARAMS="$PARAMS $i";
    fi
done

java $SYSPROPS -cp $CLASSPATH org.openmuc.j60870.app.ClientApp $PARAMS
