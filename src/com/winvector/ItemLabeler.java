package com.winvector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Ugly little state machine to track item numbering.
 * Users must call startElement(), endElement() and characters()
 * @author johnmount
 *
 */
public final class ItemLabeler extends DefaultHandler {
	// config
	private static final String TITLE = "title";
	private final ArrayList<Set<String>> sectList = new ArrayList<Set<String>>();
	{
		sectList.add(new TreeSet<String>(Arrays.asList(new String[] {"chapter", "part", "appendix"})));
		sectList.add(new TreeSet<String>(Arrays.asList(new String[] {"sect1"})));
		sectList.add(new TreeSet<String>(Arrays.asList(new String[] {"sect2"})));
	}
	// state
	private final LinkedList<String> tagStack = new LinkedList<String>();
	private String chapterNumber = "";
	private final int[] count = new int[sectList.size()];
	private final String[] name = new String[sectList.size()];
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
		b.append(qName);
		final String posKey = posKey(qName);
		final Integer bc = blockCounts.get(posKey);
		if(null!=bc) {
			b.append(":" + bc);
		}
		b.append("_" + chapterNumber);
		for(int i=1;i<depth;++i) {
			b.append("." + count[i]);
		}
		return b.toString();
	}
	
	public String curPositionDescription(final String qName) {
		final StringBuilder b = new StringBuilder();
		b.append(qName);
		final String posKey = posKey(qName);
		final Integer bc = blockCounts.get(posKey);
		if(null!=bc) {
			b.append(":" + bc);
		}
		for(int i=0;i<depth;++i) {
			b.append(" : " + name[i]);
		}
		return b.toString();
	}
	
	@Override
	public void startElement(final String uri, 
			final String localName, final String qName, 
			final Attributes attributes) throws SAXException {
		if(sectList.get(0).contains(qName)) {
			chapterNumber = attributes.getValue("xreflabel");
		} else 	if(qName.equals(TITLE)) {
			titleText = null;
			chars = new StringBuilder();
		}
		if((depth<sectList.size())&&(sectList.get(depth).contains(qName))) {
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
			if(sectList.get(depth-1).contains(qName)) {
				--depth;
			}
		}
		// prevent leaks on ill-formed XML
		if(TITLE.equals(qName)) { 
			titleText = null;
		}
	}
}
