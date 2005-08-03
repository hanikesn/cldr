/*
 * Created on May 19, 2005
 * Copyright (C) 2004-2005, Unicode, Inc., International Business Machines Corporation, and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.tool;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.tool.GenerateCldrTests.DataShower;
//import org.unicode.cldr.tool.GenerateCldrTests.Equator;
import org.unicode.cldr.tool.GenerateCldrTests.ResultsPrinter;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.ICUServiceBuilder;
import org.unicode.cldr.util.LDMLUtilities;
import org.unicode.cldr.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;


class GenerateCldrDateTimeTests {
    Map ulocale_exemplars = new TreeMap(GenerateCldrTests.ULocaleComparator);
    Map uniqueExemplars = new HashMap();
    Set locales = new TreeSet(GenerateCldrTests.ULocaleComparator);

    UnicodeSet getExemplarSet(ULocale locale) {
        return (UnicodeSet) ulocale_exemplars.get(locale);
    }

    void show() {
        Log.logln("Showing Locales");
        Log.logln("Unique Exemplars: " + uniqueExemplars.size());
        for (Iterator it2 = ulocale_exemplars.keySet().iterator(); it2.hasNext();) {
            ULocale locale = (ULocale) it2.next();
            UnicodeSet us = getExemplarSet(locale);
            Log.logln("\t" + locale + ", " + us);
        }
    }
    static final ULocale ROOT = new ULocale("root"); // since CLDR has different root.
	private Factory cldrFactory;
	ICUServiceBuilder icuServiceBuilder;
	

    GenerateCldrDateTimeTests(String sourceDir, String localeRegex, boolean doResolved) {
    	this.cldrFactory = CLDRFile.Factory.make(sourceDir, ".*");
    	icuServiceBuilder = new ICUServiceBuilder();
        Set s = GenerateCldrTests.getMatchingXMLFiles(sourceDir, localeRegex);
        for (Iterator it = s.iterator(); it.hasNext();) {
            getInfo((String) it.next(), doResolved);
        }
        // now do inheritance manually
        for (Iterator it = locales.iterator(); it.hasNext();) {
            ULocale locale = (ULocale) it.next();
            UnicodeSet ex = (UnicodeSet) ulocale_exemplars.get(locale);
            if (ex != null) continue;
            for (ULocale parent = locale.getFallback(); parent != null; parent = parent.getFallback()) {
                ULocale fixedParent = parent.getLanguage().length() == 0 ? ROOT : parent;
                ex = (UnicodeSet) ulocale_exemplars.get(fixedParent);
                if (ex == null) continue;
                ulocale_exemplars.put(locale, ex);
                break;
            }
        }

    }
    
    void getInfo(String locale, boolean doResolved) {
        System.out.println("Getting info for: " + locale);
        locales.add(new ULocale(locale));
        CLDRFile cldrFile = cldrFactory.make(locale, doResolved);
        //Node node = LDMLUtilities.getNode(doc, "//ldml/characters/exemplarCharacters");
        String cpath = "/ldml/characters/exemplarCharacters";
        String path = cldrFile.getFullXPath(cpath);
        if (path == null) return;
        //if (path.indexOf("[@draft=") >= 0) System.out.println("Skipping draft: " + locale + ",\t" + path);
        String exemplars = cldrFile.getStringValue(cpath);
        UnicodeSet exemplarSet = new UnicodeSet(exemplars);
        UnicodeSet fixed = (UnicodeSet) uniqueExemplars.get(exemplarSet);
        if (fixed == null) {
            uniqueExemplars.put(exemplarSet, exemplarSet);
            fixed = exemplarSet;
        }
        ulocale_exemplars.put(new ULocale(locale), fixed);
    }
    // ========== DATES ==========

/*    Equator DateEquator = new Equator() {
        *//**
         * Must both be ULocales
         *//*
        public boolean equals(Object o1, Object o2) {
            ULocale loc1 = (ULocale) o1;
            ULocale loc2 = (ULocale) o2;
            for (int i = 0; i < ICUServiceBuilder.LIMIT_DATE_FORMAT_INDEX; ++i) {
                for (int j = 0; j < ICUServiceBuilder.LIMIT_DATE_FORMAT_INDEX; ++j) {
                    if (i == 0 && j == 0) continue; // skip null case
                    DateFormat df1 = icuServiceBuilder.getDateFormat(loc1.toString(), i, j);
                    NumberFormat nf = df1.getNumberFormat();
                    nf.setCurrency(ICUServiceBuilder.NO_CURRENCY);
                    df1.setNumberFormat(nf);
                    DateFormat df2 = icuServiceBuilder.getDateFormat(loc2.toString(), i, j);
                    nf = df2.getNumberFormat();
                    nf.setCurrency(ICUServiceBuilder.NO_CURRENCY);
                    df2.setNumberFormat(nf);
                    if (!df1.equals(df2)) {
                        df1.equals(df2);
                        return false;
                    }
                }
            }
            return true;
        }
    };
*/
    
