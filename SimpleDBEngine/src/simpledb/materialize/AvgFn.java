package simpledb.materialize;

import static java.sql.Types.INTEGER;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>avg</i> aggregation function.
 * The aggregated field must be an integer field.
 * Since SimpleDB only supports integer and string values,
 * the average is an integer, truncated toward zero.
 */
public class AvgFn implements AggregationFn {
   private String fldname;
   private long sum;
   private int count;
   
   /**
    * Create an avg aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public AvgFn(String fldname) {
      this.fldname = fldname;
   }
   
   /**
    * Start a new running sum and count
    * from the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = s.getInt(fldname);
      count = 1;
   }
   
   /**
    * Add the current record to the running sum and count.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getInt(fldname);
      count++;
   }
   
   /**
    * Return the field's name, prepended by "avgof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "avgof" + fldname;
   }
   
   /**
    * Return the name of the field being aggregated.
    * @see simpledb.materialize.AggregationFn#aggregatedFieldName()
    */
   public String aggregatedFieldName() {
      return fldname;
   }
   
   /**
    * Return the current average.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return new Constant((int) (sum / count));
   }
   
   /**
    * Only integer fields can be averaged.
    * @see simpledb.materialize.AggregationFn#addField(Schema, Schema)
    */
   public void addField(Schema sch, Schema source) {
      if (!source.hasField(fldname))
         throw new RuntimeException("field " + fldname + " not found.");
      if (source.type(fldname) != INTEGER)
         throw new RuntimeException("cannot average non-integer field " + fldname);
      sch.addIntField(fieldName());
   }
   
   public String toString() {
      return "avg(" + fldname + ")";
   }
}
