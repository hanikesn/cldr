package org.unicode.cldr.unittest;

import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.Relation;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Utility;

import com.ibm.icu.impl.OlsonTimeZone;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.TimeZoneRule;
import com.ibm.icu.util.TimeZoneTransition;
import com.ibm.icu.util.ULocale;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class TestMetazoneTransitions {

  private static final int printDaylightTransitions = 6;

  private static final int HOUR = 60 * 60 * 1000;

  static final long startDate;

  static final long endDate;

  static final SimpleDateFormat neutralFormat = new SimpleDateFormat(
      "yyyy-MM-dd HH:mm:ss", ULocale.ENGLISH);
  static {
    TimeZone GMT = TimeZone.getTimeZone("Etc/GMT");
    neutralFormat.setTimeZone(GMT);
    Calendar cal = Calendar.getInstance(GMT, ULocale.US);
    int year = cal.get(Calendar.YEAR);
    cal.clear(); // need to clear fractional seconds
    cal.set(1970, 0, 1, 0, 0, 0);
    startDate = cal.getTimeInMillis();
    cal.set(year + 5, 0, 1, 0, 0, 0);
    endDate = cal.getTimeInMillis();
    if (startDate != 0) {
      throw new IllegalArgumentException();
    }
  }

  public static void main(String[] args) {
    new TestMetazoneTransitions().run();
  }

  private static class ZoneTransition implements Comparable<ZoneTransition> {
    long date;

    int offset;

    public boolean equals(Object that) {
      ZoneTransition other = (ZoneTransition) that;
      return date == other.date && offset == other.offset;
    }

    public int hashCode() {
      return (int) (date ^ (date >>> 32) ^ offset);
    }

    public ZoneTransition(long date, int offset) {
      this.date = date;
      this.offset = offset;
    }

    /**
     * Return the one with the smaller offset, or if equal then the smallest
     * time
     * 
     * @param o
     * @return
     */
    public int compareTo(ZoneTransition o) {
      int delta = offset - o.offset;
      if (delta != 0)
        return delta;
      long delta2 = date - o.date;
      return delta2 == 0 ? 0 : delta2 < 0 ? -1 : 1;
    }

    @Override
    public String toString() {
      return neutralFormat.format(date) + ": " + ((double) offset) / HOUR
          + "hrs";
    }
  }

  enum DaylightChoice {NO_DAYLIGHT, ONLY_DAYLIGHT};
  
  private static class ZoneTransitions implements Comparable<ZoneTransitions> {
    List<ZoneTransition> chronologicalList = new ArrayList<ZoneTransition>();

    public boolean equals(Object that) {
      ZoneTransitions other = (ZoneTransitions) that;
      return chronologicalList.equals(other.chronologicalList);
    }

    public int hashCode() {
      return chronologicalList.hashCode();
    }

    public ZoneTransitions(String tzid, DaylightChoice allowDaylight) {
      TimeZone zone = TimeZone.getTimeZone(tzid);
      for (long date = startDate; date < endDate; date = getTransitionAfter(zone, date)) {
        addIfDifferent(zone, date, allowDaylight);
      }
    }

    private void addIfDifferent(TimeZone zone, long date,
        DaylightChoice allowDaylight) {
      int offset = zone.getOffset(date);
      int delta = getDSTSavings(zone, date);
      switch (allowDaylight) {
        case ONLY_DAYLIGHT:
          offset = delta;
          break;
        case NO_DAYLIGHT: 
          offset -= delta;
          break;
      }
      int size = chronologicalList.size();
      if (size > 0) {
        ZoneTransition last = chronologicalList.get(size - 1);
        if (last.offset == offset) {
          return;
        }
      }
      chronologicalList.add(new ZoneTransition(date, offset));
    }

    public int compareTo(ZoneTransitions other) {
      int minSize = Math.min(chronologicalList.size(), other.chronologicalList
          .size());
      for (int i = 0; i < minSize; ++i) {
        ZoneTransition a = chronologicalList.get(i);
        ZoneTransition b = other.chronologicalList.get(i);
        int order = a.compareTo(b);
        if (order != 0)
          return order;
      }
      return chronologicalList.size() - other.chronologicalList.size();
    }

    public String toString(String separator, int abbreviateToSize) {
      if (abbreviateToSize > 0 && chronologicalList.size() > abbreviateToSize) {
        int limit = abbreviateToSize/2;
        return Utility.join(slice(chronologicalList, 0, limit), separator)
        + separator + "..." + separator
        + Utility.join(slice(chronologicalList, chronologicalList.size() - limit, chronologicalList.size()), separator);
      }
      return Utility.join(chronologicalList, separator);
    }

    public String toString() {
      return toString("; ", -1);
    }

    public int size() {
      // TODO Auto-generated method stub
      return chronologicalList.size();
    }

    public Pair<ZoneTransitions, ZoneTransitions> getDifferenceFrom(ZoneTransitions other) {
      int minSize = Math.min(chronologicalList.size(), other.chronologicalList
          .size());
      for (int i = 0; i < minSize; ++i) {
        ZoneTransition a = chronologicalList.get(i);
        ZoneTransition b = other.chronologicalList.get(i);
        int order = a.compareTo(b);
        if (order != 0)
          return new Pair(a,b);
      }
      if (chronologicalList.size() > other.chronologicalList.size()) {
        return new Pair(chronologicalList.get(minSize), null);
      } else if (chronologicalList.size() < other.chronologicalList.size()) {
        return new Pair(null, other.chronologicalList.get(minSize));
      } else {
        return new Pair(null, null);
      }
    }
  }

  final static SupplementalDataInfo supplementalData = SupplementalDataInfo
      .getInstance("C:/cvsdata/unicode/cldr/common/supplemental/");

  private void run() {
    // String[] zones = TimeZone.getAvailableIDs();
    Relation<ZoneTransitions, String> partition = new Relation(new TreeMap(),
        TreeSet.class);
    Relation<ZoneTransitions, String> daylightPartition = new Relation(
        new TreeMap(), TreeSet.class);
    Map<String, ZoneTransitions> toDaylight = new TreeMap();
    Map<ZoneTransitions, String> daylightNames = new TreeMap();
    int daylightCount = 0;

    // get the main data
    for (String zone : supplementalData.getCanonicalZones()) {
      ZoneTransitions transitions = new ZoneTransitions(zone, DaylightChoice.NO_DAYLIGHT);
      partition.put(transitions, zone);
      transitions = new ZoneTransitions(zone, DaylightChoice.ONLY_DAYLIGHT);
      if (transitions.size() > 1) {
        daylightPartition.put(transitions, zone);
        toDaylight.put(zone, transitions);
      }
    }
    // now assign names
    int count = 0;
    for (ZoneTransitions transitions : daylightPartition.keySet()) {
      daylightNames.put(transitions, "D"  + (++count));
    }
    
    System.out.println();
    System.out.println("=====================================================");
    System.out.println("Non-Daylight Partition");
    System.out.println("=====================================================");
    System.out.println();

    count = 0;
    for (ZoneTransitions transitions : partition.keySet()) {
      System.out.println();
      System.out.println("Non-Daylight Partition M" + (++count));
      String daylightName = daylightNames.get(transitions);
      for (String zone : partition.getAll(transitions)) {
        System.out.println("\t" + zone
            + (daylightName == null ? "" : "\t" + daylightName));
      }
      System.out.println("\t\t" + transitions.toString("\r\n\t\t", -1));
    }
    System.out.println();
    System.out.println("=====================================================");
    System.out.println("Daylight Partition");
    System.out.println("=====================================================");
    System.out.println();
    
    ZoneTransitions lastTransitions = null;
    String lastName = null;
    for (ZoneTransitions transitions : daylightPartition.keySet()) {
      System.out.println();
      String daylightName = daylightNames.get(transitions);
      System.out.println("Daylight Partition\t" + daylightName);
      for (String zone : daylightPartition.getAll(transitions)) {
        System.out.println("\t" + zone);
      }
      System.out.println("\t\t" + transitions.toString("\r\n\t\t", printDaylightTransitions));
      if (lastTransitions != null ) {
        Pair<ZoneTransitions, ZoneTransitions> diff = transitions.getDifferenceFrom(lastTransitions);
        System.out.println("\t\tTransition Difference from " + lastName + ":\t"+ diff);
      }
      lastTransitions = transitions;
      lastName = daylightName;
    }

  }
  
  public static <T> List<T> slice(List<T> list, int start, int limit) {
    ArrayList<T> temp = new ArrayList<T>();
    for (int i = start; i < limit; ++i) {
      temp.add(list.get(i));
    }
    return temp;
  }

  /* Methods that ought to be on TimeZone */
  /**
   * Return the next point in time after date when the zone has a different
   * offset than what it has on date. If there are no later transitions, returns
   * Long.MAX_VALUE.
   * 
   * @param zone
   *          input zone -- should be method of TimeZone
   * @param date
   *          input date, in standard millis since 1970-01-01 00:00:00 GMT
   */
  public static long getTransitionAfter(TimeZone zone, long date) {
    TimeZoneTransition transition = ((OlsonTimeZone) zone).getNextTransition(
        date, false);
    if (transition == null) {
      return Long.MAX_VALUE;
    }
    date = transition.getTime();
    return date;
  }

  /**
   * Return true if the zone is in daylight savings on the date.
   * 
   * @param zone
   *          input zone -- should be method of TimeZone
   * @param date
   *          input date, in standard millis since 1970-01-01 00:00:00 GMT
   */
  public static boolean inDaylightTime(TimeZone zone, long date) {
    return ((OlsonTimeZone) zone).inDaylightTime(new Date(date));
  }

  /**
   * Return the daylight savings offset on the given date.
   * 
   * @param zone
   *          input zone -- should be method of TimeZone
   * @param date
   *          input date, in standard millis since 1970-01-01 00:00:00 GMT
   */
  public static int getDSTSavings(TimeZone zone, long date) {
    if (!inDaylightTime(zone, date)) {
      return 0;
    }
    TimeZoneTransition transition = ((OlsonTimeZone) zone)
        .getPreviousTransition(date + 1, true);
    TimeZoneRule to = transition.getTo();
    int delta = to.getDSTSavings();
    //    if (delta != HOUR) {
    //      System.out.println("Delta " + delta/(double)HOUR + " for " + zone.getID());
    //    }
    return delta;
  }
}