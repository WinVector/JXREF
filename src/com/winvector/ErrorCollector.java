package com.winvector;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class ErrorCollector {
	public int nErrors = 0;
	private Map<String,ArrayList<String>> errorsByClass = new TreeMap<String,ArrayList<String>>();
	
	public void mkError(final String group, final String msg) {
		ArrayList<String> list = errorsByClass.get(group);
		if(null==list) {
			list = new ArrayList<String>();
			errorsByClass.put(group,list);
		}
		list.add(msg);
		++nErrors;
	}
	
	public void printReport(final PrintStream p) {
		p.println("totalErrorGroups: " + errorsByClass.size() + ", totalErrors: " + nErrors);
		for(final Entry<String, ArrayList<String>> me: errorsByClass.entrySet()) {
			final String group = me.getKey();
			final ArrayList<String> list = me.getValue();
			p.println();
			p.println("###########################################");
			p.println("Error group: " + group + ",\tsize: " + list.size());
			for(final String ei : list) {
				p.println("\t" + ei);
			}
			p.println();
		}
	}
}