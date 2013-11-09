#!/bin/bash

# render all of the chapters to pdf
# The Manning tool gets confused on numbering if more than one chapter is present- so we look in book.xml and parts to get
# separate chapters (breaks cross chapter refs, could be fixed by using our extenal links from: https://github.com/WinVector/JXREF ).
# assumes <xi: lines in book.xml are on a single line and we can grep/sed out the file name.
# assumes <xi: lines in parts (found in book) are on a single line and we can grep/sed out the file name.
# assumes first part is 0 (front-matter, not really a part) and numbers up by ones.

# get script directory (from: http://stackoverflow.com/questions/59895/can-a-bash-script-tell-what-directory-its-stored-in )
SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
#sourceDir=$SCRIPTDIR/../Production
sourceDir=$SCRIPTDIR/../Book
resultDir=$SCRIPTDIR/../previews
sourceXML="book.xml"
resPDF="PracticalDataScienceWithR.pdf"
toolDir=$SCRIPTDIR/../ManningTools/AAMakePDFv19

# need to be in production directory for Manning tool to work
if [ $# -gt 0 ]
then
   echo "mkProdBook takes no arguments"
   popd
   exit -1
fi

pushd $sourceDir
if [[ $? -ne 0 ]]; then
    exit 1
fi

mkdir -p $resultDir
if [[ $? -ne 0 ]]; then
    exit 1
fi



## pre clean
rm -f "c:\\sw\\text.txt"
rm -f temp.xml
rm -f *.temp.xml
rm -f $resPDF
rm -f $resultDir/$resPDF
rm -f $toolDir/temp.xml
rm -f tp.xml

# do the work
toplist=`fgrep '<xi:' book.xml | fgrep -v '_external_links.xml' | sed 's/^[^"]*"//' | sed 's/"[^"]*$//'`
let PCOUNTER=0
let CCOUNTER=1
pdffspecs=""
tmppdfs=""
for ti in $toplist
do
   tpdf=`printf "part%02i.pdf" $PCOUNTER`
   rm -f $tpdf
   rm -f $resultDir/$tpdf
   fgrep -v '<xi:' $ti > tp.xml
   sh $toolDir/AAMakePDFMac.sh tp.xml $tpdf
   pdfjoin $tpdf 2- --outfile $resultDir/$tpdf # assume first page in each render is blank (has been historicly)
   pdffspecs="$pdffspecs $tpdf 2-"             # assume first page in each render is blank (has been historicly)
   tmppdfs="$tmppdfs $tpdf"
   chlist=`fgrep '<xi:' $ti | fgrep -v '_external_links.xml' | sed 's/^[^"]*"//' | sed 's/"[^"]*$//'`
   for ci in $chlist
   do
      cpdf=`printf "ch%02i.pdf" $CCOUNTER`
      rm -f $cpdf
      rm -f $resultDir/$cpdf
      sh $toolDir/AAMakePDFMac.sh $ci $cpdf
      pdfjoin $cpdf 2- --outfile $resultDir/$cpdf # assume first page in each render is blank (has been historicly)
      pdffspecs="$pdffspecs $cpdf 2-"             # assume first page in each render is blank (has been historicly)
      tmppdfs="$tmppdfs $cpdf"
      let CCOUNTER=CCOUNTER+1 
   done
   let PCOUNTER=PCOUNTER+1 
done

pdfjoin $pdffspecs --outfile $resPDF
mv $resPDF $resultDir/$resPDF

# post clean
rm -f $tmppdfs
rm -f "c:\\sw\\text.txt"
rm -f temp.xml
rm -f *.temp.xml
rm -f $toolDir/temp.xml
rm -f tp.xml

# return to first dir
popd
date
echo "all done"

