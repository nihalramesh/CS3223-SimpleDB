package simpledb.plan;

import simpledb.query.DistinctScan;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The plan corresponding to the SQL DISTINCT operator.
 */
public class DistinctPlan implements Plan {
   private Plan p;

   public DistinctPlan(Plan p) {
      this.p = p;
   }

   public Scan open() {
      return new DistinctScan(p.open(), p.schema().fields());
   }

   public int blocksAccessed() {
      return p.blocksAccessed();
   }

   public int recordsOutput() {
      int maxDistinct = 1;
      for (String fldname : p.schema().fields()) {
         int distinct = p.distinctValues(fldname);
         if (distinct > Integer.MAX_VALUE / maxDistinct)
            return p.recordsOutput();
         maxDistinct *= distinct;
      }
      return Math.min(p.recordsOutput(), maxDistinct);
   }

   public int distinctValues(String fldname) {
      return p.distinctValues(fldname);
   }

   public Schema schema() {
      return p.schema();
   }

   public Plan[] children() {
      return new Plan[] { p };
   }

   public String description() {
      return "DistinctPlan";
   }
}
