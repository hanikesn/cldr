/*
 ******************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xml.sax.SAXException;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.test.util.BagFormatter;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.TimezoneFormatter;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.StringValue;
import org.unicode.cldr.util.CLDRFile.Value;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UnicodeSet;

/**
 * Initial version of CLDR tests
 */
public class CLDRTest extends TestFmwk {
	
	private static String MATCH; // = ".*"; // "fr" ".*";
	private boolean skipDraft = true;
	private Set locales;
	private Set languageLocales;
	private CLDRFile.Factory cldrFactory;
	private CLDRFile resolvedRoot;
	private CLDRFile resolvedEnglish;
	
	/**
	 * TestFmwk boilerplate
	 */
	public static void main(String[] args) throws Exception {
        MATCH = System.getProperty("XML_MATCH");
        if (MATCH == null) MATCH = ".*";

        double deltaTime = System.currentTimeMillis();
        new CLDRTest().run(args);
        deltaTime = System.currentTimeMillis() - deltaTime;
        System.out.println("Seconds: " + deltaTime/1000);
    }
	
	/**
	 * TestFmwk boilerplate
	 */
	public CLDRTest() throws SAXException, IOException {
		// TODO parameterize the directory and filter
		cldrFactory = CLDRFile.Factory.make(Utility.MAIN_DIRECTORY, MATCH);
		//CLDRKey.main(new String[]{"-mde.*"});
		locales = cldrFactory.getAvailable();
		languageLocales = cldrFactory.getAvailableLanguages();
		resolvedRoot = cldrFactory.make("root", true);
		/*
		PrintWriter out = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY + "resolved/", "root.xml");
		CLDRFile temp = (CLDRFile) resolvedRoot.clone();
		temp.write(out);
		out.close();
		*/
		resolvedEnglish = cldrFactory.make("en", true);
	}
	
