package experiment;

import java.io.File;
import java.util.Random;
import simpledb.plan.Planner;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Creates the database "expdb" used for the join experiments.
 * It has the same tables as studentdb, but with many more rows,
 * so that the join algorithms take measurably different times.
 *
 * The data is random but generated from a fixed seed, so running
 * this program again (after deleting expdb) gives the same data.
 *
 * Like StudentMajorNoJDBC, it uses the SimpleDB classes directly,
 * so that the inserts can be committed in batches instead of
 * once per insert, which is much faster.
 */
public class CreateExperimentDB {
   static final String DBNAME = "expdb";

   // table sizes
   static final int DEPTS    = 20;
   static final int STUDENTS = 2000;
   static final int COURSES  = 100;
   static final int SECTIONS = 400;
   static final int ENROLLS  = 8000;

   static final int BATCH = 500; // inserts per transaction

   public static void main(String[] args) {
      if (new File(DBNAME).exists()) {
         System.out.println("Database " + DBNAME + " already exists; "
               + "delete the " + DBNAME + " folder first to recreate it.");
         return;
      }
      SimpleDB db = new SimpleDB(DBNAME);
      Planner planner = db.planner();
      Random rand = new Random(3223);
      long start = System.currentTimeMillis();

      Transaction tx = db.newTx();
      // the indexes are created before the inserts,
      // since SimpleDB only indexes rows inserted after the index exists
      String[] ddl = {
         "create table dept(did int, dname varchar(10))",
         "create table student(sid int, sname varchar(10), majorid int, gradyear int)",
         "create table course(cid int, title varchar(20), deptid int)",
         "create table section(sectid int, courseid int, prof varchar(10), yearoffered int)",
         "create table enroll(eid int, studentid int, sectionid int, grade varchar(2))",
         "create index sididx on student(sid) using btree",
         "create index studentididx on enroll(studentid) using btree",
         "create index sectididx on section(sectid) using btree",
         "create index cididx on course(cid) using btree" };
      for (String cmd : ddl)
         planner.executeUpdate(cmd, tx);
      tx.commit();
      System.out.println("Tables and indexes created.");

      Inserter ins = new Inserter(db);
      for (int did = 1; did <= DEPTS; did++)
         ins.insert("insert into dept(did, dname) values (" + did + ", 'dept" + did + "')");
      ins.done("dept", DEPTS);

      for (int sid = 1; sid <= STUDENTS; sid++)
         ins.insert("insert into student(sid, sname, majorid, gradyear) values (" + sid
               + ", 'stu" + sid + "', " + (1 + rand.nextInt(DEPTS))
               + ", " + (2019 + rand.nextInt(8)) + ")");
      ins.done("student", STUDENTS);

      for (int cid = 1; cid <= COURSES; cid++)
         ins.insert("insert into course(cid, title, deptid) values (" + cid
               + ", 'course" + cid + "', " + (1 + rand.nextInt(DEPTS)) + ")");
      ins.done("course", COURSES);

      for (int sectid = 1; sectid <= SECTIONS; sectid++)
         ins.insert("insert into section(sectid, courseid, prof, yearoffered) values (" + sectid
               + ", " + (1 + rand.nextInt(COURSES)) + ", 'prof" + (1 + rand.nextInt(50))
               + "', " + (2019 + rand.nextInt(8)) + ")");
      ins.done("section", SECTIONS);

      String[] grades = { "A", "B", "C", "D", "F" };
      for (int eid = 1; eid <= ENROLLS; eid++)
         ins.insert("insert into enroll(eid, studentid, sectionid, grade) values (" + eid
               + ", " + (1 + rand.nextInt(STUDENTS)) + ", " + (1 + rand.nextInt(SECTIONS))
               + ", '" + grades[rand.nextInt(grades.length)] + "')");
      ins.done("enroll", ENROLLS);

      System.out.println("Database " + DBNAME + " created in "
            + (System.currentTimeMillis() - start) / 1000 + "s.");
   }

   /**
    * Runs inserts, committing every BATCH inserts.
    */
   static class Inserter {
      private SimpleDB db;
      private Transaction tx;
      private int count = 0;

      Inserter(SimpleDB db) {
         this.db = db;
         tx = db.newTx();
      }

      void insert(String cmd) {
         db.planner().executeUpdate(cmd, tx);
         if (++count % BATCH == 0) {
            tx.commit();
            tx = db.newTx();
         }
      }

      void done(String tblname, int n) {
         tx.commit();
         tx = db.newTx();
         System.out.println(n + " " + tblname + " records inserted.");
      }
   }
}
