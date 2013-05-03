package com.winvector;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * For each file name in book.xml create file with all of the cross-references defined from other files, append with _external_links.xml
 * the strategy is to get all possible external refs by add a line of XML like: 	<xi:include href="X_external_links.xml"/>
 * 
 * Also check for a number of errors:
 *   1) Illegal id tags (not stating with alpha or containing whitespace)
 *   2) linkend references to non-existent tags
 *   3) Duplicate tags
 *   4) Case confusion between tags
 *   5) Use of <co id=X> and <callout arrearefs=X> in non-example context ( <example> or <informalexample> )
 *   6) Non-parallel structure between call-outs <co id=X> and <callout arrearefs=X>
 *   7) items that must have ids (and these ids must be reffered to): <example> and <figure>
 *   7) Dangling filerefs.
 *   8) Unused file assets (warn)
 *   9) resource directories used by more than one XML file (warn)
 *   
 * @author johnmount
 *
 */
public final class ScanIDs {

	public static final Set<String> callOutContexts = new HashSet<String>(Arrays.asList(new String[] {
			"informalexample",
			"example"
	}));
	
	public static final Set<String> mustHaveId = new HashSet<String>(Arrays.asList(new String[] {
		"co", "example", "figure"	
	}));

	
	private static final class FileRec {
		public final String id;
		public final File f;
		
		public FileRec(final String id, final File f) {
			this.id = id;
			this.f = f;
		}
		
		@Override
		public String toString() {
			return "(" + id + "," + f + ")";
		}
	}

	private static final Comparator<String> compareIgnoreCase = new Comparator<String>() {
		@Override
		public int compare(final String o1, final String o2) {
			return o1.compareToIgnoreCase(o2);
		}
	};
	

	public final File workingDir;

	public ScanIDs(final File workingDir) {
		this.workingDir = workingDir;
	}
	
	
	private static final class OutlineHandler extends DefaultHandler {
		private LinkedList<String> tagStack = new LinkedList<String>();
		private StringBuilder titleBuf = null;

		@Override
		public void startElement(final String uri, 
				final String localName, final String qName, 
				final Attributes attributes) throws SAXException {
			if(qName.equals("title")) {
				titleBuf = new StringBuilder();
			}
			tagStack.addLast(qName);
		}
		
		@Override
		public void characters(final char[] ch,
                final int start,
                final int length)
                throws SAXException {
			if(null!=titleBuf) {
				for(int i=start;i<start+length;++i) {
					titleBuf.append(ch[i]);
				}
			}
		}
		
		@Override
		public void endElement(final String uri,
                final String localName,
                final String qName)
                throws SAXException {
			tagStack.removeLast();
			if(null!=titleBuf) {
				if((!tagStack.isEmpty())) {
					final String prevElt = tagStack.getLast();
					if(prevElt.equals("chapter")||prevElt.equals("sect1")) {
						for(int i=0;i<tagStack.size();++i) {
							System.out.print("\t");
						}
						System.out.println(titleBuf.toString());
					}
				}
				titleBuf = null;
			}
		}
	}
		
	private final class CheckHandler extends DefaultHandler {
		public String fi = null;
		public int nErrors = 0;
		public Locator locator = null;
		
		public final class TagRec implements Comparable<TagRec> {
			public final String fileName;
			public final String tagType;
			public final String fieldType;
			public final String id;
			public final int lineNum;
			public final int colNum;
			
			public TagRec(final String fileName, final String tagType, final String fieldType, final String id) {
				this.fileName = fileName;
				this.tagType = tagType;
				this.fieldType = fieldType;
				this.id = id;
				lineNum = locator.getLineNumber();
				colNum = locator.getColumnNumber();
			}
			
			@Override
			public String toString() {
				return  fileName + " (line: " + lineNum + ", col: " + colNum + "): <" + tagType + " " + fieldType +"=\"" + id + "\" />";
			}

