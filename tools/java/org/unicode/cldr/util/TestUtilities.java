/*
**********************************************************************
* Copyright (c) 2002-2004, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: Mark Davis
**********************************************************************
*/

package org.unicode.cldr.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile.Factory;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.util.ULocale;

/**
 * @author davis
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class TestUtilities {
	public static void main(String[] args) throws Exception {
		checkLanguages();
		//printCountries();
		//printZoneSamples();
		//printCurrencies();
		System.out.println("Done");
	}

	private static void checkLanguages() {
		// TODO Auto-generated method stub

		Factory mainCldrFactory = Factory.make(Utility.COMMON_DIRECTORY + "main" + File.separator, ".*");
		Set availableLocales = mainCldrFactory.getAvailable();
		Set available = new TreeSet();
		LocaleIDParser lip = new LocaleIDParser();
		for (Iterator it = availableLocales.iterator(); it.hasNext();) {
			available.add(lip.set((String)it.next()).getLanguage());
		}
		Set langHack = new TreeSet();
		for (int i = 0; i < language_territory_hack.length; ++i) {
			String lang = language_territory_hack[i][0];
			langHack.add(lang);
		}
		if (langHack.containsAll(available)) System.out.println("All ok");
		else {
			available.removeAll(langHack);
			for (Iterator it = available.iterator(); it.hasNext();) {
				String item = (String) it.next();
				System.out.println("{\"" + item + "\", \"XXX\"},/t//"
						+ ULocale.getDisplayLanguage(item, ULocale.ENGLISH));
			}
		}
	}

	/**
	 * @throws IOException
	 * 
	 */
	private static void printCountries() throws IOException {
        Factory mainCldrFactory = Factory.make(Utility.COMMON_DIRECTORY + "main" + File.separator, ".*");
        CLDRFile english = mainCldrFactory.make("en", true);
        PrintWriter out = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY, "country_language_names.txt");
        StandardCodes sc = StandardCodes.make();
        for(Iterator it = sc.getGoodAvailableCodes("language").iterator(); it.hasNext();) {
            String code = (String)it.next();
            out.println(code + "\t" + english.getName(CLDRFile.LANGUAGE_NAME, code, false));
        }
        out.println("****");
        for(Iterator it = sc.getGoodAvailableCodes("territory").iterator(); it.hasNext();) {
            String code = (String)it.next();
            out.println(code + "\t" + english.getName(CLDRFile.TERRITORY_NAME, code, false));
        }
        out.println("****");
        for(Iterator it = sc.getGoodAvailableCodes("script").iterator(); it.hasNext();) {
            String code = (String)it.next();
            out.println(code + "\t" + english.getName(CLDRFile.SCRIPT_NAME, code, false));
        }
		out.close();
	}

	/**
	 * 
	 */
	private static void printCurrencies() {
		StandardCodes sc = StandardCodes.make();
		Set s = sc.getAvailableCodes("currency");
		for (Iterator it = s.iterator(); it.hasNext();) {
			String code = (String)it.next();
			String name = sc.getData("currency", code);
			List data = sc.getFullData("currency", code);
			System.out.println(code + "\t" + name + "\t" + data);
		}
	}

	/**
	 * @throws IOException
	 * @throws ParseException
	 * 
	 */
	private static void printZoneSamples() throws Exception {
		String[] locales = {
				"en",
                "en_GB",
				"de",
				"zh",
				"hi",
				"bg",
				"ru",
				"ja",
				"as" // picked deliberately because it has few itesm
		};
		String[] zones = {
				"America/Los_Angeles",
				"America/Argentina/Buenos_Aires",
				"America/Buenos_Aires",
				"America/Havana",
				"Australia/ACT",
				"Australia/Sydney",
				"Europe/London",
				"Europe/Moscow",
				"Etc/GMT+3"
		};
		String[][] fields = {
				{"2004-01-15T00:00:00Z", "Z", "ZZZZ", "z", "zzzz"},
				{"2004-07-15T00:00:00Z", "Z", "ZZZZ", "z", "zzzz", "v", "vvvv"}
		};
    	Factory mainCldrFactory = Factory.make(Utility.COMMON_DIRECTORY + "main" + File.separator, ".*");
    	PrintWriter out = BagFormatter.openUTF8Writer(Utility.GEN_DIRECTORY, "timezone_samples.txt");
    	long[] offsetMillis = new long[1];
    	
		for (int i = 0; i < locales.length; ++i) {
			String locale = locales[i];
			TimezoneFormatter tzf = new TimezoneFormatter(mainCldrFactory, locale, false).setSkipDraft(true);
			for (int j = 0; j < zones.length; ++j) {
				String zone = zones[j];
				for (int k = 0; k < fields.length; ++k) {
					String type = fields[k][0];
					Date datetime = ICUServiceBuilder.isoDateParse(type);
					for (int m = 1; m < fields[k].length; ++m) {
						String field = fields[k][m];
						String formatted = tzf.getFormattedZone(zone, field, datetime.getTime());
						String parsed = tzf.parse(formatted,offsetMillis);
						if (parsed == null) parsed = "FAILED PARSE";
						else if (parsed.length() == 0) parsed = format(offsetMillis[0]);
						out.println("{\"" + locale
							+ "\",\t\"" + zone
							+ "\",\t\"" + type
							+ "\",\t\"" + field
							+ "\",\t\"" + formatted
							+ "\",\t\"" + parsed
							+ "\"},"
						);
					}
				}
				out.println();
			}
			out.println("==========");
			out.println();
		}
		out.close();
	}

	/**
	 * quick & dirty format
	 */
	private static String format(long offsetMillis) {
		offsetMillis /= 60*1000;
		String sign = "+";
		if (offsetMillis < 0) {
			offsetMillis = -offsetMillis;
			sign = "-";
		}
		return sign + String.valueOf(offsetMillis/60)
		+ ":" + String.valueOf(100 + (offsetMillis%60)).substring(1,3);
	}
	
	private static final String[][] language_territory_hack = {
		{"af", "ZA"},
		{"am", "ET"},
		{"ar", "SA"},
		{"as", "IN"},
		{"ay", "PE"},
		{"az", "AZ"},
		{"bal", "PK"},
		{"be", "BY"},
		{"bg", "BG"},
		{"bn", "IN"},
		{"bs", "BA"},
		{"ca", "ES"},
		{"ch", "MP"},
		{"cpe", "SL"},
		{"cs", "CZ"},
		{"cy", "GB"},
		{"da", "DK"},
		{"de", "DE"},
		{"dv", "MV"},
		{"dz", "BT"},
		{"el", "GR"},
		{"en", "US"},
		{"es", "ES"},
		{"et", "EE"},
		{"eu", "ES"},
		{"fa", "IR"},
		{"fi", "FI"},
		{"fil", "PH"},
		{"fj", "FJ"},
		{"fo", "FO"},
		{"fr", "FR"},
		{"ga", "IE"},
		{"gd", "GB"},
		{"gl", "ES"},
		{"gn", "PY"},
		{"gu", "IN"},
		{"gv", "GB"},
		{"ha", "NG"},
		{"he", "IL"},
		{"hi", "IN"},
		{"ho", "PG"},
		{"hr", "HR"},
		{"ht", "HT"},
		{"hu", "HU"},
		{"hy", "AM"},
		{"id", "ID"},
		{"is", "IS"},
		{"it", "IT"},
		{"ja", "JP"},
		{"ka", "GE"},
		{"kk", "KZ"},
		{"kl", "GL"},
		{"km", "KH"},
		{"kn", "IN"},
		{"ko", "KR"},
		{"kok", "IN"},
		{"ks", "IN"},
		{"ku", "TR"},
		{"ky", "KG"},
		{"la", "VA"},
		{"lb", "LU"},
		{"ln", "CG"},
		{"lo", "LA"},
		{"lt", "LT"},
		{"lv", "LV"},
		{"mai", "IN"},
		{"men", "GN"},
		{"mg", "MG"},
		{"mh", "MH"},
		{"mk", "MK"},
		{"ml", "IN"},
		{"mn", "MN"},
		{"mni", "IN"},
		{"mo", "MD"},
		{"mr", "IN"},
		{"ms", "MY"},
		{"mt", "MT"},
		{"my", "MM"},
		{"na", "NR"},
		{"nb", "NO"},
		{"nd", "ZA"},
		{"ne", "NP"},
		{"niu", "NU"},
		{"nl", "NL"},
		{"nn", "NO"},
		{"no", "NO"},
		{"nr", "ZA"},
		{"nso", "ZA"},
		{"ny", "MW"},
		{"om", "KE"},
		{"or", "IN"},
		{"pa", "IN"},
		{"pau", "PW"},
		{"pl", "PL"},
		{"ps", "PK"},
		{"pt", "BR"},
		{"qu", "PE"},
		{"rn", "BI"},
		{"ro", "RO"},
		{"ru", "RU"},
		{"rw", "RW"},
		{"sd", "IN"},
		{"sg", "CF"},
		{"si", "LK"},
		{"sk", "SK"},
		{"sl", "SI"},
		{"sm", "WS"},
		{"so", "DJ"},
		{"sq", "CS"},
		{"sr", "CS"},
		{"ss", "ZA"},
		{"st", "ZA"},
		{"sv", "SE"},
		{"sw", "KE"},
		{"ta", "IN"},
		{"te", "IN"},
		{"tem", "SL"},
		{"tet", "TL"},
		{"th", "TH"},
		{"ti", "ET"},
		{"tg", "TJ"},
		{"tk", "TM"},
		{"tkl", "TK"},
		{"tvl", "TV"},
		{"tl", "PH"},
		{"tn", "ZA"},
		{"to", "TO"},
		{"tpi", "PG"},
		{"tr", "TR"},
		{"ts", "ZA"},
		{"uk", "UA"},
		{"ur", "IN"},
		{"uz", "UZ"},
		{"ve", "ZA"},
		{"vi", "VN"},
		{"wo", "SN"},
		{"xh", "ZA"},
		{"zh", "CN"},
		{"zh_Hant", "TW"},
		{"zu", "ZA"},
		{"aa", "ET"},
		{"byn", "ER"},
		{"eo", "DE"},
		{"gez", "ET"},
		{"haw", "US"},
		{"iu", "CA"},
		{"kw", "GB"},
		{"sa", "IN"},
		{"sh", "HR"},
		{"sid", "ET"},
		{"syr", "SY"},
		{"tig", "ER"},
		{"tt", "RU"},
		{"wal", "ET"},
		};

}
