package simpledb.materialize;

import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>min</i> aggregation function.
 */
public class MinFn implements AggregationFn {
   private String fldname;
   private Constant val;
   
   /**
    * Create a min aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public MinFn(String fldname) {
      this.fldname = fldname;
   }
   
   /**
    * Start a new minimum to be the 
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      val = s.getVal(fldname);
   }
   
   /**
    * Replace the current minimum by the field value
    * in the current record, if it is lower.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) < 0)
         val = newval;
   }
   
   /**
    * Return the field's name, prepended by "minof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "minof" + fldname;
   }
   
   /**
    * Return the current minimum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return val;
   }
   
   /**
    * The minimum has the same type as the aggregated field.
    * @see simpledb.materialize.AggregationFn#addField(Schema, Schema)
    */
   public void addField(Schema sch, Schema source) {
      if (!source.hasField(fldname))
         throw new RuntimeException("field " + fldname + " not found.");
      sch.addField(fieldName(), source.type(fldname), source.length(fldname));
   }
   
   public String toString() {
      return "min(" + fldname + ")";
   }
}
