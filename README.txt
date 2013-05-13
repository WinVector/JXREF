
4-1-2013 (not a joke)

 Program to check Manning Publications Agile Author XML for some cross
 reference issues (Java based).  Property Win-Vector LLC.  
 Distribution license: GPL3 or later.
 Hosted at: https://github.com/WinVector/JXREF

 Experimental code we
 use for our book, no statements of fitness or guarantees given here.
 probably does not yet properly cover all of DocBook cases (mostly 
 put checks for structures we used).  Assumes well-formed XML pretty
 much obeying the Manning schema (as this is easy to check with 
 the manning supplied XSD and tools)

To Use:

 Run the class com.winvector.ScanIDs .  It takes no arguments and
 expects to find a file named book.xml in its working directory.  For
 each file named in book.xml the program creates file with all of the
 cross-references defined from other files, append with
 _external_links.xml the strategy is to get all possible external refs
 by add a line of XML like: <xi:include href="X_external_links.xml"/>
 (you should remove this include before pushing files to Manning,
 we suggest using something like "fgrep -v '_external_links.xml'").

 Example production of external links in test/GoodBook
 
 Also checks for a number of errors:
   1) Illegal id tags (not stating with alpha or containing whitespace)
   2) linkend references to non-existent tags
   3) Duplicate tags
   4) Case confusion between tags
   5) Use of <co id=X> and <callout arrearefs=X> in non-example context ( <example> or <informalexample> )
   6) Non-parallel structure between call-outs <co id=X> and <callout arrearefs=X>
   7) items that must have ids (and these ids must be referred to): <example> and <figure>
   9) Dangling filerefs.
   9) Unused file assets (warn)
  10) resource directories used by more than one XML file (warn)
  11) referring to un-numbered structures (informalexample, formalpara, sect3)


  As a final side effect chapter plus level-1 section names are printed.

  Example errors reported in test/BadBook




Example of good run:

working in: GoodBook/.
reading:	book.xml	./book.xml
	reading referenced content
	Chapter 1: Good Chapter 1
		sect
	Chapter 2: Good Chapter 2
		sect
	writing links
total Errors: 0
done

which produces the files:
   GoodChapter1.xml_external_links.xml
   GoodChapter2.xml_external_links.xml

Each "_external_links.xml" file contains all the defined id's in the
book that are not in the chapter in question. Look in test/GoodBook
for the simple book example.
