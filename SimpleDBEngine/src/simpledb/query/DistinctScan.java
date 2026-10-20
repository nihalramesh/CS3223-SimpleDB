package simpledb.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A scan that returns each row at most once.
 */
public class DistinctScan implements Scan {
   private Scan s;
   private List<String> fieldlist;
   private Set<List<Constant>> seen;

   public DistinctScan(Scan s, List<String> fieldlist) {
      this.s = s;
      this.fieldlist = fieldlist;
      seen = new HashSet<>();
   }

   public void beforeFirst() {
      s.beforeFirst();
      seen.clear();
   }

   public boolean next() {
      while (s.next()) {
         List<Constant> row = new ArrayList<>();
         for (String fldname : fieldlist)
            row.add(s.getVal(fldname));
         if (seen.add(row))
            return true;
      }
      return false;
   }

   public int getInt(String fldname) {
      return s.getInt(fldname);
   }

   public String getString(String fldname) {
      return s.getString(fldname);
   }

   public Constant getVal(String fldname) {
      return s.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return fieldlist.contains(fldname);
   }

   public void close() {
      s.close();
   }
}