	/**
	 * Check to make sure that the currency formats are kosher.
	 */
	public void TestCurrencyFormats() {
	    //String decimal = "/ldml/numbers/decimalFormats/decimalFormatLength/decimalFormat[@type=\"standard\"]/";
	    //String currency = "/ldml/numbers/currencyFormats/currencyFormatLength/currencyFormat[@type=\"standard\"]/";
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			boolean isPOSIX = locale.indexOf("POSIX") >= 0;
			logln("Testing: " + locale);
			CLDRFile item = cldrFactory.make(locale, false);
			for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				byte type = getNumericType(xpath);
				if (type == NOT_NUMERIC_TYPE) continue;
				CLDRFile.StringValue value = (StringValue) item.getValue(xpath);
				// at this point, we only have currency formats
				String pattern = getCanonicalPattern(value.getStringValue(), type, isPOSIX);
				if (!pattern.equals(value.getStringValue())) {
					String draft = "";
					if (value.getFullXPath().indexOf("[@draft=\"true\"]") >= 0) draft = " [draft]";
					assertEquals(getLocaleAndName(locale) + draft + " " + TYPE_NAME[type] + " pattern incorrect", pattern, value.getStringValue());
				}
			}
		}
	}
	
	/**
	 * @return
	 */
	public static byte getNumericType(String xpath) {
		if (!xpath.startsWith("/ldml/numbers/")) {
			return NOT_NUMERIC_TYPE;
		}
		if (xpath.startsWith("/ldml/numbers/currencyFormats/")) {
			return CURRENCY_TYPE;
		} else if (xpath.startsWith("/ldml/numbers/decimalFormats/")) {
			return DECIMAL_TYPE;
		} else if (xpath.startsWith("/ldml/numbers/percentFormats/")) {
			return PERCENT_TYPE;
		} else if (xpath.startsWith("/ldml/numbers/scientificFormats/")) {
			return SCIENTIFIC_TYPE;
		} else if (xpath.startsWith("/ldml/numbers/currencies/currency/")
				&& xpath.indexOf("pattern") >= 0) {
			return CURRENCY_TYPE;
		} else {
			return NOT_NUMERIC_TYPE;
		}
	}

	public static final byte NOT_NUMERIC_TYPE = -1, CURRENCY_TYPE = 0, DECIMAL_TYPE = 1, PERCENT_TYPE = 2, SCIENTIFIC_TYPE = 3;
	public static final String[] TYPE_NAME = {"currency", "decimal", "percent", "scientific"};
	static int[][] DIGIT_COUNT = {{1,2,2}, {1,0,3}, {1,0,0}, {0,0,0}};
	static int[][] POSIX_DIGIT_COUNT = {{1,2,2}, {1,0,6}, {1,0,0}, {1,6,6}};
	
	public static String getCanonicalPattern(String inpattern, byte type, boolean isPOSIX) {
		// TODO fix later to properly handle quoted ;
		DecimalFormat df = new DecimalFormat(inpattern);
		int decimals = type == CURRENCY_TYPE ? 2 : 1;
		int[] digits = isPOSIX ? POSIX_DIGIT_COUNT[type] : DIGIT_COUNT[type];
		df.setMinimumIntegerDigits(digits[0]);
		df.setMinimumFractionDigits(digits[1]);
		df.setMaximumFractionDigits(digits[2]);
		String pattern = df.toPattern();

		int pos = pattern.indexOf(';');
		if (pos < 0) return pattern + ";-" + pattern;
		return pattern;
	}
	
	static class ValueCount {
		int count = 1;
		Value value;
	}
	
	/**
	 * Verify that if all the children of a language locale do not have the same value for the same key.
	 */
	public void TestCommonChildren() {
		Map currentValues = new TreeMap();
		Set okValues = new TreeSet();
	
		for (Iterator it = languageLocales.iterator(); it.hasNext();) {
			String parent = (String)it.next();
			logln("Testing: " + parent);		
			currentValues.clear();
			okValues.clear();
			Set availableWithParent = cldrFactory.getAvailableWithParent(parent, true);
			for (Iterator it1 = availableWithParent.iterator(); it1.hasNext();) {
				String locale = (String)it1.next();
				logln("\tTesting: " + locale);
				CLDRFile item = cldrFactory.make(locale, false);
				// Walk through all the xpaths, adding to currentValues
				// Whenever two values for the same xpath are different, we remove from currentValues, and add to okValues
				for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
					String xpath = (String) it2.next();
					if (okValues.contains(xpath)) continue;
					if (xpath.startsWith("/ldml/identity/")) continue; // skip identity elements
					Value v = item.getValue(xpath);
					ValueCount last = (ValueCount) currentValues.get(xpath);
					if (last == null) {
						ValueCount vc = new ValueCount();
						vc.value = v;
						currentValues.put(xpath, vc);
					} else if (v.equals(last.value)) {
						last.count++;
					} else {
						okValues.add(xpath);
						currentValues.remove(xpath);
					}
				}
				// at the end, only the keys left in currentValues are (possibly) faulty
				// they are actually bad IFF either 
				// (a) the count is equal to the total (thus all children are the same), or
				// (b) their value is the same as the parent's resolved value (thus all children are the same or the same
				// as the inherited parent value).
			}
			if (currentValues.size() == 0) continue;
			int size = availableWithParent.size();
			CLDRFile parentCLDR = cldrFactory.make(parent, true);
			XPathParts p = new XPathParts(null, null);
			for (Iterator it2 = currentValues.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				ValueCount vc = (ValueCount) currentValues.get(xpath);
				if (vc.count == size || vc.value.equals(parentCLDR.getValue(xpath))) {
					String draft = "";
					if (true) {
						if (vc.value.getFullXPath().indexOf("[@draft=\"true\"]") >= 0) draft = " [draft]";
					} else {
						p.set(vc.value.getFullXPath());
						if (p.containsAttributeValue("draft","true")) draft = " [draft]";
					}
					String count = (vc.count == size ? "" : vc.count + "/") + size;
					errln(getLocaleAndName(parent) + draft +
							", all children (" + count + ") have same value for:\t"
							+ xpath + ";\t" + vc.value.getStringValue());
				}
			}
		}
	}
	
	final UnicodeSet commonAndInherited = new UnicodeSet("[[:script=common:][:script=inherited:][:alphabetic=false:]]");
	
	/**
	 * Check that the exemplars include all characters in the data.
	 */
	public void TestThatExemplarsContainAll() {
		Set counts = new TreeSet();
		int totalCount = 0;
		UnicodeSet localeMissing = new UnicodeSet();
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			if (locale.equals("root")) continue; 
			CLDRFile resolved = cldrFactory.make(locale, true);
			UnicodeSet exemplars = getFixedExemplarSet(locale, resolved);
			CLDRFile plain = cldrFactory.make(locale, false);
			int count = 0;
			localeMissing.clear();
			for (Iterator it2 = plain.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				if (xpath.indexOf("/exemplarCharacters") > 0
						|| xpath.indexOf("/pattern") > 0) continue; // skip some items.
				Value pvalue = plain.getValue(xpath);
				if (skipDraft && pvalue.getFullXPath().indexOf("[@draft=\"true\"") > 0) continue;
				String value = pvalue.getStringValue();
				if (!exemplars.containsAll(value)) {
					count++;
					UnicodeSet missing = new UnicodeSet().addAll(value).removeAll(exemplars);
					localeMissing.addAll(missing);
					errln(getLocaleAndName(locale) + "\t" + xpath + "/" + value + " contains " + missing + ", not in exemplars");					
				}
			}
			NumberFormat nf = new DecimalFormat("000");
			if (count != 0) {
				totalCount += count;
				counts.add(nf.format(count) + "\t" + getLocaleAndName(locale) + "\t" + localeMissing);
			}
		}
		for (Iterator it = counts.iterator(); it.hasNext();) {
			logln(it.next().toString());
		}
		logln("Total Count: " + totalCount);
	}

	/**
	 * @param locale
	 * @param cldrfile
	 * @return
	 */
	private UnicodeSet getFixedExemplarSet(String locale, CLDRFile cldrfile) {
		UnicodeSet exemplars = getExemplarSet(cldrfile,"");
		if (exemplars.size() == 0) {
			errln(getLocaleAndName(locale) + " has empty exemplar set");
		}
		exemplars.addAll(getExemplarSet(cldrfile,"standard"))
		.addAll(getExemplarSet(cldrfile,"auxiliary"))
		.addAll(commonAndInherited);
		return exemplars;
	}

	/**
	 * @param cldrfile
	 * @return
	 */
	public UnicodeSet getExemplarSet(CLDRFile cldrfile, String type) {
		if (type.length() != 0) type = "[@type=\"" + type + "\"]";
		Value v = cldrfile.getValue("/ldml/characters/exemplarCharacters" + type);
		if (v == null) return new UnicodeSet();
		String pattern = v.getStringValue();
		if (pattern.indexOf("[:") >= 0 || pattern.indexOf("\\p{") > 0) {
			errln(getLocaleName(cldrfile.getKey()) + " exemplar pattern contains property: " + pattern);
		}
		UnicodeSet result = new UnicodeSet(v.getStringValue(), UnicodeSet.CASE);
		if (type.length() != 0) System.out.println("fetched set for " + type);
		return result;
	}
	
	Map localeNameCache = new HashMap();
	CLDRFile english = null;

	public String getLocaleAndName(String locale) {
		return locale + " (" + getLocaleName(locale) + ")";
	}
	
	public String getIDAndLocalization(String id) {
		return id + " " + getLocalization(id);
	}

	public String getLocalization(String id) {
		if (english == null) english = cldrFactory.make("en", true);
		if (id.length() == 0) return "?";
		// pick on basis of case
		char ch = id.charAt(0);
		if ('a' <= ch && ch <= 'z') return getName(english, "languages/language", id);
		if (id.length() == 4 && 'A' <= ch && ch <= 'Z') return getName(english, "scripts/script", id);
		return getName(english, "territories/territory", id);
	}

	/**
	 * @param missing
	 * @return
	 */
	private String getIDAndLocalization(Set missing) {
		StringBuffer buffer = new StringBuffer();
		for (Iterator it3 = missing.iterator(); it3.hasNext();) {
			if (buffer.length() != 0) buffer.append("; ");
			buffer.append(getIDAndLocalization((String)it3.next()));
		}
		String s = buffer.toString();
		return s;
	}


	public String getLocaleName(String locale) {
		String name = (String) localeNameCache.get(locale);
		if (name != null) return name;
		if (english == null) english = cldrFactory.make("en", true);
		String result = english.getName(locale, false);
		/*
		Collection c = Utility.splitList(locale, '_', false, null);
		String[] pieces = new String[c.size()];
		c.toArray(pieces);
		int i = 0;
		String result = getName(english, "languages/language", pieces[i++]);
		if (pieces[i].length() == 0) return result;
		if (pieces[i].length() == 4) {
			result += " " + getName(english, "scripts/script", pieces[i++]);
		}
		if (pieces[i].length() == 0) return result;
		result += " " + getName(english, "territories/territory", pieces[i++]);
		if (pieces[i].length() == 0) return result;
		result += " " + getName(english, "variant/variants", pieces[i++]);
		*/
		localeNameCache.put(locale, result);
		return result;
	}

	/**
	 * @param english
	 * @param pieces
	 * @param i
	 * @return
	 */
	private String getName(CLDRFile english, String kind, String type) {
		Value v = english.getValue("/ldml/localeDisplayNames/" + kind + "[@type=\"" + type + "\"]");
		if (v == null) return "<" + type + ">";
		return v.getStringValue();
	}
	
	/**
	 * Make sure we are only using attribute values that are in RFC3066, the Olson database (with aliases removed)
	 * or ISO 4217
	 * @throws IOException
	 */
	public void TestForIllegalAttributeValues()  {
		// check for illegal attribute values that are not in the DTD
		Map result = new TreeMap();
		Map totalResult = new TreeMap();
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			logln("Testing: " + locale);
			CLDRFile item = cldrFactory.make(locale, false);
			result.clear();
			Set xpathFailures = null; // don't collect
			//XPathParts parts;
			//String xpath;
			//CLDRFile.StringValue value;
			//String element;
			//Map attributes;
			checkAttributeValidity(item, result, xpathFailures);
			
			// now show
			String localeName = getLocaleAndName(locale);
			for (Iterator it3 = result.keySet().iterator(); it3.hasNext();) {
				String code = (String) it3.next();
				Set avalues = (Set) result.get(code);
				errln(getLocaleAndName(locale) + "\tillegal attribute value for " + code + ", value:\t" + show(avalues));
				Set totalvalues = (Set)totalResult.get(code);
				if (totalvalues == null) totalResult.put(code, totalvalues = new TreeSet());
				totalvalues.addAll(avalues);
			}
		}
		for (Iterator it3 = totalResult.keySet().iterator(); it3.hasNext();) {
			String code = (String) it3.next();
			Set avalues = (Set) totalResult.get(code);
			errln("All illegal attribute values for " + code + ", value:\t" + show(avalues));
		}
	}
	
	/**
	 * Tests whether the display names have any collisions, e.g. if in the fully resolved
	 * locale $ is used for both USD and UAD.
	 *
	 */
	public void testDisplayNameCollisions() {
		Map[] maps = new HashMap[CLDRFile.LIMIT_TYPES];
		for (int i = 0; i < maps.length; ++i) maps[i] = new HashMap();
		Set collisions = new TreeSet();
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			logln("Testing: " + locale);
			CLDRFile item = cldrFactory.make(locale, true);
			for (int i = 0; i < maps.length; ++i) maps[i].clear();
			collisions.clear();
			
			for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				int nameType = CLDRFile.getNameType(xpath);
				if (nameType < 0) continue;
				String value = ((StringValue) item.getValue(xpath)).getStringValue();
				String xpath2 = (String) maps[nameType].get(value);
				if (xpath2 == null) {
					maps[nameType].put(value, xpath);
					continue;
				}
				collisions.add(CLDRFile.getNameTypeName(nameType) + "\t" + value + "\t" + xpath + "\t" + xpath2);
			}
			
			for (Iterator it2 = collisions.iterator(); it2.hasNext();) {
				errln((String)it2.next());
			}
		}		
	}

	/**
	 * @param item
	 * @param badCodes
	 * @param xpathFailures
	 */
	public static void checkAttributeValidity(CLDRFile item, Map badCodes, Set xpathFailures) {
		XPathParts parts = new XPathParts(null, null);
		for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
			String xpath = (String) it2.next();
			CLDRFile.StringValue value = (StringValue) item.getValue(xpath);
			parts.set(value.getFullXPath());
			for (int i = 0; i < parts.size(); ++i) {
				String element = parts.getElement(i);
				Map attributes = parts.getAttributes(i);
				if (attributes == null) continue;
				for (Iterator it3 = attributes.keySet().iterator(); it3.hasNext();) {
					String attribute = (String) it3.next();
					String avalue = (String) attributes.get(attribute);
					checkValidity(xpath, element, attribute, avalue, badCodes, xpathFailures);
				}
			}
		}
	}

	/**
	 * @param avalues
	 * @return
	 */
	private String show(Collection avalues) {
		StringBuffer result = new StringBuffer("{");
		boolean first = true;
		for (Iterator it3 = avalues.iterator(); it3.hasNext();) {
			if (first) first = false;
			else result.append(", ");
			result.append(it3.next().toString());
		}
		result.append("}");
		return result.toString();
	}

	/**
	 * @param xpath TODO
	 * @param element
	 * @param attribute
	 * @param avalue
	 * @param xpathsFailing TODO
	 * @throws IOException
	 */
	private static void checkValidity(String xpath, String element, String attribute, String avalue, Map results, Set xpathsFailing)  {
		StandardCodes codes = StandardCodes.make();
		if (attribute.equals("type")) {
			if (element.equals("currency")) checkCodes(xpath, "currency", avalue, codes, results, xpathsFailing);
			else if (element.equals("script")) checkCodes(xpath, "script", avalue, codes, results, xpathsFailing);
			else if (element.equals("territory")) checkCodes(xpath, "territory", avalue, codes, results, xpathsFailing);
			else if (element.equals("language")) checkCodes(xpath, "language", avalue, codes, results, xpathsFailing);
			else if (element.equals("zone")) checkCodes(xpath, "tzid", avalue, codes, results, xpathsFailing);
		}
	}

	/**
	 * @param xpath TODO
	 * @param avalue
	 * @param codes
	 * @param xpathFailures TODO
	 * @param locale
	 */
	private static void checkCodes(String xpath, String code, String avalue, StandardCodes codes, Map results, Set xpathFailures) {
		if (codes.getData(code, avalue) == null) {
			if (xpathFailures != null) xpathFailures.add(xpath);
			if (results == null) return;
			Set s = (Set) results.get(code);
			if (s == null) {
				s = new TreeSet();
				results.put(code, s);
			}
			s.add(avalue);
		}
	}

	/**
	 * Verify that English has everything translated.
	 * @throws IOException
	 */
	public void TestTranslatedCodes() {
		// just test English for now
		if (english == null) english = cldrFactory.make("en", true);		
		checkTranslatedCodes(english);
	}

	/**
	 * @throws IOException
	 */
	private void checkTranslatedCodes(CLDRFile cldrfile)  {
		StandardCodes codes = StandardCodes.make();
		checkTranslatedCode(cldrfile, codes, "currency", "/ldml/numbers/currencies/currency", "/displayName");
		checkTranslatedCode(cldrfile, codes, "tzid", "/ldml/dates/timeZoneNames/zone", "");
		checkTranslatedCode(cldrfile, codes, "language", "/ldml/localeDisplayNames/languages/language", "");
		checkTranslatedCode(cldrfile, codes, "script", "/ldml/localeDisplayNames/scripts/script", "");
		checkTranslatedCode(cldrfile, codes, "territory", "/ldml/localeDisplayNames/territories/territory", "");
		checkTranslatedCode(cldrfile, codes, "variant", "/ldml/localeDisplayNames/variants/variant", "");
	}

	/**
	 * @param codes
	 * @param type
	 * @param prefix
	 * @param postfix TODO
	 */
	private void checkTranslatedCode(CLDRFile cldrfile, StandardCodes codes, String type, String prefix, String postfix) {
		Set codeItems = codes.getAvailableCodes(type);
		int count = 0;
		for (Iterator it = codeItems.iterator(); it.hasNext();) {
			String code = (String) it.next();
			String rfcname = codes.getData(type, code);
			//if (rfcname.equals("ZZ")) continue;
			++count;
			String fullFragment = prefix + "[@type=\"" + code + "\"]" + postfix;
			Value v = cldrfile.getValue(fullFragment);
			if (v == null) {
				errln(" Missing English translation for:\t<" + type + " type=\""  + code + "\">" + rfcname + "</" + type + ">");
				continue;
			}
			String translation = v.getStringValue();
			if (!translation.equalsIgnoreCase(rfcname)) {
				logln(type + " translation differs from RFC, check: " + code + "\trfc: " + rfcname + "\tcldr: " + translation);
			}
		}
		logln("Total " + type + ":\t" + count);
	}
	
	// <territoryContainment><group type="001" contains="002 009 019 142 150"/>
	// <languageData><language type="af" scripts="Latn" territories="ZA"/>
	void getSupplementalData(Map language_scripts, Map language_territories, Map group_territory, Map territory_currencies) {
		boolean SHOW = false;
		Factory cldrFactory = Factory.make(Utility.MAIN_DIRECTORY, ".*");
		CLDRFile supp = cldrFactory.make(CLDRFile.SUPPLEMENTAL_NAME, false);
		XPathParts parts = new XPathParts(null, null);
		for (Iterator it = supp.keySet().iterator(); it.hasNext();) {
			String path = (String) it.next();
			Value v = supp.getValue(path);
			parts.set(v.getFullXPath());
			Map m;
			if (territory_currencies != null) {
				m = parts.findAttributes("region");
				if (m != null) {
					String region = (String) m.get("iso3166");
					Set s = (Set) territory_currencies.get(region);
					if (s == null) territory_currencies.put(region, s = new LinkedHashSet());
					m = parts.findAttributes("currency");
					if (m == null) {
						System.out.println("Error: missing currency; " + path);
						continue;
					}
					String currency = (String) m.get("iso4217");
					s.add(currency);
					m = parts.findAttributes("alternate");
					String alternate = m == null ? null : (String) m.get("iso4217");
					if (alternate != null) s.add(alternate);
					continue;
				}
			}
			m = parts.findAttributes("group");
			if (m != null) {
				if (group_territory == null) continue;
				String type = (String) m.get("type");
				String contains = (String) m.get("contains");
				group_territory.put(type, new TreeSet(Utility.splitList(contains,' ', true)));
				continue;
			}
			m = parts.findAttributes("language");
			if (m == null) continue;
			String language = (String) m.get("type");
			String scripts = (String) m.get("scripts");
			if (scripts == null) language_scripts.put(language, new TreeSet());
			else {
				language_scripts.put(language, new TreeSet(Utility.splitList(scripts,' ', true)));
				if (SHOW) System.out.println(getIDAndLocalization(language) + "\t\t" + getIDAndLocalization((Set)language_scripts.get(language)));
			}
			String territories = (String) m.get("territories");
			if (territories == null)  language_territories.put(language, new TreeSet());
			else {
				language_territories.put(language, new TreeSet(Utility.splitList(territories,' ', true)));
				if (SHOW) System.out.println(getIDAndLocalization(language) + "\t\t" + getIDAndLocalization((Set)language_territories.get(language)));
			}
		}
	}

	public void TestMinimalLocalization() throws IOException {
		boolean testDraft = false;
		Map language_scripts = new HashMap();
		Map language_territories = new HashMap();
		getSupplementalData(language_scripts, language_territories, null, null);
		LanguageTagParser localIDParser = new LanguageTagParser();
		// see http://oss.software.ibm.com/cvs/icu/~checkout~/locale/docs/design/minimal_requirements.htm
		int[] failureCount = new int[1];
		int[] warningCount = new int[1];
		for (Iterator it = languageLocales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			if (locale.equals("root")) continue;
			//if (!locale.equals("zh_Hant")) continue;
			
			CLDRFile item = cldrFactory.make(locale, true);
			if (!testDraft && item.isDraft()) {
				logln(getLocaleAndName(locale) + "\tskipping draft");
				continue;
			}
			UnicodeSet exemplars = getFixedExemplarSet(locale, item);
			CLDRFile missing = CLDRFile.make(locale);
			failureCount[0] = 0;
			warningCount[0] = 0;
			localIDParser.set(locale);
			String language = localIDParser.getLanguage();
			logln("Testing: " + locale);
			// languages
			Set languages = new TreeSet(Utility.MINIMUM_LANGUAGES);
			languages.add(language);
			// LANGUAGE_NAME = 0, SCRIPT_NAME = 1, TERRITORY_NAME = 2, VARIANT_NAME = 3,
			// CURRENCY_NAME = 4, CURRENCY_SYMBOL = 5, TZID = 6
			
			checkForItems(item, languages, CLDRFile.LANGUAGE_NAME, missing, failureCount, null);
			
/*			checkTranslatedCode(cldrfile, codes, "currency", "/ldml/numbers/currencies/currency");
			checkTranslatedCode(cldrfile, codes, "tzid", "/ldml/dates/timeZoneNames/zone");
			checkTranslatedCode(cldrfile, codes, "variant", "/ldml/localeDisplayNames/variants/variant");
*/
			
			Set scripts = new TreeSet();
			scripts.add("Latn");
			Set others = (Set) language_scripts.get(language);
			if (others != null) scripts.addAll(others);
			checkForItems(item, scripts, CLDRFile.SCRIPT_NAME, missing, failureCount, null);

			Set countries = new TreeSet(Utility.MINIMUM_TERRITORIES);
			others = (Set) language_territories.get(language);
			if (others != null) countries.addAll(others);
			checkForItems(item, countries, CLDRFile.TERRITORY_NAME, missing, failureCount, null);
			
			Set currencies = new TreeSet();
			StandardCodes sc = StandardCodes.make();
			for (Iterator it2 = countries.iterator(); it2.hasNext(); ) {
				String country = (String) it2.next();
				Set countryCurrencies = sc.getMainCurrencies(country);
				if (countryCurrencies == null) {
					errln("Internal Error: no currencies for " + country + ", locale: " + locale);
				} else {
					currencies.addAll(countryCurrencies);
				}
			}
			checkForItems(item, currencies, CLDRFile.CURRENCY_NAME, missing, failureCount, null);
			checkForItems(item, currencies, CLDRFile.CURRENCY_SYMBOL, missing, failureCount, exemplars);
			
			//context=format and width=wide; context=stand-alone & width=abbreviated
			Set months = new TreeSet();
			for (int i = 1; i <= 12; ++i) months.add(i+"");
			Set days = new TreeSet(Arrays.asList(new String[] {"sun", "mon", "tue", "wed", "thu", "fri", "sat"}));
			for (int i = -6; i < 0; ++i) {
				checkForItems(item, (i < -3 ? months : days), i, missing, failureCount, null);
			}
			
			String filename = "missing_" + locale + ".xml";
			if (failureCount[0] > 0 || warningCount[0] > 0) {
				PrintWriter out = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY + "missing/", filename);
				missing.write(out);
				out.close();
				//String s = getIDAndLocalization(missing);
				if (failureCount[0] > 0) errln(getLocaleAndName(locale) + "\tmissing localizations, creating file");
				else logln(getLocaleAndName(locale) + "\tpossibly missing localizations, creating file");
			} else {
				new File(Utility.GEN_DIRECTORY + "missing/" + filename).delete();
			}
		}
	}
	
	private String getDateKey(String monthOrDay, String width, String code) {
		// String context = width.equals("narrow") ? "format" : "stand-alone";
		return "/ldml/dates/calendars/calendar[@type=\"gregorian\"]/"
				+ monthOrDay + "s/" + monthOrDay + "Context[@type=\"format\"]/"
				+ monthOrDay + "Width[@type=\"" + width + "\"]/" + monthOrDay
				+ "[@type=\"" + code + "\"]";
	}

	private String getDateKey(int type, String code) {
		return getDateKey(MONTHORDAYS[type / 3], WIDTHS[type % 3], code);
	}
	
	static final String[] WIDTHS = {"narrow", "wide", "abbreviated"};
	static final String[] MONTHORDAYS = {"day", "month"};

	/**
	 * @param item
	 * @param codes
	 * @param missing
	 * @param exemplarTest TODO
	 * @param fragment
	 *            TODO
	 */
	private void checkForItems(CLDRFile item, Set codes, int type, CLDRFile missing, int failureCount[], UnicodeSet exemplarTest) {
		// check codes
		for (Iterator it2 = codes.iterator(); it2.hasNext();) {
			String code = (String)it2.next();
			String key;
			if (type >= 0) {
				key = CLDRFile.getKey(type, code);
			} else {
				key = getDateKey(-type-1, code);
				System.out.println(key);
			}
			Value v = item.getValue(key);
			Value rootValue = resolvedRoot.getValue(key);
			if (v == null || v.hasSameValue(rootValue) && (exemplarTest == null || !exemplarTest.containsAll(rootValue.getStringValue()))) {
				Value englishValue = resolvedEnglish.getValue(key);
				String transValue;
				if (englishValue != null) {
					transValue = englishValue.getStringValue();
				} else {
					transValue = code;
				}
				missing.add(key, key, "TODO " + transValue);
				failureCount[0]++;
			} else {
				logln("\t" + code + "\t" + v.getStringValue());
			}
		}
	}
	/*
	void showTestStr() {
		LocaleIDParser lparser = new LocaleIDParser();
		Collection s = split(teststr,',', true, new ArrayList());
		for (Iterator it = s.iterator(); it.hasNext();) {
			String item = (String)it.next();
			lparser.set(item.replace('?', '_'));
			String region = lparser.getRegion();
			System.out.print(item.replace('?', '-') + " (" + getLocalization(region) + "), ");
			//System.out.print(getLocalization(region) + ", ");
		}
	}
	static String teststr = "en?AG, en?AI, en?AS, en?AU, en?IN, en?BB, en?BE, en?BM, en?BN, en?BS, en?BW, en?BZ, en?CA, en?CK, en?CM, en?DM, en?ER, en?ET, en?FJ, en?FK, en?FM, en?GB, en?GD, en?GH, en?GI, en?GM, en?GU, en?GY, en?HK, en?IE, en?IL, en?IO, en?JM, en?KE, en?KI, en?KN, en?KY, en?LC, en?LR, en?LS, en?MH, en?MP, en?MS, en?MT, en?MU, en?MW, en?NA, en?NF, en?NG, en?NR, en?NU, en?NZ, en?PG, en?PH, en?PK, en?PN, en?PR, en?PW, en?RW, en?SB, en?SC, en?SG, en?SH, en?SL, en?SO, en?SZ, en?TC, en?TK, en?TO, en?TT, en?UG, en?UM, en?US, en?VC, en?VG, en?VI, en?VU, en?WS, en?ZA, en?ZM, en?ZW";
	*/
	
	Utility.Transform EnglishName = new Utility.Transform() {
		public Object transform(Object source) {
			// TODO Auto-generated method stub
			return getLocalization(source.toString()) + " (" + source + ")";
		}		
	};
	
	Utility.Transform EnglishCurrencyName = new Utility.Transform() {
		public Object transform(Object source) {
			if (english == null) english = cldrFactory.make("en", true);
			return english.getName("currency", source.toString(), false) + " (" + source + ")";
		}		
	};
	
	public void testSupplemental() {
		Map language_scripts = new TreeMap();
		Map language_territories = new TreeMap();
		Map groups = new TreeMap();
		Map territory_currencies = new TreeMap();
		getSupplementalData(language_scripts, language_territories, groups, territory_currencies);
		Set sTerritories = new TreeSet();
		for (Iterator it = language_territories.values().iterator(); it.hasNext(); ){
			sTerritories.addAll((Collection)it.next());
		}
		StandardCodes sc = StandardCodes.make();
		Set temp = sc.getAvailableCodes("territory");
		
		Set allLanguages = new TreeSet(language_scripts.keySet());
		allLanguages.addAll(language_territories.keySet());
		for (Iterator it = allLanguages.iterator(); it.hasNext();) {
			Object language = it.next();
			Set scripts = (Set) language_scripts.get(language);
			Set territories = (Set) language_territories.get(language);
			logln(EnglishName.transform(language) 
					+ " scripts: " + EnglishName.transform(scripts)
					+ " territories: " + EnglishName.transform(territories));
		}
		
		// remove private use, deprecated, groups
		Set standardTerritories = new TreeSet();
		for (Iterator it = temp.iterator(); it.hasNext();) {
			String code = (String)it.next();
			List data = sc.getFullData("territory", code);
			if (data.get(0).equals("PRIVATE USE")) continue;
			if (!data.get(2).equals("")) continue;
			standardTerritories.add(code);
		}
		standardTerritories.removeAll(groups.keySet());
		
		if (!standardTerritories.containsAll(sTerritories)) {
			TreeSet extras = new TreeSet(sTerritories);
			extras.removeAll(standardTerritories);
			errln("Supplemental Territories contain illegal values: " + EnglishName.transform(extras));
		}
		if (!sTerritories.containsAll(standardTerritories)) {
			TreeSet extras = new TreeSet(standardTerritories);
			extras.removeAll(sTerritories);
			errln("Missing Territories: " + EnglishName.transform(extras));
		}
		
		// now test currencies
        logln("Check that no illegal territories are used");
		if (!standardTerritories.containsAll(territory_currencies.keySet())) {
			TreeSet extras = new TreeSet(territory_currencies.keySet());
			extras.removeAll(standardTerritories);
			errln("Currency info -- Illegal Territories: " + EnglishName.transform(extras));
		}
        logln("Check that no territories are missing");
		if (!territory_currencies.keySet().containsAll(standardTerritories)) {
			TreeSet extras = new TreeSet(standardTerritories);
			extras.removeAll(territory_currencies.keySet());
			errln("Currency info -- Missing Territories: " + EnglishName.transform(extras));
		}
		Set currencies = new TreeSet();
		for (Iterator it = territory_currencies.values().iterator(); it.hasNext(); ){
			currencies.addAll((Collection)it.next());
		}
        logln("Check that no illegal currencies are used");
        Set legalCurrencies = sc.getAvailableCodes("currency");
		if (!legalCurrencies.containsAll(currencies)) {
			TreeSet extras = new TreeSet(currencies);
			extras.removeAll(legalCurrencies);
			errln("Currency info -- Illegal Currencies: " + EnglishCurrencyName.transform(extras));
		}
        logln("Check that there are no missing currencies");
		if (!currencies.containsAll(legalCurrencies)) {
			TreeSet extras = new TreeSet(legalCurrencies);
			extras.removeAll(currencies);
			Map failures = new TreeMap();
			for (Iterator it = extras.iterator(); it.hasNext();) {
				String code = (String) it.next();
				List data = sc.getFullData("currency", code);
				String type = data.get(3) + "/" + data.get(1);
				Set s = (Set) failures.get(type);
				if (s == null) failures.put(type, s = new TreeSet());
				s.add(code);
			}
			for (Iterator it = failures.keySet().iterator(); it.hasNext();) {
				String type = (String) it.next();
				Set s = (Set) failures.get(type);
				errln("Currency info -- Missing Currencies: " + type + "\t => " + EnglishCurrencyName.transform(s));
			}
		}
        logln("Missing English currency names");
        for (Iterator it = legalCurrencies.iterator(); it.hasNext();) {
        	String currency = (String) it.next();
        	String name = english.getName("currency", currency, false);
        	if (name == null) {
        		String standardName = (String) sc.getFullData("currency", currency).get(0);
        		logln("\t\t\t<currency type=\"" + currency + "\">");
        		logln("\t\t\t\t<displayName>" + standardName + "</displayName>");
        		logln("\t\t\t</currency>");				
        	}
        }
		
	}
	
	public void testZones() {
		StandardCodes sc = StandardCodes.make();
		
		Map defaultNames = new TreeMap();
		Map old_new = sc.getZoneLinkold_new();
		Set core = sc.getZoneData().keySet();
		logln("Checking for collisions with last field");
		for (Iterator it = core.iterator(); it.hasNext(); ) {
			String currentItem = (String) it.next();
			String defaultName = TimezoneFormatter.getFallbackName(currentItem);
			String fullName = (String) defaultNames.get(defaultName);
			if (fullName == null) defaultNames.put(defaultName, currentItem);
			else {
				errln("Collision between: " + currentItem + " AND " + fullName);
			}
		}
		
		logln("Checking that all links are TO canonical zones");
		Set s = new TreeSet(old_new.values());
		s.removeAll(core);
		if (s.size() != 0) {
			errln("Links go TO zones that are not canonical! " + s);
		}
		
		logln("Checking that no links are FROM canonical zones");
		s = new TreeSet(core);
		s.retainAll(old_new.keySet());
		if (s.size() != 0) {
			errln("Links go FROM zones that are canonical! " + s);
		}
		
		logln("Checking that the zones with rule data are all canonical"); 
		Set zonesWithRules = sc.getZone_rules().keySet();
		s.clear();
		s.addAll(zonesWithRules);
		s.removeAll(core);
		if (s.size() != 0) errln("Zones with rules that are not canonical: " + s);
		
		logln("Checking that the rule data are all canonical"); 
		s.clear();
		s.addAll(core);
		s.removeAll(zonesWithRules);
		s.removeAll(old_new.keySet());
		if (s.size() != 0) errln("Canonical zones that don't have rules or links: " + s);
		
		for (Iterator it = old_new.keySet().iterator(); it.hasNext(); ) {
			String oldItem = (String) it.next();
			logln("old: " + oldItem + "\tnew: " + old_new.get(oldItem));
		}
		Map new_old = new TreeMap();
		for (Iterator it = core.iterator(); it.hasNext(); ) {
			new_old.put(it.next(), new TreeSet());
		}
		for (Iterator it = old_new.keySet().iterator(); it.hasNext(); ) {
			String oldItem = (String) it.next();
			String newItem = (String) old_new.get(oldItem);
			Set oldItems = (Set) new_old.get(newItem);
			if (oldItems == null) { // try recursing
				logln("!!!!Skipping " + oldItem + " => " + newItem);
				continue;
				// new_old.put(oldOne, oldItems = new TreeSet());
			} 
			oldItems.add(oldItem);
		}
		for (Iterator it = new_old.keySet().iterator(); it.hasNext(); ) {
			String newOne = (String) it.next();
			Set oldItems = (Set) new_old.get(newOne);
			logln(newOne + "\t" + oldItems);
		}
	}
}


