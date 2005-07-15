/*
 ******************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.tool;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.CLDRFile.Factory;

/**
 * Simple program to count the amount of data in CLDR. Internal Use.
 */
public class CountItems {
	private static Set keys = new HashSet();
	/**
	 * Count the data.
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		StandardCodes sc = StandardCodes.make();
		Map m = sc.getZoneLinkold_new();
		int i = 0;
		System.out.println("/* Generated by org.unicode.cldr.tool.CountItems */");
		for (Iterator it = m.keySet().iterator(); it.hasNext();) {
			String old = (String) it.next();
			String newOne = (String) m.get(old);
			System.out.println("\"" + old + "\", \"" + newOne + "\",");
			++i;
		}
		System.out.println("/* Total: " + i + " */");
		if (true) return;
		
		Factory cldrFactory = CLDRFile.Factory.make(Utility.MAIN_DIRECTORY, ".*");
    	Map platform_locale_status = StandardCodes.make().getLocaleTypes();
    	Map onlyLocales = (Map) platform_locale_status.get("IBM");
    	Set locales = onlyLocales.keySet();
    	CLDRFile english = cldrFactory.make("en", true);
    	for (Iterator it = locales.iterator(); it.hasNext();) {
    		String locale = (String) it.next();
    	   	System.out.println(locale + "\t" + english.getName(locale,false) + "\t" + onlyLocales.get(locale));  		
    	}
    	if (true) return;
 
		//CLDRKey.main(new String[]{"-mde.*"});
		int count = countItems(cldrFactory, false);
		System.out.println("Count (core): " + count);
		count = countItems(cldrFactory, true);
		System.out.println("Count (resolved): " + count);
		System.out.println("Unique XPaths: " + keys.size());
	}

	/**
	 * @param cldrFactory
	 * @param resolved
	 */
	private static int countItems(Factory cldrFactory, boolean resolved) {
		int count = 0;
		Set locales = cldrFactory.getAvailable();
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			CLDRFile item = cldrFactory.make(locale, resolved);
			keys.addAll(item.keySet());
			int current = item.keySet().size();
			System.out.println(locale + "\t" + current);
			count += current;
		}
		return count;
	}

}