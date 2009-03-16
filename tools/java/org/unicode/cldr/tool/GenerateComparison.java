package org.unicode.cldr.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.PrettyPath;
import org.unicode.cldr.util.Row;
import org.unicode.cldr.util.Timer;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.Row.R2;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.dev.test.util.CollectionUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UTF16;

public class GenerateComparison {

  private static PrettyPath prettyPathMaker;

  private static Collator collator = Collator.getInstance();

  static class EnglishRowComparator implements Comparator<R2<String,String>> {
    private static Comparator<String> unicode = new UTF16.StringComparator(true, false, 0);
    public int compare(R2<String, String> arg0, R2<String, String> arg1) {
      int result = collator.compare(arg0.get0(), arg1.get0());
      if (result != 0) return result;
      result = unicode.compare(arg0.get0(), arg1.get0());
      if (result != 0) return result;
      result = collator.compare(arg0.get1(), arg1.get1());
      if (result != 0) return result;
      result = unicode.compare(arg0.get1(), arg1.get1());
      return result;
    }
  }

  static EnglishRowComparator ENG = new EnglishRowComparator();

  public static void main(String[] args) throws IOException {

    // Setup
    Timer timer = new Timer();
    Timer totalTimer = new Timer();
    long totalPaths = 0;
    NumberFormat format = NumberFormat.getNumberInstance();
    format.setGroupingUsed(true);

    Counter<String> totalCounter = new Counter<String>();

    // Get the args

    String oldDirectory = Utility.getProperty("oldDirectory", new File(Utility.BASE_DIRECTORY, "../../common-cldr1.6/main/").getCanonicalPath() + "/");
    String newDirectory = Utility.getProperty("newDirectory", new File(Utility.BASE_DIRECTORY, "incoming/proposed/main/").getCanonicalPath() + "/");
    String changesDirectory = Utility.getProperty("changesDirectory", new File(Utility.CHART_DIRECTORY + "/changes/").getCanonicalPath() + "/");

    String filter = Utility.getProperty("localeFilter", ".*");
    boolean SHOW_ALIASED = Utility.getProperty("showAliased", "false").toLowerCase().startsWith("t");

    // Create the factories

    Factory oldFactory = Factory.make(oldDirectory, filter);
    Factory newFactory = Factory.make(newDirectory, filter);
    CLDRFile english = newFactory.make("en", true);
    CLDRFile oldRoot = oldFactory.make("root", true);
    CLDRFile newRoot = newFactory.make("root", true);

    // Get the union of all the language locales, sorted by English name

    Set<String> oldList = oldFactory.getAvailableLanguages();
    Set<String> newList = newFactory.getAvailableLanguages();
    Set<String> unifiedList = new HashSet<String>(oldList);
    unifiedList.addAll(newList);
    Set<R2<String, String>> pairs = new TreeSet<R2<String, String>>();
    for (String code : unifiedList) {
      pairs.add(Row.make(english.getName(code), code));
    }

    prettyPathMaker = new PrettyPath();
    int totalDifferences = 0;
    int differences = 0;

    Set<R2<String,String>> indexInfo = new TreeSet<R2<String,String>>(ENG);

    // iterate through those
    for (R2<String, String> pair : pairs) {
      timer.start();
      final String locale = pair.get1();
      final String localeName = pair.get0();
      System.out.println(locale);
      differences = 0;
      System.out.println();

      // Create CLDR files for both; null if can't open

      CLDRFile oldFile = null;
      if (oldList.contains(locale)) {
        try {
          oldFile = oldFactory.make(locale, true, true);
        } catch (Exception e) {
          addToIndex(indexInfo, "ERROR1.6", locale, localeName);
          continue;
        }
      }
      CLDRFile newFile = null;
      if (newList.contains(locale)) {
        try {
          newFile = newFactory.make(locale, true, true);
        } catch (Exception e) {
          addToIndex(indexInfo, "ERROR1.7", locale, localeName);
          continue;
        }
      }

      // Check for null cases

      if (oldFile == null) {
        addToIndex(indexInfo, "NEW", locale, localeName);
        continue;
      } else if (newFile == null) {
        addToIndex(indexInfo, "DELETED", locale, localeName);
        continue;
      }
      System.out.println("*** " + localeName + "\t" + locale);
      System.out.println();

      // Get the union of all the paths

      Set<String> paths;
      try {
        paths = new HashSet<String>();
        CollectionUtilities.addAll(oldFile.iterator(), paths);
        paths.addAll(oldFile.getExtraPaths());
        CollectionUtilities.addAll(newFile.iterator(), paths);
        paths.addAll(newFile.getExtraPaths());
      } catch (Exception e) {
        addToIndex(indexInfo, "ERROR", locale, localeName);
        continue;
      }

      // We now have the full set of all the paths for old and new files
      // TODO Sort by the pretty form
      // Set<R2<String,String>> pathPairs = new TreeSet();
      // for (String code : unifiedList) {
      // pairs.add(Row.make(code, english.getName(code)));
      // }

      // Initialize sets
      //       .addColumn("Code", "class='source'", "<a name=\"{0}\" href='likely_subtags.html#und_{0}'>{0}</a>", "class='source'", true)

      final String localeDisplayName = english.getName(locale);
      TablePrinter table = new TablePrinter()
      .setCaption("Changes in " + localeDisplayName + " (" + locale + ")")
      .addColumn("PRETTY_SORT1").setSortPriority(1).setHidden(true).setRepeatHeader(true)
      .addColumn("PRETTY_SORT2").setSortPriority(2).setHidden(true)
      .addColumn("PRETTY_SORT3").setSortPriority(3).setHidden(true)
      .addColumn("ESCAPED_PATH").setHidden(true)
      .addColumn("Inh.").setCellAttributes("class=\"{0}\"").setSortPriority(0).setSpanRows(true).setRepeatHeader(true)
      .addColumn("Section").setSpanRows(true).setCellAttributes("class='section'")
      .addColumn("Subsection").setSpanRows(true).setCellAttributes("class='subsection'")
      .addColumn("Item").setSpanRows(true).setCellPattern("<a href=\"{4}\">{0}</a>").setCellAttributes("class='item'")
      .addColumn("English").setCellAttributes("class='english'")
      .addColumn("Status").setSortPriority(4).setCellAttributes("class=\"{0}\"")
      .addColumn("Old" + localeDisplayName).setCellAttributes("class='old'")
      .addColumn("New" + localeDisplayName).setCellAttributes("class='new'")
      ;
      Counter<String> fileCounter = new Counter<String>();

      for (String path : paths) {
        if (path.contains("/alias")) {
          continue;
        }
        String cleanedPath = CLDRFile.getNondraftNonaltXPath(path);

        String oldValue = oldFile.getStringValue(cleanedPath);
        String newValue = newFile.getStringValue(path);
        String englishValue = english.getStringValue(cleanedPath);

        // for debugging
        if (oldValue != null && oldValue.contains("{1} {0}")) {
          System.out.print("");
        }

        if (equals(newValue, oldValue)) {
          continue;
        }

        // get the actual place the data is stored
        // AND adjust if the same as root!

        Status oldStatus = new Status();
        String oldFoundLocale = getStatus(oldFile, oldRoot, cleanedPath, oldValue, oldStatus);

        Status newStatus = new Status();
        String newFoundLocale = getStatus(newFile, newRoot, path, newValue, newStatus);

        // At this point, we have two unequal values
        // TODO check for non-distinguishing attribute value differences

        boolean isAliased = false;
        if (!SHOW_ALIASED) {

          // Skip deletions of alt-proposed

//          if (newValue == null) {
//            if (path.contains("@alt=\"proposed")) {
//              continue;
//            }
//          }

          // Skip if both inherited from the same locale, since we should catch it
          // in that locale.

          // Mark as aliased if new locale or path is different
          if (!newStatus.pathWhereFound.equals(path)) {
            isAliased=true;
            //continue;
          }         
          
          if (!newFoundLocale.equals(locale)) {
            isAliased=true;
            //continue;
          }
          
//          // skip if old locale or path is aliased
//          if (!oldFoundLocale.equals(locale)) {
//            //isAliased=true;
//            continue;
//          }
//          
//          // Skip if either found path is are different
//          if (!oldStatus.pathWhereFound.equals(cleanedPath)) {
//            //isAliased=true;
//            continue;
//          }

          // Now check other aliases

//          final boolean newIsAlias = !newStatus.pathWhereFound.equals(path);
//          if (newIsAlias) { // new is alias
//            // filter out cases of a new string that is found via alias
//            if (oldValue == null) {
//              continue;
//            }
//
//          }
        }

        // We definitely have a difference worth recording, so do so

        String newFullPath = newFile.getFullXPath(path);
        final boolean reject = newFullPath != null && newFullPath.contains("@draft") && !newFullPath.contains("@draft=\"contributed\"");
        String status;
        if (reject) {
          status = "NOT-ACC";
        } else if (newValue == null) {
          status = "deleted";
        } else if (oldValue == null) {
          status = "added";
        } else {
          status = "changed";
        }
        String coreStatus = status;
        if (isAliased) {
          status = "I+" + status;
        }
        fileCounter.increment(status);
        totalCounter.increment(status);

        String pretty_sort = prettyPathMaker.getPrettyPath(cleanedPath);
        String[] prettyPartsSort = pretty_sort.split("[|]");
        if (prettyPartsSort.length != 3) {
          System.out.println("Bad pretty path: " + pretty_sort + ", original: " + cleanedPath);
        }
        String prettySort1 = prettyPartsSort[0];
        String prettySort2 = prettyPartsSort[1];
        String prettySort3 = prettyPartsSort[2];
        
        String pretty = prettyPathMaker.getOutputForm(pretty_sort);
        String escapedPath = "http://unicode.org/cldr/apps/survey?_=" + locale + "&xpath=" + URLEscaper(cleanedPath);
        String[] prettyParts = pretty.split("[|]");
        if (prettyParts.length != 3) {
          System.out.println("Bad pretty path: " + pretty + ", original: " + cleanedPath);
        }
        String pretty1 = prettyParts[0];
        String pretty2 = prettyParts[1];
        String pretty3 = prettyParts[2];
          
          // http://kwanyin.unicode.org/cldr-apps/survey?_=kw_GB&xpath=%2F%2Fldml%2FlocaleDisplayNames%2Flanguages%2Flanguage%5B%40type%3D%22mt%22%5D

        table.addRow()
        .addCell(prettySort1)
        .addCell(prettySort2)
        .addCell(prettySort3)
        .addCell(escapedPath)
        .addCell(isAliased ? "I" : "")
        .addCell(pretty1)
        .addCell(pretty2)
        .addCell(pretty3)
        .addCell(englishValue == null ? "-" : englishValue)
        .addCell(coreStatus)
        .addCell(oldValue == null ? "-" : oldValue)
        .addCell(newValue == null ? "-" : newValue)
        .finishRow();

        totalDifferences++;
        differences++;
      }

      addToIndex(indexInfo, "", locale, localeName);
      PrintWriter out = BagFormatter.openUTF8Writer(changesDirectory, locale + ".html");
      String title = "Changes in " + localeDisplayName ;
      out.println("<html>" +
              "<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" + Utility.LINE_SEPARATOR +
              "<title>" + title + "</title>" + Utility.LINE_SEPARATOR +
              "<link rel='stylesheet' href='index.css' type='text/css'>" + Utility.LINE_SEPARATOR +
              //"<base target='_blank'>" + Utility.LINE_SEPARATOR +
              "</head><body>" + Utility.LINE_SEPARATOR +
              "<h1>" + title + "</h1>" + Utility.LINE_SEPARATOR
              + "<a href='index.html'>Index</a>"
              + "<p><b>Warning: this is still being developed, and might have problems.</b> " +
              		"In particular, until the voting code has been changed to match the new rules, " +
              		"the status will not be correct. Please send feedback to cldr-users@unicode.org as per the email.</p>" +
              		"<p>The Inh. column indicates when a value is inherited or aliased.</p>"
              );

      TablePrinter table2 = new TablePrinter()
      .setCaption("Totals")
      .addColumn("Inh.").setSortPriority(0)
      .addColumn("Status").setSortPriority(1)
      .addColumn("Total")
      ;

      for (String key : fileCounter.getKeysetSortedByKey()) {
        boolean inherited = key.startsWith("I+");
        table2.addRow()
        .addCell(inherited ? "I" : "")
        .addCell(inherited ? key.substring(2) : key)
        .addCell(fileCounter.getCount(key))
        .finishRow();
      }
      out.println(table2);
      out.println("<br>");
      out.println(table);

      // show status on console

      System.out.println(locale + "\tDifferences:\t" + format.format(differences)
              + "\tPaths:\t" + format.format(paths.size())
              + "\tTime:\t" + timer.getDuration() + "ms");
      totalPaths += paths.size();
      out.println("</body></html>");
      out.close();
    }
    PrintWriter indexFile = BagFormatter.openUTF8Writer(changesDirectory, "index.html");
    indexFile.println("<html>" +
            "<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" + Utility.LINE_SEPARATOR +
            "<title>" + "Change Summary" + "</title>" + Utility.LINE_SEPARATOR +
            "<link rel='stylesheet' href='index.css' type='text/css'>" + Utility.LINE_SEPARATOR +
            "<base target='_blank'>" + Utility.LINE_SEPARATOR +
            "</head><body>" + Utility.LINE_SEPARATOR +
            "<h1>" + "Change Summary" + "</h1>" + Utility.LINE_SEPARATOR
            + "<p>");

    String separator = "";
    int last = 0;
    for (R2<String,String> indexPair : indexInfo) {
      int firstChar = indexPair.get0().codePointAt(0);
      indexFile.append(firstChar != last ? "</p><p>" : separator).append(indexPair.get1());
      separator = " | ";
      last = indexPair.get0().codePointAt(0);
    }
    indexFile.println("</p></body></html>");
    indexFile.close();

    System.out.println();


    for (String key : totalCounter.getKeysetSortedByKey()) {
      System.out.println(key + "\t" + totalCounter.getCount(key));
    }

    System.out.println("Total Differences:\t" + format.format(totalDifferences) 
            + "\tPaths:\t" + format.format(totalPaths)
            + "\tTotal Time:\t" + format.format(totalTimer.getDuration()) + "ms");
  }
  