/*    private static final int
HELP1 = 0,
HELP2 = 1,
SOURCEDIR = 2,
DESTDIR = 3,
MATCH = 4,
SKIP = 5,
TZADIR = 6,
NONVALIDATING = 7,
SHOW_DTD = 8,
TRANSLIT = 9;
options[SOURCEDIR].value

private static final UOption[] options = {
		UOption.HELP_H(),
		UOption.HELP_QUESTION_MARK(),
		UOption.SOURCEDIR().setDefault("C:\\ICU4C\\locale\\common\\main\\"),
		UOption.DESTDIR().setDefault("C:\\DATA\\GEN\\cldr\\mainCheck\\"),
		UOption.create("match", 'm', UOption.REQUIRES_ARG).setDefault(".*"),
		UOption.create("skip", 'z', UOption.REQUIRES_ARG).setDefault("zh_(C|S|HK|M).*"),
		UOption.create("tzadir", 't', UOption.REQUIRES_ARG).setDefault("C:\\ICU4J\\icu4j\\src\\com\\ibm\\icu\\dev\\tool\\cldr\\"),
		UOption.create("nonvalidating", 'n', UOption.NO_ARG),
		UOption.create("dtd", 'w', UOption.NO_ARG),
		UOption.create("transliterate", 'y', UOption.NO_ARG), };

private static String timeZoneAliasDir = null;
* /

public static void main(String[] args) throws SAXException, IOException {
	UOption.parseArgs(args, options);
	localeList = getMatchingXMLFiles(options[SOURCEDIR].value, options[MATCH].value);
	/*
    log = BagFormatter.openUTF8Writer(options[DESTDIR].value, "log.txt");
    try {
    	for (Iterator it = getMatchingXMLFiles(options[SOURCEDIR].value, options[MATCH].value).iterator(); it.hasNext();) {
    		String name = (String) it.next();
    		for (int i = 0; i <= 1; ++i) {
    			boolean resolved = i == 1;
        		CLDRKey key = make(name, resolved);
        		
        		PrintWriter pw = BagFormatter.openUTF8Writer(options[DESTDIR].value, name + (resolved ? "_r" : "") + ".txt");
				write(pw, key);
    	        pw.close();
    	        
    		}
    	}
    } finally {
    	log.close();
    	System.out.println("Done");
    }
    

			<language type="in">Indonesian</language>
			<language type="iw">Hebrew</language>
			<script type="Bali">Balinese</script>
			<script type="Batk">Batak</script>
			<script type="Blis">Blissymbols</script>
			<script type="Brah">Brahmi</script>
			<script type="Bugi">Buginese</script>
			<script type="Cham">Cham</script>
			<script type="Cirt">Cirth</script>
			<script type="Cyrs">Cyrillic (Old Church Slavonic variant)</script>
			<script type="Egyd">Egyptian demotic</script>
			<script type="Egyh">Egyptian hieratic</script>
			<script type="Egyp">Egyptian hieroglyphs</script>
			<script type="Glag">Glagolitic</script>
			<script type="Hmng">Pahawh Hmong</script>
			<script type="Hung">Old Hungarian</script>
			<script type="Inds">Indus (Harappan)</script>
			<script type="Java">Javanese</script>
			<script type="Kali">Kayah Li</script>
			<script type="Khar">Kharoshthi</script>
			<script type="Latf">Latin (Fraktur variant)</script>
			<script type="Latg">Latin (Gaelic variant)</script>
			<script type="Lepc">Lepcha (Rong)</script>
			<script type="Lina">Linear A</script>
			<script type="Mand">Mandaean</script>
			<script type="Maya">Mayan hieroglyphs</script>
			<script type="Mero">Meroitic</script>
			<script type="Orkh">Orkhon</script>
			<script type="Perm">Old Permic</script>
			<script type="Phag">Phags-pa</script>
			<script type="Phnx">Phoenician</script>
			<script type="Plrd">Pollard Phonetic</script>
			<script type="Roro">Rongorongo</script>
			<script type="Sara">Sarati</script>
			<script type="Sylo">Syloti Nagri</script>
			<script type="Syre">Syriac (Estrangelo variant)</script>
			<script type="Syrj">Syriac (Western variant)</script>
			<script type="Syrn">Syriac (Eastern variant)</script>
			<script type="Talu">Tai Lue</script>
			<script type="Teng">Tengwar</script>
			<script type="Tfng">Tifinagh (Berber)</script>
			<script type="Thai">Thai</script>
			<script type="Vaii">Vai</script>
			<script type="Visp">Visible Speech</script>
			<script type="Xpeo">Old Persian</script>
			<script type="Xsux">Cuneiform, Sumero-Akkadian</script>
			<script type="Zxxx">Code for unwritten languages</script>
			<script type="Zzzz">Code for uncoded script</script>
			<territory type="001">World</territory>
			<territory type="002">Africa</territory>
			<territory type="003">North America</territory>
			<territory type="005">South America</territory>
			<territory type="009">Oceania</territory>
			<territory type="011">Western Africa</territory>
			<territory type="013">Central America</territory>
			<territory type="014">Eastern Africa</territory>
			<territory type="015">Northern Africa</territory>
			<territory type="017">Middle Africa</territory>
			<territory type="018">Southern Africa</territory>
			<territory type="019">Americas</territory>
			<territory type="021">Northern America</territory>
			<territory type="029">Caribbean</territory>
			<territory type="030">Eastern Asia</territory>
			<territory type="035">South-eastern Asia</territory>
			<territory type="039">Southern Europe</territory>
			<territory type="053">Australia and New Zealand</territory>
			<territory type="054">Melanesia</territory>
			<territory type="057">Micronesia</territory>
			<territory type="061">Polynesia</territory>
			<territory type="062">South-central Asia</territory>
			<territory type="AX">Aland Islands</territory>
			<territory type="BQ">British Antarctic Territory</territory>
			<territory type="BU">Myanmar</territory>
			<territory type="CS">Czechoslovakia</territory>
			<territory type="CT">Canton and Enderbury Islands</territory>
			<territory type="DD">East Germany</territory>
			<territory type="DY">Benin</territory>
			<territory type="FQ">French Southern and Antarctic Territories</territory>
			<territory type="FX">Metropolitan France</territory>
			<territory type="HV">Burkina Faso</territory>
			<territory type="JT">Johnston Island</territory>
			<territory type="MI">Midway Islands</territory>
			<territory type="NH">Vanuatu</territory>
			<territory type="NQ">Dronning Maud Land</territory>
			<territory type="NT">Neutral Zone</territory>
			<territory type="PC">Pacific Islands Trust Territory</territory>
			<territory type="PU">U.S. Miscellaneous Pacific Islands</territory>
			<territory type="PZ">Panama Canal Zone</territory>
			<territory type="RH">Zimbabwe</territory>
			<territory type="SU">Union of Soviet Socialist Republics</territory>
			<territory type="TP">Timor-Leste</territory>
			<territory type="VD">North Vietnam</territory>
			<territory type="WK">Wake Island</territory>
			<territory type="YD">People's Democratic Republic of Yemen</territory>
			<territory type="ZR">Congo, The Democratic Republic of the</territory>
			<variant type="1901">Traditional German orthography</variant>
			<variant type="1996">German orthography of 1996</variant>
			<variant type="boont">Boontling</variant>
			<variant type="gaulish">Gaulish</variant>
			<variant type="guoyu">Mandarin or Standard Chinese</variant>
			<variant type="hakka">Hakka</variant>
			<variant type="lojban">Lojban</variant>
			<variant type="nedis">Natisone dialect</variant>
			<variant type="rozaj">Resian</variant>
			<variant type="scouse">Scouse</variant>
			<variant type="xiang">Xiang or Hunanese</variant>

			
			<currency type="CFP"><displayName>???</displayName></currency>
			<currency type="DDR"><displayName>???</displayName></currency>
			<currency type="EQE"><displayName>???</displayName></currency>
			<currency type="ESA"><displayName>???</displayName></currency>
			<currency type="ESB"><displayName>???</displayName></currency>
			<currency type="JAN"><displayName>???</displayName></currency>
			<currency type="LSM"><displayName>???</displayName></currency>
			<currency type="LUC"><displayName>???</displayName></currency>
			<currency type="LUL"><displayName>???</displayName></currency>
			<currency type="NAM"><displayName>???</displayName></currency>
			<currency type="NEW"><displayName>???</displayName></currency>
			<currency type="RHD"><displayName>???</displayName></currency>
			<currency type="SAN"><displayName>???</displayName></currency>
			<currency type="SDR"><displayName>???</displayName></currency>
			<currency type="SEE"><displayName>???</displayName></currency>
			<currency type="SRI"><displayName>???</displayName></currency>
			<currency type="UAE"><displayName>???</displayName></currency>
			<currency type="UDI"><displayName>???</displayName></currency>
			<currency type="UIC"><displayName>???</displayName></currency>
			<currency type="XAG"><displayName>???</displayName></currency>
			<currency type="XPD"><displayName>???</displayName></currency>
			<currency type="XPT"><displayName>???</displayName></currency>
			<currency type="XRE"><displayName>???</displayName></currency>
			<currency type="XTS"><displayName>???</displayName></currency>
			<currency type="XXX"><displayName>???</displayName></currency>


    */