			@Override
			public int compareTo(final TagRec o) {
				{
					final int cmp = fileName.compareTo(o.fileName);
					if(cmp!=0) {
						return cmp;
					}
				}
				if(lineNum!=o.lineNum) {
					if(lineNum>=o.lineNum) {
						return 1;
					} else {
						return -1;
					}
				}
				if(colNum!=o.colNum) {
					if(colNum>=o.colNum) {
						return 1;
					} else {
						return -1;
					}
				}
				{
					final int cmp = tagType.compareTo(o.tagType);
					if(cmp!=0) {
						return cmp;
					}
				}
				{
					final int cmp = fieldType.compareTo(o.fieldType);
					if(cmp!=0) {
						return cmp;
					}
				}
				{
					final int cmp = id.compareTo(o.id);
					if(cmp!=0) {
						return cmp;
					}
				}
				return 0;
			}
			
			@Override
			public boolean equals(final Object o) {
				return compareTo((TagRec)o)==0;
			}
			
			@Override
			public int hashCode() {
				return fileName.hashCode() + 3*lineNum + 7*colNum + 13*tagType.hashCode() + 29*fieldType.hashCode() + 37*id.hashCode();
			}
		}
		
		public final Map<String,TagRec> idToRec = new TreeMap<String,TagRec>(compareIgnoreCase); // map ids to record of XML element
		public final Map<String,TagRec> cantGloballyRef = new TreeMap<String,TagRec>(compareIgnoreCase); // ids that must be used (co callouts at this point)
		public final Map<String,TagRec> mustReferTo = new TreeMap<String,TagRec>(compareIgnoreCase); // ids must be used somewhere
		public final Map<String,TagRec> idRefToFirstIdRef = new TreeMap<String,TagRec>(compareIgnoreCase); // uses of ids to reference  (maps to exact casing of first use)
		public final Map<String,FileRec> fileRefToExamplePerXML = new TreeMap<String,FileRec>(compareIgnoreCase); // file refs in XML
		public final Map<String,FileRec> fileResfToExample = new TreeMap<String,FileRec>(compareIgnoreCase); // file refs overall
		// callout declaration to use matching
		private Set<String> knownCallOuts = null;
		private final Set<String> perXMLResourceDirs = new TreeSet<String>();
		private ArrayList<TagRec> callOutsMarks = null;
		private ArrayList<TagRec> callOutsTexts = null;

		public void startXMLFile(final String fi) {
			this.fi = fi;
			knownCallOuts = null;
			perXMLResourceDirs.clear();
		}
		
		@Override
		public void setDocumentLocator(final Locator locator) {
		    this.locator = locator;
		}

		private boolean goodID(final String s) {
			if(null==s) {
				return false;
			}
			if(s.length()<1) {
				return false;
			}
			if(!Character.isLetter(s.charAt(0))) {
				return false;
			}
			final Pattern space = Pattern.compile("\\s");
			final Matcher matcher = space.matcher(s);
			if(matcher.find()) {
				return false;
			}
			return true;
		}
		
