package com.winvector;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public final class ExampleClipper extends DefaultHandler {

	
	public final class Clip {
		public String chapter;
		public String positionCode;
		public String positionDescription;
		public String clipTitle;
		public String progText;
		public ArrayList<String> calloutText;
		
		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			//b.append(chapter + lineBreak);
			b.append(positionCode + lineBreak);
			b.append(positionDescription + lineBreak);
			if((null!=clipTitle)&&(clipTitle.trim().length()>0)) {
				b.append(clipTitle + lineBreak);
			}
			b.append(progText + lineBreak);
			if(null!=calloutText) {
				for(int i=0;i<calloutText.size();++i) {
					b.append(lineBreak);
					b.append(openComment + " Note " + (i+1) + ": " + closeComment + lineBreak + openComment + "   ");
					final String ci = calloutText.get(i);
					b.append(ci.replaceAll("[ \t]+"," ").replaceAll("[\n\r]+"," " + closeComment + lineBreak + openComment + "  "));
					b.append(" " + closeComment + lineBreak);
				}
			}
			return b.toString();
		}
	}
	
	public static interface ClipConsumer {
		void takeClip(Clip clip) throws IOException;
	}
	
	public static String safeFileName(final String o) {
		String s = o.replaceAll("\\s+"," ").trim();
		s = s.replaceAll("[^\\w/ .]+","_");
		return s;
	}
	
	private static String cleanDirComponent(final String o) {
		String s = o.replaceAll("\\s+"," ").trim();
		s = s.replaceAll("[^\\w .]+","_");
		return s;
	}
	
	public static final class ClipZipper implements ClipConsumer {
		private final ZipOutputStream o;
		private final String dirName;
		private final NumberFormat clipNF = new DecimalFormat("00000000");
		private final NumberFormat chapNF = new DecimalFormat("00");
		private final Map<String,Integer> chNumbers = new HashMap<String,Integer>();
		private int clipNumber = 0;
		
		public ClipZipper(final ZipOutputStream o, final String dirName) {
			this.o = o;
			this.dirName = dirName;
		}
		
		@Override
		public void takeClip(final Clip clip) throws IOException {
			++clipNumber;
			Integer chNumber = chNumbers.get(clip.chapter);
			if(null==chNumber) {
				chNumber = chNumbers.size() + 1;
				chNumbers.put(clip.chapter,chNumber);
			}
			final String safeFileName = safeFileName(dirName 
					+ "/" + cleanDirComponent(chapNF.format(chNumber) + "_" + clip.chapter) 
					+ "/" + cleanDirComponent(clipNF.format(clipNumber) + "_" + clip.positionCode) 
					+ ".txt");
			final ZipEntry e = new ZipEntry(safeFileName);
			o.putNextEntry(e);
			final byte[] data = clip.toString().getBytes("UTF-8");
		    o.write(data, 0, data.length);
		    o.closeEntry();
		}
	}

	// config
	public boolean takeCallouts = true;
	public String openComment = "#";
	public String closeComment = "";
	public String lineBreak = "\n";
	private static final String CHAPTER = "chapter";
	private static final String TITLE = "title";
	private static final String PROGRAMLISTING = "programlisting";
	private static final String EXAMPLE = "example";
	private final String[] sectList = { CHAPTER, "sect1", "sect2" };
	private final String[] blocks = { EXAMPLE, "informalexample" };
	private final ClipConsumer clipConsumer;
	// state
	private final LinkedList<String> tagStack = new LinkedList<String>();
	private String chapterNumber = "";
	private final int[] count = new int[sectList.length];
	private final String[] name = new String[sectList.length];
	private int depth = 0;
	private final Map<String,Integer> blockCounts = new TreeMap<String,Integer>();
	private StringBuilder chars = null;
	private String titleText = null;
	private int nCallouts = 0;
	private String progText = null;
	private ArrayList<String> calloutText = null;
	private String progTitle = null;

	
	public ExampleClipper(final ClipConsumer clipConsumer) {
		this.clipConsumer = clipConsumer;
		for(final String b: blocks) {
			blockCounts.put(b,0);
		}
	}
	
	
	private String curPositionCode() {
		final StringBuilder b = new StringBuilder();
		b.append(chapterNumber);
		for(int i=1;i<depth;++i) {
			b.append("." + count[i]);
		}
		return b.toString();
	}
	
	private String curPositionDescription() {
		final StringBuilder b = new StringBuilder();
		b.append(name[0]);
		for(int i=1;i<depth;++i) {
			b.append(" : " + name[i]);
		}
		return b.toString();
	}
	
	@Override
	public void startElement(final String uri, 
			final String localName, final String qName, 
			final Attributes attributes) throws SAXException {
		if(qName.equals(CHAPTER)) {
			chapterNumber = attributes.getValue("xreflabel");
		} else 	if(qName.equals(TITLE)) {
			titleText = null;
			chars = new StringBuilder();
		} else if(qName.equals(PROGRAMLISTING)) {
			progText = null;
			calloutText = null;
			nCallouts = 0;
			chars = new StringBuilder();
		} else if(qName.equals("calloutlist")) {
			calloutText = new ArrayList<String>();
		}
		if(takeCallouts) {
			if(qName.equals("co")) {
				if(null!=chars) {
					++nCallouts;
					chars.append("\t" + openComment + " Note: " + nCallouts + " " + closeComment);
				}
			} else if(qName.equals("callout")) {
				chars = new StringBuilder();
			}
		}
		if((depth<sectList.length)&&(sectList[depth].equals(qName))) {
			count[depth] += 1;
			for(int i=depth+1;i<count.length;++i) {
				count[i] = 0;
			}
			name[depth] = null;
			++depth;
		}
		tagStack.addLast(qName);
	}
	
	@Override
	public void characters(final char[] ch,
            final int start,
            final int length)
            throws SAXException {
		if(null!=chars) {
			for(int i=start;i<start+length;++i) {
				chars.append(ch[i]);
			}
		}
	}
	
	@Override
	public void endElement(final String uri,
            final String localName,
            final String qName)
            throws SAXException {
		if(qName.equals(TITLE)) {
			titleText = chars.toString();
			chars = null;
		} else if(qName.equals(PROGRAMLISTING)) {
			progText = chars.toString();
			chars = null;
		} else if(qName.equals("callout")) {
			calloutText.add(chars.toString());
			chars = null;
		}
		tagStack.removeLast();
		if(depth>0) {
			if(TITLE.equals(qName)) { 
				if(null==name[depth-1]) {  // waiting on a section title
					name[depth-1] = titleText;
				} else {
					if((!tagStack.isEmpty())&&(EXAMPLE.equals(tagStack.getLast()))) {
						progTitle = titleText;
					}
				}
			} else {
				final Integer oc = blockCounts.get(qName);
				if(null!=oc) {
					if((null!=progText)&&(progText.trim().length()>0)) {
						final Clip clip = new Clip();
						clip.chapter = name[0];
						clip.positionCode = curPositionCode() + " " + qName + " " + (oc+1);
						clip.positionDescription = curPositionDescription() + " " + qName + " " + (oc+1);
						clip.clipTitle = progTitle;
						clip.progText = progText;
						clip.calloutText = calloutText;
						if(null!=clipConsumer) {
							try {
								clipConsumer.takeClip(clip);
							} catch (IOException e) {
								throw new SAXException("caught: " + e);
							}
						}
					}
					blockCounts.put(qName,oc+1);
				}
			}
			if(sectList[depth-1].equals(qName)) {
				--depth;
			}
		}
		// prevent leaks on ill-formed XML
		if(TITLE.equals(qName)) { 
			titleText = null;
		}
		if(blockCounts.containsKey(qName)) {
			titleText = null;
			progTitle = null;
			progText = null;
			calloutText = null;
			nCallouts = 0;
		}
	}
}
