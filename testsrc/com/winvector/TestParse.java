package com.winvector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.Test;

import com.winvector.ExampleClipper.ClipZipper;



public class TestParse {
	@Test
	public void test() throws Exception {
		final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		final SAXParser saxParser = saxFactory.newSAXParser();
		final File f = File.createTempFile("testZip",".zip");
		//System.out.println("writing: " + f.getAbsolutePath());
		final ZipOutputStream o = new ZipOutputStream(new FileOutputStream(f));
		final ExampleClipper sectCounter = new ExampleClipper(new ClipZipper(o,"bd"));
		final InputStream source = this.getClass().getClassLoader().getResourceAsStream("com/winvector/ExampleElt.xml");
		saxParser.parse(source,sectCounter);
		o.close();
		f.delete();
	}
}
