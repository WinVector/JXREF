#! /usr/bin/env python

# render all of the chapters to pdf
# The Manning tool gets confused on numbering if more than one chapter is present- so we look in book.xml and parts to get
# separate chapters (breaks cross chapter refs, fixed by using our extenal links from: https://github.com/WinVector/JXREF ).
# assumes all xml files in same directory
# assumes <xi:include lines are all found with the regexp: 
# '<xi:include\s[^><]+/>' (and nothing else matches this)
# assumes <xi:include lines with "_external_links.xml" are only in chapters/appendicies (so we don't need to process below chapters).


import sys
import os
import re
import shutil
import itertools
import pipes
import xml.etree.ElementTree as ET
from subprocess import call

# scriptdir = os.path.dirname('.') ## for degugging
scriptdir = os.path.dirname(os.path.realpath(__file__))
sourcedir = os.path.join(scriptdir,'../Production') # alternate dir we can build from
resultdir = os.path.join(scriptdir,'../previews')
sourceXML='book.xml'
coverpath = os.path.join(sourcedir,'cover.pdf')
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
if os.path.isfile(coverpath):
   pdfspecs.append(str(coverpath))
   pdfspecs.append('1-1')
for ti in contentList:
    nameLab = getXMLName(os.path.join(sourcedir,ti)) 
    ni = nameLab[0]
    li = nameLab[1]
    if not ni=='book':
        print ti,ni,li
        tpdf = ni + '_' + li + '.pdf'
        tmpfiles.append(tpdf)
        spec='2-' # assume first page in each render is blank (has been historicly the case)
        pdfspecs.append(tpdf)
        pdfspecs.append(spec)
        if not (ni=='chapter' or ni=='appendix'):
            with open(ti,'r') as inf:
                content = inf.read()
                with open('tp.xml', 'w') as f:
                   content = re.sub(r'<xi:include\s[^><]+/>',' ',content)  # prevent double includes
                   f.write(content)
        else:
            with open(ti,'r') as inf:
                content = inf.read()
                with open('tp.xml', 'w') as f:
                   content = re.sub(r'\.jpg"','.pdf"',content)  # revert any jpgs back to original pdfs
                   f.write(content)
        with open(os.devnull, "w") as dn:
            call(pipes.quote(os.path.join(tooldir,'AAMakePDFMac.sh')) + ' tp.xml ' + pipes.quote(tpdf),shell=True,stdout=dn,stderr=dn)
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

