package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.materialize.*;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 * @author Edward Sciore
 */
class TablePlanner {
   // whether to print which access and join methods are chosen;
   // turned off while an optimizer builds plans only to cost them
   static boolean verbose = true;

   private TablePlan myplan;
   private Predicate mypred;
   private Schema myschema;
   private Map<String,IndexInfo> indexes;
   private Transaction tx;
   
   /**
    * Creates a new table planner.
    * The specified predicate applies to the entire query.
    * The table planner is responsible for determining
    * which portion of the predicate is useful to the table,
    * and when indexes are useful.
    * @param tblname the name of the table
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, mdm);
      myschema = myplan.schema();
      indexes  = mdm.getIndexInfo(tblname, tx);
   }
   
   /**
    * Constructs a select plan for the table.
    * The plan will use an indexselect, if possible.
    * @return a select plan for the table.
    */
   public Plan makeSelectPlan() {
      Plan p = makeIndexSelect();
      if (p == null)
         p = myplan;
      return addSelectPred(p);
   }
   
   /**
    * Constructs a join plan of the specified plan
    * and the table.  The method tries, in order of
    * preference: an indexjoin (if an equality join field
    * is indexed), the cheaper of a hashjoin and a mergejoin
    * (if an equality join field exists but is not indexed),
    * and finally a
    * nestedloopjoin, which works for any join predicate.
    * The method returns null if no join is possible.
    * @param current the specified plan
    * @return a join plan of the plan and this table
    */
   public Plan makeJoinPlan(Plan current) {
      Schema currsch = current.schema();
      Predicate joinpred = mypred.joinSubPred(myschema, currsch);
      if (joinpred == null)
         return null;
      Plan p = makeIndexJoin(current, currsch);
      if (p == null)
         p = makeEquiJoin(current, currsch);
      if (p == null)
         p = makeNestedLoopJoin(current, currsch, joinpred);
      return p;
   }
   
   /**
    * Constructs a product plan of the specified plan and
    * this table.
    * @param current the specified plan
    * @return a product plan of the specified plan and this table
    */
   public Plan makeProductPlan(Plan current) {
      Plan p = addSelectPred(myplan);
      return new MultibufferProductPlan(tx, current, p);
   }
   
   private Plan makeIndexSelect() {
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null) {
            IndexInfo ii = indexes.get(fldname);
            if (verbose)
               System.out.println("index on " + fldname + " used");
            return new IndexSelectPlan(myplan, ii, val);
         }
      }
      return null;
   }
   
   private Plan makeIndexJoin(Plan current, Schema currsch) {
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            IndexInfo ii = indexes.get(fldname);
            Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
            p = addSelectPred(p);
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }
   
   /**
    * Tries to construct an equijoin plan, for the case when
    * the join predicate equates a field of this table with a
    * field of the current plan but no index is available.
    * Both a hashjoin and a mergejoin are considered, and the
    * one with the lower estimated total cost (including the
    * one-time cost of partitioning or sorting) is chosen.
    */
   private Plan makeEquiJoin(Plan current, Schema currsch) {
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan rhs = addSelectPred(myplan);
            HashJoinPlan hash = new HashJoinPlan(tx, current, rhs, outerfield, fldname);
            MergeJoinPlan merge = new MergeJoinPlan(tx, current, rhs, outerfield, fldname);
            int hashcost = hash.partitionCost() + hash.blocksAccessed();
            int mergecost = PlanCost.sortCost(current, tx) + PlanCost.sortCost(rhs, tx)
                  + merge.blocksAccessed();
            Plan p;
            if (hashcost < mergecost) {
               if (verbose)
                  System.out.println("hashjoin on " + outerfield + "=" + fldname + " used"
                        + " (est. cost " + hashcost + " vs mergejoin " + mergecost + ")");
               p = hash;
            }
            else {
               if (verbose)
                  System.out.println("mergejoin on " + outerfield + "=" + fldname + " used"
                        + " (est. cost " + mergecost + " vs hashjoin " + hashcost + ")");
               p = merge;
            }
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }
   
   /**
    * Returns every join plan of the specified plan and this table,
    * one for each applicable join method: an indexjoin for each
    * indexed field equated with a field of the current plan, a
    * hashjoin and a mergejoin on the first equated field, and a
    * nestedloopjoin. Unlike {@link #makeJoinPlan}, no method is
    * preferred over another; the caller compares their costs.
    * The list is empty if no join predicate connects the plan
    * and this table.
    * @param current the specified plan
    * @return the candidate join plans
    */
   public List<Plan> joinCandidates(Plan current) {
      List<Plan> plans = new ArrayList<>();
      Schema currsch = current.schema();
      Predicate joinpred = mypred.joinSubPred(myschema, currsch);
      if (joinpred == null)
         return plans;
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan p = new IndexJoinPlan(current, myplan, indexes.get(fldname), outerfield);
            p = addSelectPred(p);
            plans.add(addJoinPred(p, currsch));
         }
      }
      for (String fldname : myschema.fields()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            Plan rhs = addSelectPred(myplan);
            plans.add(addJoinPred(new HashJoinPlan(tx, current, rhs, outerfield, fldname), currsch));
            plans.add(addJoinPred(new MergeJoinPlan(tx, current, rhs, outerfield, fldname), currsch));
            break;
         }
      }
      plans.add(new NestedLoopJoinPlan(current, addSelectPred(myplan), joinpred));
      return plans;
   }

   /**
    * Constructs a nestedloopjoin plan of the specified plan and
    * this table. Unlike {@link #makeProductPlan}, the join
    * predicate is evaluated directly by the join scan instead of
    * through a separate selection operator. This is the fallback
    * join strategy, used when no index or equality join field is
    * available (e.g. for non-equality join predicates).
    */
   private Plan makeNestedLoopJoin(Plan current, Schema currsch, Predicate joinpred) {
      Plan rhs = addSelectPred(myplan);
      if (verbose)
         System.out.println("nestedloopjoin used");
      return new NestedLoopJoinPlan(current, rhs, joinpred);
   }
   
   private Plan addSelectPred(Plan p) {
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         return new SelectPlan(p, selectpred);
      else
         return p;
   }
   
   private Plan addJoinPred(Plan p, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      if (joinpred != null)
         return new SelectPlan(p, joinpred);
      else
         return p;
   }
}
