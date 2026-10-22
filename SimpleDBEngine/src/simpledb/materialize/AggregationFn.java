package simpledb.materialize;

import simpledb.query.*;
import simpledb.record.Schema;

/**
 * The interface implemented by aggregation functions.
 * Aggregation functions are used by the <i>groupby</i> operator.
 * @author Edward Sciore
 */
public interface AggregationFn {
   
   /**
    * Use the current record of the specified scan
    * to be the first record in the group.
    * @param s the scan to aggregate over.
    */
   void processFirst(Scan s);
   
   /**
    * Use the current record of the specified scan
    * to be the next record in the group.
    * @param s the scan to aggregate over.
    */
   void processNext(Scan s);
   
   /**
    * Return the name of the new aggregation field.
    * @return the name of the new aggregation field
    */
   String fieldName();

   /**
    * Return the name of the field being aggregated,
    * e.g. "sid" for count(sid).
    * @return the name of the aggregated field
    */
   String aggregatedFieldName();

   /**
    * Return the computed aggregation value.
    * @return the computed aggregation value
    */
   Constant value();
   
   /**
    * Add the new aggregation field to the specified output schema.
    * By default the field is an integer field; functions whose
    * result has the type of the aggregated field (min, max)
    * override this.
    * @param sch the output schema
    * @param source the schema of the records being aggregated
    */
   default void addField(Schema sch, Schema source) {
      sch.addIntField(fieldName());
   }
}
