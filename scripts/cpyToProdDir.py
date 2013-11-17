#! /usr/bin/env python

# copy from our book dir to manning dir, remove external_refs stuff
# assumes <xi:include lines are all found with the regexp: 
#  r'<xi:include\s[^><]*href="[^"><]*.xml_external_links.xml"[^><]*/>'
#  (and nothing else matches this)

import sys
import os
import re
import shutil
import itertools
import xml.etree.ElementTree as ET
from subprocess import call

scriptdir = os.path.dirname(os.path.realpath(__file__))
sourcedir = os.path.join(scriptdir,'../Book')
proddir = os.path.join(scriptdir,'../Production')
origdir=os.getcwd()

print 'scriptdir',scriptdir
print 'sourcedir',sourcedir
print 'proddir',proddir
if not (os.path.isdir(scriptdir) and os.path.isdir(sourcedir) and os.path.isdir(proddir)):
   sys.exit(1)
os.chdir(proddir)
call('/bin/rm -rf *.xml *.css *.xsd figures',shell=True)
os.chdir(sourcedir)
call('/bin/cp -rf figures *.css *.xsd ' + proddir,shell=True)
for f in [f for f in os.listdir('.') if (not f.startswith('.')) and f.endswith('.xml')]:
    with open(f,'r') as inf:
        content = inf.read()
        with open(os.path.join(proddir,f), 'w') as ouf:
           content = re.sub(r'<xi:include\s[^><]*href="[^"><]*.xml_external_links.xml"[^><]*/>',' ',content)
           ouf.write(content)
# return to first dir
os.chdir(origdir)


