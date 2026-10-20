package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The Scan class for the partition-based <i>hashjoin</i> operator.
 * The partitions are processed one pair at a time: the build
 * partition is loaded into an in-memory hash table keyed on its
 * join field, and the probe partition is then scanned, looking up
 * the matching build records for each probe record.
 */
public class HashJoinScan implements Scan {
   private List<TempTable> probeparts, buildparts;
   private String probefld, buildfld;
   private Schema buildsch;
   private int currentpart;
   private Scan probe;
   private Map<Constant, List<Map<String,Constant>>> table;
   private List<Map<String,Constant>> matches;
   private int matchpos;
   private Map<String,Constant> buildrec;

   /**
    * Create a hashjoin scan over the specified partitions,
    * where partition i of the probe side can only match
    * partition i of the build side.
    * @param probeparts the partitions of the probe input
    * @param buildparts the partitions of the build input
    * @param probefld the join field of the probe input
    * @param buildfld the join field of the build input
    * @param buildsch the schema of the build input
    */
   public HashJoinScan(List<TempTable> probeparts, List<TempTable> buildparts,
                       String probefld, String buildfld, Schema buildsch) {
      this.probeparts = probeparts;
      this.buildparts = buildparts;
      this.probefld = probefld;
      this.buildfld = buildfld;
      this.buildsch = buildsch;
      beforeFirst();
   }

   /**
    * Position the scan before the first record of the join,
    * which is before the first pair of partitions.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      closeProbe();
      currentpart = -1;
      matches = null;
      buildrec = null;
   }

   /**
    * Move to the next joined record. If the current probe
    * record has more matching build records, move to the
    * next one; otherwise move to the next probe record
    * that has matches, moving on to the next pair of
    * partitions whenever the current probe partition
    * is exhausted.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (true) {
         if (matches != null && matchpos < matches.size()) {
            buildrec = matches.get(matchpos++);
            return true;
         }
         if (probe != null && probe.next()) {
            matches = table.get(probe.getVal(probefld));
            matchpos = 0;
            continue;
         }
         closeProbe();
         if (currentpart + 1 >= probeparts.size())
            return false;
         currentpart++;
         loadPartition(currentpart);
      }
   }

   /**
    * Close the scan by closing the current probe partition.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      closeProbe();
   }

   /**
    * Return the value of the specified field, taken from the
    * probe record or from the matching build record.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (buildsch.hasField(fldname))
         return buildrec.get(fldname);
      else
         return probe.getVal(fldname);
   }

   public int getInt(String fldname) {
      return getVal(fldname).asInt();
   }

   public String getString(String fldname) {
      return getVal(fldname).asString();
   }

   /**
    * Return true if the specified field is in
    * either of the underlying inputs.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return buildsch.hasField(fldname) || probeparts.get(0).getLayout().schema().hasField(fldname);
   }

   /**
    * Build the in-memory hash table for the specified build
    * partition, and open the corresponding probe partition.
    * The probe partition is skipped if no build records exist.
    */
   private void loadPartition(int i) {
      table = new HashMap<>();
      Scan s = buildparts.get(i).open();
      while (s.next()) {
         Map<String,Constant> rec = new HashMap<>();
         for (String fldname : buildsch.fields())
            rec.put(fldname, s.getVal(fldname));
         table.computeIfAbsent(rec.get(buildfld), k -> new ArrayList<>()).add(rec);
      }
      s.close();
      matches = null;
      if (!table.isEmpty())
         probe = probeparts.get(i).open();
   }

   private void closeProbe() {
      if (probe != null) {
         probe.close();
         probe = null;
      }
   }
}
