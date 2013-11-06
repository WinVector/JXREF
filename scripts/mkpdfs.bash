#!/bin/bash

# render all of the chapters to pdf
# can be run in any directory reads from Book writes to previews
# optionaly can take a single chapter xml argument (name not path) 
# with zero arguments elements <xi> from book.xml are processed
#   assumes <xi: lines in book.xml are on a signle line and we can grep/sed out the file name.

# get script directory (from: http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in )
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
productionDir=$SCRIPTDIR/../Book
resultDir=$SCRIPTDIR/../previews
toolDir=$SCRIPTDIR/../ManningTools/AAMakePDFv19

pushd $productionDir
if [[ $? -ne 0 ]]; then
    exit 1
fi

mkdir -p $resultDir
if [[ $? -ne 0 ]]; then
    exit 1
fi

# need to be in production directory for Manning tool to work
if [ $# -gt 0 ]
then
   if [ $# -gt 1 ]
   then
      echo "mkpdfs takes at most one xml file arugment"
      popd
      exit -1
   fi
   filename=$(basename "$1")
   extension="${filename##*.}"
   if [ $extension = "xml" ]
   then
      xmlist=$filename
   else
      echo "mkpdfs argument must end in .xml ( $1 )"
      popd
      exit -1
   fi
else 
   # get list of chapter source xmls
   xmlist=`fgrep '<xi:' book.xml | sed 's/^[^"]*"//' | sed 's/"[^"]*$//' | fgrep -v '_external_links.xml'`
fi
echo $xmlist




## pre clean
rm -f "c:\\sw\\text.txt"
rm -f temp.xml
rm -f *.temp.xml
rm -f $toolDir/temp.xml

for xmlf in $xmlist
do
  if [ -e $xmlf ]
  then 
     pdff=${xmlf%.xml}.pdf
     rm -f $pdff
     rm -f $resultDir/$pdff
  else 
     echo "Waring $xmlf not found"
  fi
done

for xmlf in $xmlist
do
  if [ -e $xmlf ]
  then 
     pdff=${xmlf%.xml}.pdf
     sh $toolDir/AAMakePDFMac.sh $xmlf $pdff
     mv $pdff $resultDir/$pdff
     ## post clean
     rm -f "c:\\sw\\text.txt"
     rm -f temp.xml
     rm -f *.temp.xml
  else 
     echo "Waring $xmlf not found"
  fi
done

## post clean
rm -f "c:\\sw\\text.txt"
rm -f temp.xml
rm -f *.temp.xml
rm -f $toolDir/temp.xml

# return to first dir
popd
date
echo "all done"