  static Transliterator urlHex = Transliterator.createFromRules("foo", 
          "([a-zA-Z0-9]) > $1 ;" +
          "(.) > &hex($1) ;" +
          ":: null;" +
          "'\\u00' > '%' ;"
          , Transliterator.FORWARD);

  private static String URLEscaper(String path) {
    return urlHex.transform(path);
  }

  private static void addToIndex(Set<R2<String,String>> indexInfo, String title, final String locale,
          final String localeName) {
    if (title.startsWith("ERROR")) {
      indexInfo.add(R2.make(localeName, title + " " + localeName + " (" + locale + ")"));
      return;
    }
    indexInfo.add(R2.make(localeName, "<a href='" + locale + ".html'>" + title + " " + localeName + " (" + locale + ")</a>"));
  }

  //  private static int accumulate(Set<R2<String,String>> rejected, int totalRejected,
  //          final String locale, String indicator, String oldValue, String newValue, String path) {
  //    String pretty = prettyPathMaker.getPrettyPath(path, false);
  //    String line = locale + "\t" + indicator +"\t\u200E[" + oldValue + "]\u200E\t\u200E[" + newValue + "]\u200E\t" + pretty;
  //    String pretty2 = prettyPathMaker.getOutputForm(pretty);
  //    rejected.add(Row.make(pretty2, line));
  //    totalRejected++;
  //    return totalRejected;
  //  }

  private static String getStatus(CLDRFile oldFile, CLDRFile oldRoot, String path,
          String oldString, Status oldStatus) {
    String oldLocale = oldFile.getSourceLocaleID(path, oldStatus);
    if (!oldLocale.equals("root")) {
      String oldRootValue = oldRoot.getStringValue(oldStatus.pathWhereFound);
      if (equals(oldString, oldRootValue)) {
        oldLocale = "root";
      }
    }
    return oldLocale;
  }

  private static void showSet(PrintWriter out, Set<R2<String,String>> rejected, final String locale, String title) {
    if (rejected.size() != 0) {
      out.println();
      out.println(locale + "\t" + title + "\t" + rejected.size());
      for (R2<String,String> prettyAndline : rejected) {
        out.println(prettyAndline.get1());
      }
    }
  }

  private static boolean equals(String newString, String oldString) {
    if (newString == null) {
      return oldString == null;
    }
    return newString.equals(oldString);
  }
}
