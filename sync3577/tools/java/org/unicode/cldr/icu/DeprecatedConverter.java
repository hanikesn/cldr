// Copyright 2009 Google Inc. All Rights Reserved.

package org.unicode.cldr.icu;

import static org.unicode.cldr.icu.ICUID.ICU_BOUNDARIES;
import static org.unicode.cldr.icu.ICUID.ICU_BRKITR_DATA;
import static org.unicode.cldr.icu.ICUID.ICU_DEPENDENCY;
import static org.unicode.cldr.icu.ICUID.ICU_DICTIONARIES;
import static org.unicode.cldr.icu.ICUID.ICU_DICTIONARY;
import static org.unicode.cldr.icu.ICUID.ICU_GRAPHEME;
import static org.unicode.cldr.icu.ICUID.ICU_LINE;
import static org.unicode.cldr.icu.ICUID.ICU_SENTENCE;
import static org.unicode.cldr.icu.ICUID.ICU_TITLE;
import static org.unicode.cldr.icu.ICUID.ICU_WORD;
import static org.unicode.cldr.icu.ICUID.ICU_XGC;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.ant.CLDRConverterTool.Alias;
import org.unicode.cldr.ant.CLDRConverterTool.AliasDeprecates;
import org.unicode.cldr.icu.ICULog.Emitter;
import org.unicode.cldr.icu.ICULog.Level;
import org.unicode.cldr.icu.ICUResourceWriter.Resource;
import org.unicode.cldr.icu.ICUResourceWriter.ResourceString;
import org.unicode.cldr.icu.ICUResourceWriter.ResourceTable;
import org.unicode.cldr.icu.LDML2ICUConverter.LDMLServices;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.ibm.icu.util.ULocale;

public class DeprecatedConverter {
  private final ICULog log;
  private final LDMLServices services;
  private final File depDir;
  private final File dstDir;

  static final String SOURCE_INFO = "icu-config.xml & build.xml";

  public DeprecatedConverter(ICULog log, LDMLServices services, File depDir, File dstDir) {
    this.log = log;
    this.services = services;
    this.depDir = depDir;
    this.dstDir = dstDir;
  }

  static class MakefileInfo {
    // Ordinary XML source files
    // ex: "mt.xml"
    Set<String> fromFiles = new TreeSet<String>();

    // Empty files generated by validSubLocales
    // ex: "en_US.xml"
    Set<String> emptyFromFiles = new TreeSet<String>();

    // Files that actually exist in LDML and contain aliases
    // ex: zh_MO.xml
    Set<String> aliasFromFiles = new TreeSet<String>();

    // Files generated directly from the alias list (no XML actually exists).
    // ex: th_TH_TRADITIONAL.xml -> File
    Set<String> generatedAliasFiles = new TreeSet<String>();

    List<String> ctdFiles = new ArrayList<String>();

    List<String> brkFiles = new ArrayList<String>();
  }

  static class PostProcessResult {
    List<Resource> generatedResources;
    MakefileInfo generatedMakeInfo;
  }

