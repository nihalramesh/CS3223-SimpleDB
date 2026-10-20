package simpledb.plan;

import simpledb.materialize.SortPlan;

/** Formats a query plan as an indented operator tree. */
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

      result.append(plan.description()).append("\n");

      for (Plan child : plan.children())
         append(child, result, depth + 1, plan);
   }
}
