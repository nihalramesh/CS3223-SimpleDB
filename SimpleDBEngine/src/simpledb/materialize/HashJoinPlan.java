package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The Plan class for the partition-based <i>hashjoin</i> operator.
 * Both inputs are partitioned on their join field using the
 * same hash function, so matching records always land in
 * partitions with the same index. Each pair of partitions
 * is then joined separately by {@link HashJoinScan}.
 */
public class HashJoinPlan implements Plan {
   private Transaction tx;
   private Plan p1, p2;
   private String fldname1, fldname2;
   private Schema sch = new Schema();

   /**
    * Creates a hashjoin plan for the two specified queries.
    * The smaller of the two inputs is used as the build side,
    * whose partitions are loaded into memory one at a time.
    * @param tx the calling transaction
    * @param p1 the LHS query plan
    * @param p2 the RHS query plan
    * @param fldname1 the LHS join field
    * @param fldname2 the RHS join field
    */
   public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      this.p1 = p1;
      this.p2 = p2;
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;
      sch.addAll(p1.schema());
      sch.addAll(p2.schema());
   }

   /**
    * Partitions both inputs into temporary tables
    * and returns a hashjoin scan over the partitions.
    * The number of partitions is one less than the number
    * of buffers available once both inputs are open, since
    * partitioning needs one buffer per output partition.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      int numparts = Math.max(1, tx.availableBuffs() - 1);
      List<TempTable> parts1 = partition(s1, p1.schema(), fldname1, numparts);
      s1.close();
      List<TempTable> parts2 = partition(s2, p2.schema(), fldname2, numparts);
      s2.close();
      if (p2.recordsOutput() <= p1.recordsOutput())
         return new HashJoinScan(parts1, parts2, fldname1, fldname2, p2.schema());
      else
         return new HashJoinScan(parts2, parts1, fldname2, fldname1, p1.schema());
   }

   /**
    * Returns the number of block accesses required to
    * join the partitions, which is one pass through
    * the partitions of each input.
    * It does <i>not</i> include the one-time cost
    * of partitioning the inputs; see {@link #partitionCost()}.
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return materializedSize(p1) + materializedSize(p2);
   }

   /**
    * Returns the one-time cost of partitioning:
    * reading each input once and writing its partitions.
    */
   public int partitionCost() {
      return p1.blocksAccessed() + p2.blocksAccessed()
            + materializedSize(p1) + materializedSize(p2);
   }

   /**
    * Returns the number of records in the join.
    * Assuming uniform distribution, the formula is:
    * <pre> R(join(p1,p2)) = R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)}</pre>
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      int maxvals = Math.max(p1.distinctValues(fldname1),
                             p2.distinctValues(fldname2));
      return (p1.recordsOutput() * p2.recordsOutput()) / maxvals;
   }

   /**
    * Estimates the distinct number of field values in the join.
    * Since the join does not increase or decrease field values,
    * the estimate is the same as in the appropriate underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   /**
    * Returns the schema of the join,
    * which is the union of the schemas of the underlying queries.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return sch;
   }

   public Plan[] children() {
      return new Plan[] { p1, p2 };
   }

   public String description() {
      return "HashJoinPlan(fields=" + fldname1 + "=" + fldname2 + ")";
   }

   private int materializedSize(Plan p) {
      return new MaterializePlan(tx, p).blocksAccessed(); // not opened; just for analysis
   }

   /**
    * Copies each record of the scan into the partition
    * given by the hash of its join field value.
    */
   private List<TempTable> partition(Scan src, Schema srcsch, String fldname, int numparts) {
      List<TempTable> parts = new ArrayList<>();
      List<UpdateScan> dests = new ArrayList<>();
      for (int i = 0; i < numparts; i++) {
         TempTable t = new TempTable(tx, srcsch);
         parts.add(t);
         dests.add(t.open());
      }
      src.beforeFirst();
      while (src.next()) {
         UpdateScan dest = dests.get(partitionOf(src.getVal(fldname), numparts));
         dest.insert();
         for (String f : srcsch.fields())
            dest.setVal(f, src.getVal(f));
      }
      for (UpdateScan dest : dests)
         dest.close();
      return parts;
   }

   private static int partitionOf(Constant val, int numparts) {
      return Math.floorMod(val.hashCode(), numparts);
   }
}
