package simpledb.opt;

import simpledb.tx.Transaction;
import simpledb.materialize.*;
import simpledb.plan.Plan;

/**
 * Estimates the total number of block accesses needed to
 * execute a plan, so that different plans for the same query
 * can be compared fairly.
 * <p>
 * A plan's blocksAccessed() is not enough for this, because
 * some operators leave out one-time costs they pay when opened:
 * a SortPlan only counts reading its sorted output (not reading
 * its input, writing the runs and merging them), and a HashJoinPlan
 * only counts reading its partitions (not reading its inputs and
 * writing the partitions). Since every plan's blocksAccessed()
 * already counts its children's blocksAccessed(), the total cost is
 * the root's blocksAccessed() plus these missing one-time costs,
 * summed over every node in the tree.
 */
class PlanCost {
   private PlanCost() {
   }

   /**
    * Returns the estimated total cost of executing the plan.
    * @param p the plan
    * @param tx the calling transaction
    * @return the estimated number of block accesses
    */
   static long totalCost(Plan p, Transaction tx) {
      return p.blocksAccessed() + oneTimeCosts(p, tx);
   }

   /**
    * Estimates the one-time cost of sorting the output of p:
    * reading p and writing the initial runs, then reading and
    * writing every block once per merge iteration until at
    * most two runs remain (as done by SortPlan).
    * Assumes one initial run per block.
    * @param p the plan being sorted
    * @param tx the calling transaction
    * @return the estimated number of block accesses
    */
   static int sortCost(Plan p, Transaction tx) {
      int blocks = new MaterializePlan(tx, p).blocksAccessed();
      int runs = Math.max(1, blocks);
      int iterations = 0;
      while (runs > 2) {
         runs = (runs + 1) / 2;
         iterations++;
      }
      return p.blocksAccessed() + blocks + 2 * blocks * iterations;
   }

   private static long oneTimeCosts(Plan p, Transaction tx) {
      long cost = 0;
      if (p instanceof SortPlan)
         cost += sortCost(p.children()[0], tx);
      else if (p instanceof HashJoinPlan)
         cost += ((HashJoinPlan) p).partitionCost();
      for (Plan child : p.children())
         cost += oneTimeCosts(child, tx);
      return cost;
   }
}