  public void write(ICUWriter writer, AliasDeprecates alias, boolean parseDraft,
      boolean parseSubLocale, Set<String> validLocales) {

    MakefileInfo mfi = new MakefileInfo();
    List<Resource> generated = new ArrayList<Resource>();

    log.setStatus(null);
    log.log("Postprocess start");
    // en -> "en_US en_GB ..."
    Map<String, String> validSubMap = new TreeMap<String, String>();

    // for in -> id where id is a synthetic alias
    Map<String, String> maybeValidAlias = new TreeMap<String, String>();

    // (1) Collect .xml files.
    // These are .xml files in depDir that are not supplementalData.xml and that have
    // counterparts in destDir.
    File[] depXmlFiles = getDepXmlFiles();

    // (2) Iterate through the files.
    // Information is collected for each file.
    updateSubmapAndFromFiles(depXmlFiles, parseDraft, parseSubLocale, validSubMap, mfi.fromFiles);

    if (alias != null) {
      // (3) Generate empty locale .xml files.
      // - Write a .txt alias file for each
      // - Record the file in generatedAliasFiles
      generateEmptyLocales(alias.emptyLocaleList, generated, mfi.generatedAliasFiles);

      // (4) Generate alias files
      generateAliases(alias.aliasList, parseSubLocale, maybeValidAlias, generated, mfi.fromFiles,
          mfi.generatedAliasFiles);

      // (5) Move aliased locales from fromFiles to aliasFromfiles
      updateAliasFromFiles(alias.aliasLocaleList, mfi.fromFiles, mfi.aliasFromFiles);
    }

    // (6) Calculate 'valid sub locales'
    // These are empty locales generated due to a validSubLocales attribute.
    processValidSubmap(validSubMap, maybeValidAlias, generated, mfi.fromFiles, mfi.emptyFromFiles,
        mfi.generatedAliasFiles,validLocales);

    // (7) Warn about any files still in maybeValidAlias
    warnUnusedAliases(maybeValidAlias);

    // (8) get brkIterator brk and compact trie files
    getBrkCtdFiles(mfi.brkFiles, mfi.ctdFiles);

    // (9) Write the generated resources
    writeResources(writer, generated);

    // (10) Finally, write the makefile
    writeMakefile(mfi);

    log.setStatus(null);
    log.log("Postprocess done.");
  }

