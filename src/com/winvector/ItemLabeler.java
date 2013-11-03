package com.winvector;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Ugly little state machine to track item numbering
 * @author johnmount
 *
 */
public final class ItemLabeler extends DefaultHandler {
	// config
	private static final String CHAPTER = "chapter";
	private static final String TITLE = "title";
	private final String[] sectList = { CHAPTER, "sect1", "sect2" };
	// state
	private final LinkedList<String> tagStack = new LinkedList<String>();
	private String chapterNumber = "";
	private final int[] count = new int[sectList.length];
	private final String[] name = new String[sectList.length];
	private int depth = 0;
	private final Map<String,Integer> blockCounts = new TreeMap<String,Integer>();
	private StringBuilder chars = null;
	private String titleText = null;
	
	
	private String posKey(final String qName) {
		final StringBuilder b = new StringBuilder();
		b.append(qName);
		b.append("." + chapterNumber);
		for(int i=1;i<depth;++i) {
			b.append("." + count[i]);
		}
		return b.toString();
	}
	
	public String chapterName() {
		return name[0];
	}
	
	public String curPositionCode(final String qName) {
		final StringBuilder b = new StringBuilder();
		final String posKey = posKey(qName);
		b.append(qName + ":" + blockCounts.get(posKey));
		b.append("_" + chapterNumber);
		for(int i=1;i<depth;++i) {
			b.append("." + count[i]);
		}
		return b.toString();
	}
	
	public String curPositionDescription(final String qName) {
		final StringBuilder b = new StringBuilder();
		final String posKey = posKey(qName);
		b.append(qName + ":" + blockCounts.get(posKey));
		for(int i=0;i<depth;++i) {
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
		}
		final String posKey = posKey(qName);
		tagStack.removeLast();
		if(depth>0) {
			if(TITLE.equals(qName)) { 
				if(null==name[depth-1]) {  // waiting on a section title
					name[depth-1] = titleText;
				}
			} else {
				Integer oc = blockCounts.get(posKey);
				if(null==oc) {
					oc = 0;
				}
				blockCounts.put(posKey,oc+1);
			}
			if(sectList[depth-1].equals(qName)) {
				--depth;
			}
		}
		// prevent leaks on ill-formed XML
		if(TITLE.equals(qName)) { 
			titleText = null;
		}
	}
}
