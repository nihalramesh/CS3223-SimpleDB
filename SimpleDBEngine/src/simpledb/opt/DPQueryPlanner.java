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
 * <li>for each set of 2, 3, ..., n tables, finds the cheapest
 *     left-deep plan by trying each table of the set as the last
 *     (inner) table, joined to the cheapest plan already found for
 *     the remaining tables, with every applicable join method
 *     (index, hash, sort-merge and nested-loops join);
 * <li>postpones cartesian products, using one only where no
 *     join predicate can connect the tables.
 * </ol>
 * Plans are compared by their estimated total block accesses
 * (see {@link PlanCost}). This considers every left-deep join order
 * in O(n * 2^n) steps instead of n! by keeping only the cheapest plan
 * for each set of tables.
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

      int sets = 1 << n;              // bit i of a set means tables.get(i) is in it
      Plan[] best = new Plan[sets];   // cheapest plan for each set of tables
      long[] cost = new long[sets];
      String[] order = new String[sets];
      boolean[] hasProduct = new boolean[sets]; // whether best[set] uses a product
      int costed = 0;

      boolean verbose = TablePlanner.verbose;
      TablePlanner.verbose = false;
      try {
         List<TablePlanner> tps = new ArrayList<>();
         for (String tblname : tables)
            tps.add(new TablePlanner(tblname, data.pred(), tx, mdm));

         // Step 1: the cheapest plan for each single table
         for (int i = 0; i < n; i++) {
            int set = 1 << i;
            best[set] = tps.get(i).makeSelectPlan();
            cost[set] = PlanCost.totalCost(best[set], tx);
            order[set] = tables.get(i);
            costed++;
         }

         // Step 2: the cheapest plan for each larger set of tables.
         // Every proper subset of a set is numerically smaller than it,
         // so the plans for all subsets are known when a set is reached.
         // Cartesian products are postponed, as in System R: a set is
         // planned in up to three passes, stopping at the first that
         // finds a plan:
         //   pass 0: join a table to the plan of a product-free subset
         //   pass 1: join a table to the plan of any subset
         //   pass 2: take the product of a table and the plan of a subset
         // A product's cost estimate can be misleadingly low (it assumes
         // the inner input fits in memory), so a product is used only
         // where no join predicate can connect the tables.
         for (int set = 1; set < sets; set++) {
            if (Integer.bitCount(set) < 2)
               continue;
            for (int pass = 0; pass < 3 && best[set] == null; pass++) {
               for (int i = 0; i < n; i++) {
                  if ((set & (1 << i)) == 0)
                     continue;
                  int rest = set & ~(1 << i);
                  if (pass == 0 && hasProduct[rest])
                     continue;
                  TablePlanner tp = tps.get(i);
                  List<Plan> candidates = (pass == 2)
                        ? Collections.singletonList(tp.makeProductPlan(best[rest]))
                        : tp.joinCandidates(best[rest]);
                  for (Plan p : candidates) {
                     costed++;
                     long c = PlanCost.totalCost(p, tx);
                     if (best[set] == null || c < cost[set]) {
                        best[set] = p;
                        cost[set] = c;
                        hasProduct[set] = hasProduct[rest] || pass == 2;
                        order[set] = order[rest] + " -> " + tables.get(i)
                              + " (" + joinMethod(p) + ")";
                     }
                  }
               }
            }
         }
      }
      finally {
         TablePlanner.verbose = verbose;
      }

      Plan plan = finishPlan(best[sets - 1], data, tx);

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
         System.out.println("  chosen join order: " + order[sets - 1]);
         System.out.println("  est. cost " + PlanCost.totalCost(plan, tx)
               + " block accesses (greedy heuristic plan: "
               + PlanCost.totalCost(greedy, tx) + ")");
      }
      return plan;
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