  private File[] getDepXmlFiles() {
    // Collect the set of file names in dstDir, replacing existing suffix with .xml.
    final Set<String> dstNameSet = new HashSet<String>();
    for (String fileName : dstDir.list()) {
      dstNameSet.add(fileName.substring(0, fileName.lastIndexOf('.')) + ".xml");
    }

    // Collect the list of xml files in depDir that have matching names.
    File depXmlFiles[] = depDir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        String n = f.getName();
        return !f.isDirectory()
          && n.endsWith(".xml")
          && !n.startsWith("supplementalData") // not a locale
          && dstNameSet.contains(n); // root is implied, will be included elsewhere.
      }
    });

    return depXmlFiles;
  }

  private void updateSubmapAndFromFiles(File[] depXmlFiles, boolean parseDraft,
      boolean parseSubLocale, Map<String, String> validSubMap, Set<String> fromFiles) {

    int numXmlFiles = depXmlFiles.length;
    boolean doParse = parseDraft || parseSubLocale;
    if (doParse) {
      log.log("Parsing " + numXmlFiles + " LDML locale files to check "
          + (parseDraft ? "draft, " : "")
          + (parseSubLocale ? "valid-sub-locales" : ""));
    }
    Emitter logEmit = log.emitter(Level.LOG);

    // Use index so we can count files while tracing output
    for (int i = 0; i < numXmlFiles; i++) {
      File depFile = depXmlFiles[i];
      String depFilePath = depFile.toString();
      String depFileName = depFile.getName();
      String localeName = depFileName.substring(0, depFileName.lastIndexOf('.'));

      log.setStatus(localeName);

      boolean isRegular = true;

      // (A) Update validSubMap and set isRegular
      // If parseDraft or parseSublocale is true, parse the file.
      // - isRegular will be false if parseDraft is true and the file is draft.
      // - validSubMap will add an entry if isRegular is true, parseSubLocale is true,
      //   collations is not null, and the validSubLocales entry on the collations is
      //   not null or empty.

      if (doParse) {
        try {
          Document doc = LDMLUtilities.parse(depFilePath, false);
          // TODO: figure out if this is really required
          if (parseDraft && LDMLUtilities.isLocaleDraft(doc)) {
            isRegular = false;
          }
          if (isRegular && parseSubLocale) {
            Node collations = LDMLUtilities.getNode(doc, "//ldml/collations");
            if (collations != null) {
              String vsl = LDMLUtilities.getAttributeValue(collations, "validSubLocales");
              if (vsl != null && vsl.length() > 0) {
                validSubMap.put(localeName, vsl);
                log.info(localeName + " <- " + vsl);
              }
            }
          }
        } catch (Throwable t) {
          log.error(t.getMessage(), t);
          System.exit(-1);
        }
      }

      // (B) Collect fromFiles
      // If the file is root, ignore it.
      // If the file is regular (parseDraft is false, or the document was not draft), add it.
      // Else it is known draft.  If the draft status is overridable, add it.
      // Else skip it.

      // Trace progress.
      if (i > 0 && (i % 60 == 0)) {
        logEmit.emit(" " + i);
        logEmit.nl();
      }
      if (!localeName.equals("root")) {
        if (isRegular) {
          logEmit.emit(".");
          fromFiles.add(localeName);
        } else {
          if (services.isDraftStatusOverridable(localeName)) {
            fromFiles.add(localeName);
            logEmit.emit("o"); // override
          } else {
            logEmit.emit("d"); // draft
          }
        }
      } else {
        logEmit.emit("_");
      }
    }

    logEmit.nl();
  }

  private void generateEmptyLocales(List<String> emptyLocaleList, List<Resource> generated,
      Set<String> generatedAliasFiles) {
    if (emptyLocaleList != null && emptyLocaleList.size() > 0) {
      for (String loc : emptyLocaleList) {
        log.setStatus(loc);
        generated.add(generateSimpleLocaleAlias(loc, null,
            "empty locale file for dependency checking"));

        // We do not want these files to show up in the installed locales list.
        generatedAliasFiles.add(loc);
      }
    }
  }

  private void generateAliases(List<Alias> aliasList, boolean parseSubLocale,
      Map<String, String> maybeValidAlias, List<Resource> generated, Set<String> fromFiles,
      Set<String> generatedAliasFiles) {

    if (aliasList != null && aliasList.size() > 0) {
      for (Alias alias : aliasList) {
        String from = alias.from;
        String to = alias.to;
        String xpath = alias.xpath;

        log.setStatus(from);

        if (from == null || to == null) {
          log.error("Malformed alias - no 'from' or 'to': from=\"" + from + "\" to=\"" + to + "\"");
          System.exit(-1);
        }

        if (to.indexOf('@') != -1 && xpath == null) {
          log.error("Malformed alias - '@' but no xpath: from=\"" + from + "\" to=\"" + to + "\"");
          System.exit(-1);
        }

        String toFileName = xpath == null ? to : to.substring(0, to.indexOf('@'));

        if (fromFiles.contains(from)) {
          throw new IllegalArgumentException(
              "Can't be both a synthetic alias locale and a real xml file - "
              + "consider using <aliasLocale locale=\"" + from + "\"/> instead. ");
        }

        ULocale fromLocale = new ULocale(from);
        String fromLocaleName = fromLocale.toString();

        String target = toFileName;
        boolean targetExists = fromFiles.contains(target);

        if (!targetExists) {
          if (parseSubLocale) {
            maybeValidAlias.put(toFileName, from);
          } else {
            generated.add(generateSimpleLocaleAlias(target, null, "generated alias target"));
            generatedAliasFiles.add(target);
            targetExists = true;
          }
        }
        if (targetExists) {
          generatedAliasFiles.add(from);
          if (xpath != null) {
            CLDRFile fakeFile = CLDRFile.make(fromLocaleName);
            fakeFile.add(xpath, "");
            fakeFile.freeze();

            Resource res = services.parseBundle(fakeFile);
            if (res != null && ((ResourceTable) res).first != null) {
              res.name = fromLocaleName;
              generated.add(res);
            } else {
              // parse error?
              log.error("Failed to write out alias bundle " + fromLocaleName + " from " + xpath
                  + " - XML list follows:");
              fakeFile.write(new PrintWriter(System.out));
            }
          } else {
            // the following conversion is probably not necessary, it would normalize the alias
            // but it should be normalized to begin with
            String toLocaleName = new ULocale(target).toString();
            generated.add(generateSimpleLocaleAlias(from, toLocaleName, null));
          }
        }
      }
    }
  }

  private void updateAliasFromFiles(List<String> aliasLocaleList, Set<String> fromFiles,
      Set<String> aliasFromFiles) {
    if (aliasLocaleList != null && aliasLocaleList.size() > 0) {
      for (String source : aliasLocaleList) {
        log.setStatus(source);
        if (!fromFiles.contains(source)) {
          log.warning("Alias file named in deprecates list but not present. Ignoring alias entry.");
        } else {
          aliasFromFiles.add(source);
          fromFiles.remove(source);
        }
      }
      log.setStatus(null);
    }
  }

  private void processValidSubmap(Map<String, String> validSubMap, Map<String,
      String> maybeValidAlias, List<Resource> generated, Set<String> fromFiles,
      Set<String> emptyFromFiles, Set<String> generatedAliasFiles, Set<String> validLocales) {
    if (!validSubMap.isEmpty()) {
      log.info("Writing valid sublocales for: " + validSubMap.toString());

      for (Map.Entry<String, String> e : validSubMap.entrySet()) {
        String actualLocale = e.getKey();
        String list = e.getValue();

        log.setStatus(actualLocale + ".xml");
        String validSubs[] = list.split(" ");
        for (String aSub : validSubs) {
          String testSub;
          for (testSub = aSub;
               testSub != null && !testSub.equals("root") && !testSub.equals(actualLocale);
               testSub = LDMLUtilities.getParent(testSub)) {

            if (fromFiles.contains(testSub)) {
              log.warning(
                  "validSubLocale=" + aSub + " overridden because  " + testSub + ".xml  exists.");
              testSub = null;
              break;
            }

            if (generatedAliasFiles.contains(testSub)) {
              log.warning(
                  "validSubLocale=" + aSub + " overridden because an alias locale " + testSub
                  + ".xml  exists.");
              testSub = null;
              break;
            }
            
            if (!validLocales.contains(testSub)) {
                log.warning(
                    "validSubLocale=" + aSub + " (validSubLoale of \"" + actualLocale + "\") not written" +
                    " because it is not in the valid locales list.");
                testSub = null;
                break;
            }
          }

          if (testSub != null) {
            log.setStatus(aSub);

            emptyFromFiles.add(aSub);

            if (maybeValidAlias.containsKey(aSub)) {
              String from = maybeValidAlias.get(aSub);
              generated.add(generateSimpleLocaleAlias(from, aSub, null));

              maybeValidAlias.remove(aSub);
              generatedAliasFiles.add(from);
            }

            generated.add(generateSimpleLocaleAlias(aSub, null, "validSubLocale of \"" +
                actualLocale + "\""));
          }
        }
      }
    }
  }

  private void warnUnusedAliases(Map<String, String> maybeValidAlias) {
    if (!maybeValidAlias.isEmpty()) {
      for (Map.Entry<String, String> e : maybeValidAlias.entrySet()) {
        String to = e.getKey();
        String from = e.getValue();
        log.warning("Alias from \"" + from
            + "\" not generated, because it would point to a nonexistent LDML file " + to + ".xml");
      }
    }
  }

  // read all xml files in depDir and create ctd file list and brk file list
  private void getBrkCtdFiles(List<String> brkFiles, List<String> ctdFiles) {
    if (!"brkitr".equals(depDir.getName())) {
      return;
    }

    FilenameFilter xmlFilter = new FilenameFilter() {
      public boolean accept(File f, String name) {
        return name.endsWith(".xml") && !name.startsWith("supplementalData");
      }
    };

    File[] files = depDir.listFiles(xmlFilter);

    // Open each file and create the list of files for brk and ctd
    for (File file : files) {
      String fileName = file.getName();
      String filePath = file.getAbsolutePath();
      log.setStatus(fileName);

      Document doc = LDMLUtilities.parse(filePath, false);
      for (Node node = doc.getFirstChild(); node != null; node = node.getNextSibling()) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }

        String name = node.getNodeName();

        if (name.equals(LDMLConstants.IDENTITY)) {
          continue;
        }

        if (name.equals(LDMLConstants.LDML)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(LDMLConstants.SPECIAL)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(ICU_BRKITR_DATA)) {
          node = node.getFirstChild();
          continue;
        }

        if (name.equals(ICU_BOUNDARIES)) {
          for (Node cn = node.getFirstChild(); cn != null; cn = cn.getNextSibling()) {
            if (cn.getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }
            String cnName = cn.getNodeName();

            if (cnName.equals(ICU_GRAPHEME)
                || cnName.equals(ICU_WORD)
                || cnName.equals(ICU_TITLE)
                || cnName.equals(ICU_SENTENCE)
                || cnName.equals(ICU_XGC)
                || cnName.equals(ICU_LINE)) {

              String val = LDMLUtilities.getAttributeValue(cn, ICU_DEPENDENCY);
              if (val != null) {
                brkFiles.add(val.substring(0, val.indexOf('.')) + ".txt");
              }
            } else {
              log.error("Encountered unknown <" + name + "> subelement: " + cnName);
              System.exit(-1);
            }
          }
          continue;
        }

        if (name.equals(ICU_DICTIONARIES)) {
          for (Node cn = node.getFirstChild(); cn != null; cn = cn.getNextSibling()) {
            if (cn.getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }
            String cnName = cn.getNodeName();

            if (cnName.equals(ICU_DICTIONARY)) {
              String val = LDMLUtilities.getAttributeValue(cn, ICU_DEPENDENCY);
              if (val != null) {
                ctdFiles.add(val.substring(0, val.indexOf('.')) + ".txt ");
              }
            } else {
              log.error("Encountered unknown <" + name + "> subelement: " + cnName);
              System.exit(-1);
            }
          }
          continue;
        }

        // Unrecognized node
        log.error("Encountered unknown <" + doc.getNodeName() + "> subelement: " + name);
        System.exit(-1);
      }
    }
  }

  private void writeResources(ICUWriter writer, List<Resource> generated) {
    log.setStatus(null);
    log.log("Writing generated resources");
    for (Resource res : generated) {
      log.setStatus(res.name);
      writer.writeResource(res, SOURCE_INFO);
    }
  }

  private void writeMakefile(MakefileInfo info) {
    ICUMakefileWriter mfw = new ICUMakefileWriter(log);
    // TODO(dougfelt): pass this in as a param, this is a hack
    String[] mapping = {
        "locales", "GENRB", "res",
        "curr", "CURR", "res",
        "lang", "LANG", "res",
        "region", "REGION", "res",
        "zone", "ZONE", "res",
        "coll", "COLLATION", "col",
        "brkitr", "BRK_RES", "brk",
        "rbnf", "RBNF", "rbnf",
    };
    String dstDirName = dstDir.getName();
    String stub = null;
    String shortStub = null;
    for (int i = 0; i < mapping.length; i += 3) {
      if (mapping[i].equals(dstDirName)) {
        stub = mapping[i+1];
        shortStub = mapping[i+2];
        break;
      }
    }
    if (stub == null) {
      throw new IllegalArgumentException("no mapping for '" + dstDirName + "'");
    }

    mfw.write(info, stub, shortStub, dstDir);
  }

  private Resource generateSimpleLocaleAlias(String fromLocale, String toLocale, String comment) {
    String dstFilePath = new File(dstDir, fromLocale + ".txt").getPath();
    ResourceTable res = new ResourceTable();
    try {
      res.name = fromLocale;
      if (toLocale != null) {
        ResourceString str = new ResourceString();
        str.name = "\"%%ALIAS\"";
        str.val = toLocale;
        res.first = str;
      } else {
        ResourceString str = new ResourceString();
        str.name = "___";
        str.val = "";
        str.comment = "so genrb doesn't issue warnings";
        res.first = str;
      }
      if (comment != null) {
        res.comment = comment;
      }
    } catch (Throwable e) {
      log.error("building synthetic locale tree for " + dstFilePath, e);
      System.exit(1);
    }

    String info;
    if (toLocale != null) {
      info = "alias to " + toLocale;
    } else {
      info = comment;
    }
    log.log("Generating " + dstFilePath + " (" + info + ")");

    return res;
  }
}