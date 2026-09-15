package simpledb.plan;

import simpledb.query.NestedLoopJoinScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/** The Plan class corresponding to the <i>nestedloopjoin</i>
  * relational algebra operator.
  * It forms the cross product of its two underlying plans,
  * like {@link ProductPlan}, but the join predicate is evaluated
  * directly by the underlying scan, so no separate selection
  * operator is required on top of it.
  * @author Edward Sciore
  */
public class NestedLoopJoinPlan implements Plan {
   private Plan p1, p2;
   private Predicate joinpred;
   private Schema schema = new Schema();

   /**
    * Creates a new nestedloopjoin node in the query tree,
    * having the two specified subqueries and join predicate.
    * @param p1 the left-hand (outer) subquery
    * @param p2 the right-hand (inner) subquery
    * @param joinpred the predicate joining the two subqueries
    */
   public NestedLoopJoinPlan(Plan p1, Plan p2, Predicate joinpred) {
      this.p1 = p1;
      this.p2 = p2;
      this.joinpred = joinpred;
      schema.addAll(p1.schema());
      schema.addAll(p2.schema());
   }

   /**
    * Creates a nestedloopjoin scan for this query.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopJoinScan(s1, s2, joinpred);
   }

   /**
    * Estimates the number of block accesses in the join.
    * For every outer record, the inner plan is scanned in full,
    * so the formula is the same as for a product:
    * <pre> B(nestedloopjoin(p1,p2)) = B(p1) + R(p1)*B(p2) </pre>
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      return p1.blocksAccessed() + (p1.recordsOutput() * p2.blocksAccessed());
   }

   /**
    * Estimates the number of output records in the join,
    * using the reduction factor of the join predicate.
    * <pre> R(nestedloopjoin(p1,p2)) = R(p1)*R(p2)/reductionFactor(joinpred) </pre>
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      return (p1.recordsOutput() * p2.recordsOutput()) / joinpred.reductionFactor(this);
   }

   /**
    * Estimates the distinct number of field values in the join.
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
      return schema;
   }
}
