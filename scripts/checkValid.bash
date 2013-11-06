#!/bin/bash

# check a single chapter (script arugment) for validity
# can be run in any directory
# use bash checkValid.bash pathtoXMLtoCheck

# get script directory (from: http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in )
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FARG=$1
char0=${FARG:0:1}
ARGDIR="$( cd "$( dirname "$FARG" )" && pwd )"
ARGNAME="$( basename $FARG )"
if [ $char0 = "/" ]
then
  echo "start slash"
  ABSARG=$FARG
else
  ABSARG=$ARGDIR/$ARGNAME
fi
toolDir=$SCRIPTDIR/../ManningTools/AAValidatorv15

echo "absarg: $ABSARG"

if [ -e $ABSARG ]
then
   # need to be in validator dir for Manning script to run
   pushd $toolDir
   pwd

   cd $ARGDIR
   # pre-clean
   rm -f temp.xml
   cd $toolDir

   java -cp .:AAValidator.jar:svnkit.jar:servlet.jar:maven-embedder-2.0.4-dep.jar:maven-2.0.9-uber.jar AAValidator $ABSARG

   cd $ARGDIR
   # post-clean
   rm -f temp.xml
   cd $toolDir

   # return to initial directory
   popd
else
   echo "File not found: $ABSARG"
   exit -1
fi

