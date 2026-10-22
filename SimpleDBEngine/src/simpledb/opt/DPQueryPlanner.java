package simpledb.opt;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.index.planner.IndexJoinPlan;
import simpledb.materialize.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.QueryData;
import simpledb.plan.*;

/**
 * A cost-based query planner that chooses the join order and the
 * join methods using dynamic programming, in the style of the
 * System R (Selinger) optimizer.
 * <p>
 * Unlike {@link HeuristicQueryPlanner}, which builds one join order
 * greedily (always adding the table that gives the smallest output)
 * and uses a fixed preference among join methods, this planner:
 * <ol>
 * <li>finds the cheapest plan for each single table (with its
 *     selection predicate, using an index if possible);
 * <li>for each set of 2, 3, ..., n tables, finds the best
 *     left-deep plans by trying each table of the set as the last
 *     (inner) table, joined to each plan already kept for the
 *     remaining tables, with every applicable join method
 *     (index, hash, sort-merge and nested-loops join);
 * <li>postpones cartesian products, using one only where no
 *     join predicate can connect the tables.
 * </ol>
 * Plans are compared by their estimated total block accesses
 * (see {@link PlanCost}). For each set of tables, only the plans
 * not beaten in both cost and output size are kept, so every
 * left-deep join order is considered without trying all n! of them.
 * For the whole query, the cost includes the group by and sort.
 */
public class DPQueryPlanner implements QueryPlanner {
   // 2^12 table sets is still fast; beyond that, fall back to the heuristic
   private static final int MAX_TABLES = 12;
   private MetadataMgr mdm;