		@Override
		public void startElement(final String uri, 
				final String localName, final String qName, 
				final Attributes attributes) throws SAXException {
			if(callOutContexts.contains(qName)) {
				knownCallOuts = new TreeSet<String>(compareIgnoreCase);
				callOutsMarks = new ArrayList<TagRec>();
				callOutsTexts = new ArrayList<TagRec>();
			}
			{
				final String IDFIELD = "id";
				final String id = attributes.getValue(IDFIELD);
				if(null!=id) {
					final TagRec idRec = new TagRec(fi,qName,IDFIELD,id);
					if(!goodID(id)) {
						System.out.println("Error: " + fi + " tag " + idRec + " bad id (must start with a letter and have no whitespace)");
						++nErrors;
					} else {
						if(idToRec.containsKey(id)) {
							System.out.println("Error: " + fi + " tag " + idRec + " duplicates tag " + idToRec.get(id));
							++nErrors;
						} else {
							idToRec.put(id,idRec);
							if("co".equalsIgnoreCase(qName)) {
								if(null==callOutsMarks) {
									System.out.println("Error: " + fi + " co " + idRec + " when not in a callout environment (example/informalexample)");
									++nErrors;									
								} else {
									if(knownCallOuts.contains(id)) {
										// not reached as error is currently handled elsewhere in the flow
										System.out.println("Error: " + fi + " co " + idRec + " duplicate callout tag");
										++nErrors;																			
									} else {
										callOutsMarks.add(idRec);
										knownCallOuts.add(id);
									}
								}
								cantGloballyRef.put(id,idRec);
							} else {
								if(mustHaveId.contains(qName)) {
									mustReferTo.put(id, idRec);
								}
							}
						}
					}
				} else {
					if(mustHaveId.contains(qName)) {
						final TagRec idRec = new TagRec(fi,qName,IDFIELD,id);
						System.out.println("Error: " + fi + " " + idRec + " must have an ID");
						++nErrors;
					}
				}
			}
			final String AREAREFKEY = "arearefs";
			for(final String field: new String[] {"linkend",AREAREFKEY }){
				final String linkEnd = attributes.getValue(field);
				if(null!=linkEnd) {
					final TagRec ourExample = new TagRec(fi,qName,field,linkEnd);
					if(!goodID(linkEnd)) {
						System.out.println("Error: " + ourExample + " bad id (must start with a letter and have no whitespace)");
						++nErrors;
					} else {
						final TagRec prevExample = idRefToFirstIdRef.get(linkEnd);
						if(null!=prevExample) {
							if(prevExample.id.compareTo(linkEnd)!=0) {
								System.out.println("Error: " + ourExample + " linkend " + linkEnd + " confusing casing with " + prevExample);
								++nErrors;						
							}
						} else {
							if(field.equals(AREAREFKEY)) {
								if(null==callOutsMarks) {
									System.out.println("Error: " + fi + " arearef " + ourExample + " when not in a callout environment (example/informalexample)");
									++nErrors;									
								} else {
									if(!knownCallOuts.contains(linkEnd)) {
										System.out.println("Error: " + fi + " co " + ourExample + " unknown callout tag");
										++nErrors;		
									} else {
										callOutsTexts.add(ourExample);
									}
								}
							} else {
								idRefToFirstIdRef.put(linkEnd,ourExample);
							}
						}
					}
				}
			}
			{
				final String FILEREFFIELD = "fileref";
				final String fileRef = attributes.getValue(FILEREFFIELD);
				if(null!=fileRef) {
					final TagRec here = new TagRec(fi,qName,FILEREFFIELD,fileRef);
					{ // global issues
						final FileRec prevGlobalExample = fileResfToExample.get(fileRef);
						if(null!=prevGlobalExample) {
							if(prevGlobalExample.id.compareTo(fileRef)!=0) {
								System.out.println("Error: " + here + " fileref " + fileRef + " confusing casing with " + prevGlobalExample);
								++nErrors;						
							}
						} else {
							final File ref = new File(workingDir,fileRef);
							fileResfToExample.put(fileRef,new FileRec(fileRef,ref));
							if((!ref.exists())||(!ref.canRead())) {
								System.out.println("Error: " + here + " missing referred file: " + fileRef);
								++nErrors;
							}
						}
					} 
					{ // per XML file issue
						final FileRec prevExample = fileRefToExamplePerXML.get(fileRef);
						if(null!=prevExample) {
						} else {
							final File ref = new File(workingDir,fileRef);
							fileRefToExamplePerXML.put(fileRef,new FileRec(fileRef,ref));
							final File resourceDir = ref.getParentFile();
							try {
								perXMLResourceDirs.add(resourceDir.getCanonicalPath().toString());
							} catch (IOException e) {
								System.out.println("Error: " + here + " threw on getCanonicalPath(): " + fileRef);
								++nErrors;
							}
						}
					}
				}
			}
		}
		
