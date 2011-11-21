package org.unicode.cldr.util;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.XPathParts.Comments;

public class SimpleXMLSource extends XMLSource {
    private HashMap<String,String> xpath_value = new HashMap<String,String>(); // TODO change to HashMap, once comparator is gone
    private HashMap<String,String> xpath_fullXPath = new HashMap<String,String>();
    private Comments xpath_comments = new Comments(); // map from paths to comments.
    Factory factory; // for now, fix later
    public DraftStatus madeWithMinimalDraftStatus = DraftStatus.unconfirmed;

    public SimpleXMLSource(Factory factory, String localeID) {
      this.factory = factory;
      this.setLocaleID(localeID);
    }
    /** 
     * Create a shallow, locked copy of another XMLSource. 
     * @param copyAsLockedFrom
     */
    protected SimpleXMLSource(SimpleXMLSource copyAsLockedFrom) {
      this.xpath_value = copyAsLockedFrom.xpath_value;
      this.xpath_fullXPath = copyAsLockedFrom.xpath_fullXPath;
      this.xpath_comments = copyAsLockedFrom.xpath_comments;
      this.factory = copyAsLockedFrom.factory;
      this.madeWithMinimalDraftStatus = copyAsLockedFrom.madeWithMinimalDraftStatus;
      this.setLocaleID(copyAsLockedFrom.getLocaleID());
      locked=true;
    }
    public String getValueAtDPath(String xpath) {
      return (String)xpath_value.get(xpath);
    }
    public File getSupplementalDirectory() {
      final File result = new File(factory.getSourceDirectory(), "../supplemental/");
      if (result.isDirectory()) {
        return result;
      }
      return new File(CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
    }
    public String getFullPathAtDPath(String xpath) {
      String result = (String) xpath_fullXPath.get(xpath);
      if (result != null) return result;
      if (xpath_value.get(xpath) != null) return xpath; // we don't store duplicates
      //System.err.println("WARNING: "+getLocaleID()+": path not present in data: " + xpath);
      //return xpath;
      return null; // throw new IllegalArgumentException("Path not present in data: " + xpath);
    }
    public Comments getXpathComments() {
      return xpath_comments;
    }
    public void setXpathComments(Comments xpath_comments) {
      this.xpath_comments = xpath_comments;
    }
    //  public void putPathValue(String xpath, String value) {
    //  if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    //  String distinguishingXPath = CLDRFile.getDistinguishingXPath(xpath, fixedPath);	
    //  xpath_value.put(distinguishingXPath, value);
    //  if (!fixedPath[0].equals(distinguishingXPath)) {
    //  xpath_fullXPath.put(distinguishingXPath, fixedPath[0]);
    //  }
    //  }
    public void removeValueAtDPath(String distinguishingXPath) {
      xpath_value.remove(distinguishingXPath);
      xpath_fullXPath.remove(distinguishingXPath);
    }
    public Iterator<String> iterator() { // must be unmodifiable or locked
      return Collections.unmodifiableSet(xpath_value.keySet()).iterator();
    }
    public Object freeze() {
      locked = true;
      return this;
    }
    public Object cloneAsThawed() {
      SimpleXMLSource result = (SimpleXMLSource) super.cloneAsThawed();
      result.xpath_comments = (Comments) result.xpath_comments.clone();
      result.xpath_fullXPath = (HashMap<String,String>) result.xpath_fullXPath.clone();
      result.xpath_value = (HashMap) result.xpath_value.clone();
      return result;
    }
    public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) {
      xpath_fullXPath.put(distinguishingXPath, fullxpath);
    }
    public void putValueAtDPath(String distinguishingXPath, String value) {
      xpath_value.put(distinguishingXPath, value);
    }
    public XMLSource make(String localeID) {
      if (localeID == null) return null;
      CLDRFile file = factory.make(localeID, false, madeWithMinimalDraftStatus);
      if (file == null) return null;
      return file.dataSource;
    }
    public Set<String> getAvailableLocales() {
      return factory.getAvailable();
    }
  }