   public DPQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }

   /**
    * Creates the cheapest left-deep plan for the query, then adds
    * the group by, sort, project and distinct steps as usual.
    * For queries over more than one table, prints the chosen join
    * order and its estimated cost, together with the estimated
    * cost of the plan the greedy heuristic planner would choose.
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      List<String> tables = new ArrayList<>(data.tables());
      int n = tables.size();
      if (n > MAX_TABLES) {
         System.out.println("dp optimizer: " + n + " tables is too many; "
               + "using the heuristic planner instead");
         return new HeuristicQueryPlanner(mdm).createPlan(data, tx);
      }

      int sets = 1 << n;   // bit i of a set means tables.get(i) is in it
      // the plans kept for each set of tables (see keep())
      List<List<Candidate>> plans = new ArrayList<>();
      for (int set = 0; set < sets; set++)
         plans.add(new ArrayList<>());
      int costed = 0;

      boolean verbose = TablePlanner.verbose;
      TablePlanner.verbose = false;
      try {
         List<TablePlanner> tps = new ArrayList<>();
         for (String tblname : tables)
            tps.add(new TablePlanner(tblname, data.pred(), tx, mdm));

         // Step 1: the cheapest plan for each single table
         for (int i = 0; i < n; i++) {
            Plan p = tps.get(i).makeSelectPlan();
            plans.get(1 << i).add(new Candidate(p, PlanCost.totalCost(p, tx),
                  p.recordsOutput(), tables.get(i), false));
            costed++;
         }

         // Step 2: the plans for each larger set of tables.
         // Every proper subset of a set is numerically smaller than it,
         // so the plans for all subsets are known when a set is reached.
         // Cartesian products are postponed, as in System R: a set is
         // planned in up to three passes, stopping at the first that
         // finds a plan:
         //   pass 0: join a table to a product-free plan of a subset
         //   pass 1: join a table to any plan of a subset
         //   pass 2: take the product of a table and a plan of a subset
         // A product's cost estimate can be misleadingly low (it assumes
         // the inner input fits in memory), so a product is used only
         // where no join predicate can connect the tables.
         for (int set = 1; set < sets; set++) {
            if (Integer.bitCount(set) < 2)
               continue;
            boolean full = (set == sets - 1);
            List<Candidate> kept = plans.get(set);
            for (int pass = 0; pass < 3 && kept.isEmpty(); pass++) {
               for (int i = 0; i < n; i++) {
                  if ((set & (1 << i)) == 0)
                     continue;
                  int rest = set & ~(1 << i);
                  TablePlanner tp = tps.get(i);
                  for (Candidate r : plans.get(rest)) {
                     if (pass == 0 && r.hasProduct)
                        continue;
                     List<Plan> joins = (pass == 2)
                           ? Collections.singletonList(tp.makeProductPlan(r.plan))
                           : tp.joinCandidates(r.plan);
                     for (Plan p : joins) {
                        costed++;
                        String order = r.order + " -> " + tables.get(i)
                              + " (" + joinMethod(p) + ")";
                        boolean product = r.hasProduct || pass == 2;
                        if (full)
                           // the whole query: include the group by and sort
                           // costs, and keep only the cheapest plan
                           keep(kept, new Candidate(p,
                                 PlanCost.totalCost(finishPlan(p, data, tx), tx),
                                 0, order, product));
                        else
                           keep(kept, new Candidate(p, PlanCost.totalCost(p, tx),
                                 p.recordsOutput(), order, product));
                     }
                  }
               }
            }
         }
      }
      finally {
         TablePlanner.verbose = verbose;
      }

      Candidate chosen = plans.get(sets - 1).get(0);
      Plan plan = finishPlan(chosen.plan, data, tx);

      if (n > 1) {
         TablePlanner.verbose = false;
         Plan greedy;
         try {
            greedy = new HeuristicQueryPlanner(mdm).createPlan(data, tx);
         }
         finally {
            TablePlanner.verbose = verbose;
         }
         System.out.println("dp optimizer: costed " + costed + " candidate plans for "
               + n + " tables");
         System.out.println("  chosen join order: " + chosen.order);
         System.out.println("  est. cost " + PlanCost.totalCost(plan, tx)
               + " block accesses (greedy heuristic plan: "
               + PlanCost.totalCost(greedy, tx) + ")");
      }
      return plan;
   }

   /**
    * Adds a candidate to the plans kept for a set of tables,
    * unless a kept plan is at least as good in both cost and
    * output size; kept plans that the candidate beats in both
    * are removed.
    * <p>
    * Keeping only the cheapest plan is not enough: SimpleDB's
    * estimate of a join's output size depends on how the join is
    * done, and a slightly more expensive plan with a smaller output
    * can make every later join (and the group by and sort) cheaper.
    */
   private static void keep(List<Candidate> kept, Candidate c) {
      for (Candidate k : kept)
         if (k.cost <= c.cost && k.records <= c.records)
            return;
      kept.removeIf(k -> c.cost <= k.cost && c.records <= k.records);
      kept.add(c);
   }

   /** A plan for a set of tables, with what the optimizer knows about it. */
   private static class Candidate {
      final Plan plan;
      final long cost;          // estimated total block accesses
      final int records;        // estimated output size
      final String order;       // the join order, for printing
      final boolean hasProduct; // whether the plan uses a cartesian product

      Candidate(Plan plan, long cost, int records, String order, boolean hasProduct) {
         this.plan = plan;
         this.cost = cost;
         this.records = records;
         this.order = order;
         this.hasProduct = hasProduct;
      }
   }

   /**
    * Adds the steps that follow the joins, in the same
    * order as the other query planners.
    */
   private Plan finishPlan(Plan p, QueryData data, Transaction tx) {
      if (data.hasGroupBy())
         p = new GroupByPlan(tx, p, data.groupFields(), data.aggregates());
      if (data.hasOrderBy())
         p = new SortPlan(tx, p, data.sortFields(), data.sortDirections());
      p = new ProjectPlan(p, data.fields());
      if (data.isDistinct())
         p = new DistinctPlan(p);
      return p;
   }

   /**
    * Returns the name of the join method used at the top of p,
    * looking through any selections added on top of the join.
    */
   private static String joinMethod(Plan p) {
      while (p instanceof SelectPlan)
         p = p.children()[0];
      if (p instanceof IndexJoinPlan)
         return "indexjoin";
      if (p instanceof HashJoinPlan)
         return "hashjoin";
      if (p instanceof MergeJoinPlan)
         return "mergejoin";
      if (p instanceof NestedLoopJoinPlan)
         return "nestedloopjoin";
      return "product";
   }
}
