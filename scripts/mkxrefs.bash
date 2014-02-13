#!/bin/bash

# build all of the xrefs

# get script directory (from: http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in )
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
bookDir=$SCRIPTDIR/../Production

pushd $bookDir
/bin/rm -rf generated
fgrep 'TODO' *.xml
java -classpath $SCRIPTDIR/JXREF.jar com.winvector.ScanIDs
mv CodeExamples.zip generated

# return to first dir
popd
date
echo "all done"