		@Override
		public void endElement(final String uri, final String localName, final String qName) throws SAXException {
			if(callOutContexts.contains(qName)) {
				// confirm parallel structure
				final int n = callOutsMarks.size();
				boolean disordered = n!=callOutsTexts.size();
				for(int i=0;(i<n)&&(!disordered);++i) {
					if(callOutsMarks.get(i).id.compareTo(callOutsTexts.get(i).id)!=0) {
						disordered = true;
					}
				}
				if(disordered) {
					final TagRec here = new TagRec(fi,qName,"","");
					System.out.println("Error: " + here + " callouts not in matching order");
					++nErrors;
				}
				// prepare for next pass
				knownCallOuts = null;
				callOutsMarks = null;
				callOutsTexts = null;
			}
		}
	}
	
	private static void scanForContent(final File dir, final Map<String,File> nameToPath) {
		final File[] files =  dir.listFiles();
		for(final File fi: files) {
			final String name = fi.getName();
			if((name.length()>0)&&(name.charAt(0)!='.')) {
				if(fi.isDirectory()) {
					scanForContent(fi,nameToPath);
				} else {
					final String nm = fi.getName();
					final File opath = nameToPath.get(nm);
					if(null!=opath) {
						System.out.println("WARN: paths " + opath + " and " + fi + " are confusing");
					} else {
						nameToPath.put(nm,fi);
					}
				}
			}
		}
	}
	
