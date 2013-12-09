package com.winvector;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * State machine to extract program listings and callouts.
 * Users must call startElement(), endElement() and characters()
 * @author johnmount
 *
 */
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
			b.append(openComment + " " + positionCode + " " + closeComment + lineBreak);
			b.append(openComment + " " + positionDescription + " " + closeComment + lineBreak);
			if((null!=clipTitle)&&(clipTitle.trim().length()>0)) {
				b.append(openComment + " " + clipTitle  + " " + closeComment + lineBreak);
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
		String s = o.replaceAll("\\s+"," ").trim().replace(' ','_');
		s = s.replaceAll("[^\\w.]+","_");
		return s;
	}
	
	public static final class ClipZipper implements ClipConsumer {
		private final ZipOutputStream o;
		private final String dirName;
		private final NumberFormat clipNF = new DecimalFormat("00000");
		private final NumberFormat chapNF = new DecimalFormat("00");
		private final Map<String,Integer> chNumbers = new HashMap<String,Integer>();
		private int clipNumber = 0;
		
		public ClipZipper(final ZipOutputStream o, final String dirName, final String readmeStr) throws IOException {
			this.o = o;
			this.dirName = dirName;
			final String safeFileName = safeFileName(dirName + "/" + "README.txt");
			final ZipEntry e = new ZipEntry(safeFileName);
			o.putNextEntry(e);
			final byte[] data = readmeStr.getBytes("UTF-8");
		    o.write(data, 0, data.length);
		    o.closeEntry();
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
			if(clip.progText.indexOf("&amp;")>=0) {
				System.out.println("Warning clip has '&amp;'s: " + safeFileName);
			}
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
	private static final String PROGRAMLISTING = "programlisting";
	private static final String EXAMPLE = "example";
	private final Set<String> blocks = new HashSet<String>(Arrays.asList(new String[] { EXAMPLE, "informalexample" }));
	private final ClipConsumer clipConsumer;
	// state
	public final ItemLabeler itemLabeler = new ItemLabeler();
	private StringBuilder chars = null;
	private int nCallouts = 0;
	private String progText = null;
	private ArrayList<String> calloutText = null;
	private String progTitle = null;

	
	public ExampleClipper(final ClipConsumer clipConsumer) {
		this.clipConsumer = clipConsumer;
	}
	
	
	@Override
	public void startElement(final String uri, 
			final String localName, final String qName, 
			final Attributes attributes) throws SAXException {
		itemLabeler.startElement(uri, localName, qName, attributes);
		if(qName.equals(PROGRAMLISTING)) {
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
	}
	
	@Override
	public void characters(final char[] ch,
            final int start,
            final int length)
            throws SAXException {
		itemLabeler.characters(ch, start, length);
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
		itemLabeler.endElement(uri, localName, qName);
		if(qName.equals(PROGRAMLISTING)) {
			progText = chars.toString();
			chars = null;
		} else if(qName.equals("callout")) {
			calloutText.add(chars.toString());
			chars = null;
		}
		if(blocks.contains(qName)) {
			if((null!=progText)&&(progText.trim().length()>0)) {
				final Clip clip = new Clip();
				clip.chapter = itemLabeler.chapterName();
				clip.positionCode = itemLabeler.curPositionCode(qName);
				clip.positionDescription = itemLabeler.curPositionDescription(qName);
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
		}
	}
}
