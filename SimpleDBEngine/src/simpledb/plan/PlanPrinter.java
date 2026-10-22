package simpledb.plan;

import simpledb.materialize.SortPlan;

/**
 * Formats a query plan as an indented operator tree.
 * Each node is annotated with the planner's estimates for
 * that subtree: blocks accessed and records output.
 */
public class PlanPrinter {
   private PlanPrinter() {
   }

   public static String format(Plan plan) {
      StringBuilder result = new StringBuilder();
      append(plan, result, 0, null);
      return result.toString();
   }

   private static void append(Plan plan, StringBuilder result, int depth, Plan parent) {
      if (plan instanceof SortPlan && !(parent instanceof ProjectPlan)) {
         for (Plan child : plan.children())
            append(child, result, depth, parent);
         return;
      }

      for (int i = 0; i < depth; i++)
         result.append("  ");

      result.append(plan.description())
            .append("  [blocks=").append(plan.blocksAccessed())
            .append(", records=").append(plan.recordsOutput())
            .append("]\n");

      for (Plan child : plan.children())
         append(child, result, depth + 1, plan);
   }
}
