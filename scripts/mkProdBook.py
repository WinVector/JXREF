#! /usr/bin/env python

# render all of the chapters to pdf
# The Manning tool gets confused on numbering if more than one chapter is present- so we look in book.xml and parts to get
# separate chapters (breaks cross chapter refs, fixed by using our extenal links from: https://github.com/WinVector/JXREF ).
# assumes all xml files in same directory
# assumes <xi:include lines are all on one line (so we can grep them out).
# assumes <xi:include lines with "_external_links.xml" are only in chapters/appendicies (so we don't need to process below chapters).


import sys
import os
import re
import itertools
import xml.etree.ElementTree as ET
from subprocess import call

scriptdir = os.path.dirname(os.path.realpath(__file__))
sourcedir = os.path.join(scriptdir,'../Book')
resultdir = os.path.join(scriptdir,'../previews')
sourceXML='book.xml'
resPDF='PracticalDataScienceWithR.pdf'
tooldir=os.path.join(scriptdir,'../ManningTools/AAMakePDFv19')
origdir=os.getcwd()

print 'scriptdir',scriptdir
print 'sourcedir',sourcedir
print 'resultdir',resultdir
print 'tooldir',tooldir
if not (os.path.isdir(scriptdir) and os.path.isdir(sourcedir) and os.path.isdir(tooldir)):
   sys.exit(1)
os.chdir(sourcedir)
if not os.path.exists(resultdir):
   os.makedirs(resultdir)
if not os.path.isdir(resultdir):
   sys.exit(1)

def getIncludes(path):
    root = ET.parse(path).getroot()
    return [y for y in [x.attrib.get('href') for x in root.iter('{http://www.w3.org/2001/XInclude}include')] if not re.match('.*_external_links.xml.*',y) ]

def getContentList(d,fname):
    res = [fname]
    fpath = os.path.join(d,fname)
    # don't go below chapters
    nameLab = getXMLName(fpath)
    if not (nameLab[0]=='chapter' or nameLab[0]=='appendix'):
        for si in [getContentList(d,x) for x in getIncludes(fpath)]:
            res.extend(si)
    return res

def getXMLName(path):
    root = ET.parse(path).getroot()
    return [re.sub('{http://www.manning.com/schemas/book}','',root.tag),root.attrib.get('label')]

contentList = getContentList(sourcedir,os.path.join(sourcedir,sourceXML))
print contentList
pdfspecs=[]
tmpfiles=[ 'c:\\sw\\text.txt', 'temp.xml', 'tp.xml', 'tp.xml.temp.xml' ]
for ti in contentList:
    nameLab = getXMLName(os.path.join(sourcedir,ti)) 
    ni = nameLab[0]
    li = nameLab[1]
    if not ni=='book':
        print ti,ni,li
        tpdf = ni + '_' + li + '.pdf'
        tmpfiles.append(tpdf)
        spec='2-' # assume first page in each render is blank (has been historicly)
        if len(pdfspecs)<=0:
            spec='3-' # Frontmatter first 2 pages are useless
        pdfspecs.append(tpdf)
        pdfspecs.append(spec)
        f = open("tp.xml", "w")
        if not (ni=='chapter' or ni=='appendix'):
           call(['fgrep','-v','<xi:include',ti],stdout=f)
        else:
           call(['cat',ti],stdout=f)
        f.close()
        with open(os.devnull, "w") as dn:
            call(os.path.join(tooldir,'AAMakePDFMac.sh') + ' tp.xml ' + tpdf,shell=True,stdout=dn,stderr=dn)
            call(['pdfjoin',tpdf,spec,'--outfile',os.path.join(resultdir,tpdf)],stdout=dn,stderr=dn)

print pdfspecs
print tmpfiles

joincmd = ['pdfjoin']
joincmd.extend(pdfspecs)
joincmd.extend(['--outfile',os.path.join(resultdir,resPDF)])
call(joincmd)
for fi in tmpfiles:
   if os.path.exists(fi):
       os.remove(fi)    
os.chdir(origdir)

