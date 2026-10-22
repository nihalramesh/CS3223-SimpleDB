package experiment;

import java.util.*;
import simpledb.opt.JoinMethods;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Runs the join experiments on the database created by
 * CreateExperimentDB.
 *
 * Experiment 1: a 2-table join, run once with each join algorithm.
 * Experiment 2: a 4-table join, run with different plans, obtained
 *               by restricting the join methods the planner may use.
 *
 * For each run it prints the plan chosen by the planner and the
 * time taken to execute the query (opening the scan and reading
 * every output record). Each query is run RUNS times, and the
 * first run is reported separately since it also includes
 * reading the blocks from disk into the buffers for the first time.
 *
 * Usage: JoinExperiment [1|2]   (no argument runs both)
 */
public class JoinExperiment {
   static final int RUNS = 3;

   static final String QUERY1 =
         "select sname, grade from student, enroll where sid = studentid";

   static final String QUERY2 =
         "select sname, grade, prof, title from student, enroll, section, course "
         + "where sid = studentid and sectionid = sectid and courseid = cid";

   public static void main(String[] args) {
      SimpleDB db = new SimpleDB(CreateExperimentDB.DBNAME);
      String which = (args.length > 0) ? args[0] : "all";
      try {
         if (!which.equals("2"))
            runExperiment(db, "Experiment 1: 2-table join", QUERY1);
         if (!which.equals("1"))
            runExperiment(db, "Experiment 2: 4-table join", QUERY2);
      }
      finally {
         JoinMethods.allowAll();
      }
   }

   private static void runExperiment(SimpleDB db, String title, String qry) {
      System.out.println("==================================================");
      System.out.println(title);
      System.out.println(qry);
      System.out.println("==================================================");
      List<String> summary = new ArrayList<>();
      for (String method : JoinMethods.ALL) {
         JoinMethods.allowOnly(method);
         summary.add(runQuery(db, qry, method));
      }
      JoinMethods.allowAll();

      System.out.println();
      System.out.println("Summary for " + title);
      System.out.println(String.format("%-16s %8s %10s %12s", "join method", "rows", "first run", "average ms"));
      for (String line : summary)
         System.out.println(line);
      System.out.println();
   }

   /**
    * Plans the query with only the specified join method allowed,
    * prints the plan, and times RUNS executions of it.
    * Returns one line for the summary table.
    */
   private static String runQuery(SimpleDB db, String qry, String method) {
      System.out.println();
      System.out.println("--- allowed join methods: " + JoinMethods.describe() + " ---");
      Transaction tx = db.newTx();
      Plan p = db.planner().createQueryPlan(qry, tx);
      System.out.print(PlanPrinter.format(p));

      int rows = 0;
      long[] times = new long[RUNS];
      for (int i = 0; i < RUNS; i++) {
         long start = System.nanoTime();
         Scan s = p.open();
         rows = 0;
         while (s.next())
            rows++;
         s.close();
         times[i] = (System.nanoTime() - start) / 1_000_000;
         System.out.println("run " + (i + 1) + ": " + rows + " rows in " + times[i] + " ms");
      }
      tx.commit();

      long total = 0;
      for (int i = 1; i < RUNS; i++)
         total += times[i];
      long avg = (RUNS > 1) ? total / (RUNS - 1) : times[0];
      System.out.println("average of runs 2-" + RUNS + ": " + avg + " ms");
      return String.format("%-16s %8d %10d %12d", method, rows, times[0], avg);
   }
}
