package simpledb.materialize;

import static java.sql.Types.INTEGER;
import static java.sql.Types.DOUBLE;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The <i>sum</i> aggregation function.
 * The aggregated field must be an integer or double field;
 * the sum has the same type as the field.
 */
public class SumFn implements AggregationFn {
   private String fldname;
   private double sum;
   private boolean isDouble; // whether the summed field is a double
   
   /**
    * Create a sum aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public SumFn(String fldname) {
      this.fldname = fldname;
   }
   
   /**
    * Start a new sum to be the
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asDouble();
   }
   
   /**
    * Add the field value in the current record to the sum.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getVal(fldname).asDouble();
   }
   
   /**
    * Return the field's name, prepended by "sumof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "sumof" + fldname;
   }
   
   /**
    * Return the name of the field being aggregated.
    * @see simpledb.materialize.AggregationFn#aggregatedFieldName()
    */
   public String aggregatedFieldName() {
      return fldname;
   }
   
   /**
    * Return the current sum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return isDouble ? new Constant(sum) : new Constant((int) sum);
   }
   
   /**
    * Only integer and double fields can be summed.
    * @see simpledb.materialize.AggregationFn#addField(Schema, Schema)
    */
   public void addField(Schema sch, Schema source) {
      if (!source.hasField(fldname))
         throw new RuntimeException("field " + fldname + " not found.");
      int type = source.type(fldname);
      if (type != INTEGER && type != DOUBLE)
         throw new RuntimeException("cannot sum non-numeric field " + fldname);
      isDouble = (type == DOUBLE);
      if (isDouble)
         sch.addDoubleField(fieldName());
      else
         sch.addIntField(fieldName());
   }
   
   public String toString() {
      return "sum(" + fldname + ")";
   }
}