	public int doWork() throws IOException, ParserConfigurationException, SAXException {
		System.out.println("working in: " + workingDir.getAbsolutePath());
		final String ourSuffix = "_external_links.xml";
		final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		final SAXParser saxParser = saxFactory.newSAXParser();
		final ArrayList<String> fileNameList = new ArrayList<String>();
		final String bookFileName = "book.xml";
		final File bookFile = new File(workingDir,bookFileName);
		System.out.println("reading:\t" + bookFileName + "\t" + bookFile);
		System.out.println("\treading referenced content");
		saxParser.parse(bookFile, new DefaultHandler() {
			@Override
			public void startElement(final String uri, 
					final String localName, final String qName, 
					final Attributes attributes) throws SAXException {
				if("xi:include".compareTo(qName)==0) {
					final String href = attributes.getValue("href");
					if((null!=href)&&(href.length()>0)&&
							(href.endsWith(".xml"))&&(!href.endsWith(ourSuffix))&&(!bookFileName.equalsIgnoreCase(href))) {
						fileNameList.add(href);
					}
				}
			}			
		});
		{ // scan for chapter and sect 1 structure
			for(final String fi: fileNameList) {
				final File f = new File(workingDir,fi);
				//System.out.println("\treading: " + fi + "\t" + f);
				final OutlineHandler dataHandler = new OutlineHandler();
				saxParser.parse(f,dataHandler);
			}			
		}
		fileNameList.add(bookFileName);
		int totErrors = 0;
		final CheckHandler checkHandler = new CheckHandler();
		final Map<String,String> resourceDirToXML = new TreeMap<String,String>();
		{ // scan all files for tags
			for(final String fi: fileNameList) {
				final File f = new File(workingDir,fi);
				//System.out.println("\treading: " + fi + "\t" + f);
				checkHandler.startXMLFile(fi);
				saxParser.parse(f,checkHandler);
				if(!checkHandler.perXMLResourceDirs.isEmpty()) {
					for(final String di: checkHandler.perXMLResourceDirs) {
						final String otherXML = resourceDirToXML.get(di);
						if(null!=otherXML) {
							System.out.println("WARN: resource directory " + di + " used by " + fi + " and " + otherXML);
						}
						resourceDirToXML.put(di,fi);
					}					
				}
			}
		}
		totErrors += checkHandler.nErrors;
		final SortedMap<com.winvector.ScanIDs.CheckHandler.TagRec,String> foundErrors = new TreeMap<com.winvector.ScanIDs.CheckHandler.TagRec,String>();
		// check for broken/dangling links
		for(final com.winvector.ScanIDs.CheckHandler.TagRec linkend: checkHandler.idRefToFirstIdRef.values()) {
			final com.winvector.ScanIDs.CheckHandler.TagRec forbidden = checkHandler.cantGloballyRef.get(linkend.id);
			if(null!=forbidden) {
				foundErrors.put(linkend,"Error: illegal global ref from " + linkend + " to " + forbidden);
				++totErrors;
			} else {
				final com.winvector.ScanIDs.CheckHandler.TagRec rec = checkHandler.idToRec.get(linkend.id);
				if(null==rec) {
					foundErrors.put(linkend,"Error: link " + linkend + " broken");
					++totErrors;
				} else {
					if(rec.id.compareTo(linkend.id)!=0) {
						foundErrors.put(linkend,"Error: linkend " + linkend + " confusing casing with " + rec.id);
						++totErrors;	
					}
				}
			}
		}
		for(final com.winvector.ScanIDs.CheckHandler.TagRec dest: checkHandler.mustReferTo.values()) {
			final com.winvector.ScanIDs.CheckHandler.TagRec use = checkHandler.idRefToFirstIdRef.get(dest.id);
			if(null==use) {
				foundErrors.put(dest,"Error: linkend " + dest + " never referred to");
				++totErrors;	
			}
		}
		for(final String errmsgs: foundErrors.values()) {
			System.out.println(errmsgs);
		}
		// check content
		final Map<String,File> nameToPath = new TreeMap<String,File>(compareIgnoreCase);
		scanForContent(workingDir,nameToPath);
		final Set<File> filesSeen = new TreeSet<File>();
		filesSeen.addAll(nameToPath.values());
		for(final FileRec f: checkHandler.fileResfToExample.values()) {
			if(!filesSeen.contains(f.f)) {
				System.out.println("WARN: file " + f.f + " not found");
			} else {
				filesSeen.remove(f.f);
			}
		}
		for(final String fi: fileNameList) {
			final File f = new File(workingDir,fi);
			filesSeen.remove(f);
		}
		for(final File f: filesSeen) {
			if((f.getParentFile().compareTo(workingDir)!=0)&&
					(!f.getName().endsWith(ourSuffix))&&(!f.getName().endsWith("~"))&&(!f.getName().endsWith(".xsd"))) {
				System.out.println("WARN: file " + f + " not used");
			}
		}
		// write external links
		final String[] header = {
				   "<simplesect xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
				   "	xsi:schemaLocation=\"http://www.manning.com/schemas/book manning-book.xsd\"",
				   "	xmlns=\"http://www.manning.com/schemas/book\" xmlns:ns=\"http://www.manning.com/schemas/book\"",
				   "	xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">",
				   "	<title>FAKE SECTION IGNORE: External Links</title>",
		};
		final String[] footer = {
				   "</simplesect>",
		};
		System.out.println("\twriting links");
		for(final String fi: fileNameList) {
			if(fi.equalsIgnoreCase(bookFileName)) {
				continue;
			}
			final File resFile = new File(workingDir,fi + ourSuffix);
			//System.out.println("\twriting: " + resFile);
			final PrintStream p = new PrintStream(resFile);
			for(final String line: header) {
				p.println(line);
			}
			for(final com.winvector.ScanIDs.CheckHandler.TagRec idRec: checkHandler.idToRec.values()) {
				if(idRec.fileName.compareToIgnoreCase(fi)!=0) {
					p.println("	<para id=\"" + idRec.id + "\" xreflabel=\"XRF:" + idRec.tagType + ":" + idRec.id + "\" />");
				}
			}
			for(final String line: footer) {
				p.println(line);
			}
			p.close();			
		}
		System.out.println("total Errors: " + totErrors);
		System.out.println("done");
		return totErrors;
	}
	
	/**
	 * @param args
	 */
	public static void main(final String[] args) throws Exception {
		final File workingDir = new File(".");
		final ScanIDs scanner = new ScanIDs(workingDir);
		final int totErrors = scanner.doWork();
		if(totErrors>0) {
			System.exit(1);
		}
	}

}