/*    Equator ZoneEquator = new Equator() {

		public boolean equals(Object o1, Object o2) {
            ULocale loc1 = (ULocale) o1;
            ULocale loc2 = (ULocale) o2;
            // locales are equivalent they have the same zone resources.
            CLDRFile cldrFile1 = cldrFactory.make(loc1.toString(),true);
            CLDRFile cldrFile2 = cldrFactory.make(loc1.toString(),true);
            for (int i = 0; i < ICUServiceBuilder.LIMIT_DATE_FORMAT_INDEX; ++i) {
                for (int j = 0; j < ICUServiceBuilder.LIMIT_DATE_FORMAT_INDEX; ++j) {
                    if (i == 0 && j == 0) continue; // skip null case
                    DateFormat df1 = icuServiceBuilder.getDateFormat(loc1.toString(), i, j);
                    NumberFormat nf = df1.getNumberFormat();
                    nf.setCurrency(ICUServiceBuilder.NO_CURRENCY);
                    df1.setNumberFormat(nf);
                    DateFormat df2 = icuServiceBuilder.getDateFormat(loc2.toString(), i, j);
                    nf = df2.getNumberFormat();
                    nf.setCurrency(ICUServiceBuilder.NO_CURRENCY);
                    df2.setNumberFormat(nf);
                    if (!df1.equals(df2)) {
                        df1.equals(df2);
                        return false;
                    }
                }
            }
			return false;
		}
    };
*/
    // ========== NUMBERS ==========


    
    /*
     * <numbers>
-
	<symbols>
<decimal>.</decimal>
<group>,</group>
<list>;</list>
<percentSign>%</percentSign>
<nativeZeroDigit>0</nativeZeroDigit>
<patternDigit>#</patternDigit>
<plusSign>+</plusSign>
<minusSign>-</minusSign>
<exponential>E</exponential>
<perMille>�</perMille>
<infinity>?</infinity>
<nan>?</nan>
</symbols>
-
	<decimalFormats>
-
	<decimalFormatLength>
-
	<decimalFormat>
<pattern>#,##0.###</pattern>

     */

 /*   Equator NumberEquator = new Equator() {
        *//**
         * Must both be ULocales
         *//*
        public boolean equals(Object o1, Object o2) {
            ULocale loc1 = (ULocale) o1;
            ULocale loc2 = (ULocale) o2;
            for (int i = 0; i < ICUServiceBuilder.LIMIT_NUMBER_INDEX; ++i) {
                NumberFormat nf1 = icuServiceBuilder.getNumberFormat(loc1.toString(), i);
                NumberFormat nf2 = icuServiceBuilder.getNumberFormat(loc2.toString(), i);
                boolean result = nf1.equals(nf2);
                if (!result) {
                    return false;
                }
            }
            return true;
        }
    };*/
    
	/**
	 * 
	 */
	public ICUServiceBuilder getICUServiceBuilder() {
		return icuServiceBuilder;
	}
}