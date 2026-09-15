package simpledb.query;

/**
 * The Scan class for the <i>nestedloopjoin</i> operator.
 * Unlike a {@link ProductScan} wrapped in a separate {@link SelectScan},
 * this scan tests the join predicate directly while iterating, so
 * that no additional selection operator is needed.
 * @author Edward Sciore
 */
public class NestedLoopJoinScan implements Scan {
   private Scan s1, s2;
   private Predicate joinpred;

   /**
    * Create a nestedloopjoin scan for the two underlying scans.
    * @param s1 the LHS (outer) scan
    * @param s2 the RHS (inner) scan
    * @param joinpred the predicate that joins the two scans
    */
   public NestedLoopJoinScan(Scan s1, Scan s2, Predicate joinpred) {
      this.s1 = s1;
      this.s2 = s2;
      this.joinpred = joinpred;
      beforeFirst();
   }

   /**
    * Position the scan before its first record.
    * The outer (LHS) scan is positioned at its first record,
    * and the inner (RHS) scan is positioned before its first record.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      s1.beforeFirst();
      s1.next();
      s2.beforeFirst();
   }

   /**
    * Move to the next record for which the join predicate is satisfied.
    * The inner scan is advanced looking for a match; when it is
    * exhausted it is restarted and the outer scan is advanced instead.
    * If there are no more outer records, the method returns false.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      while (true) {
         while (s2.next()) {
            if (joinpred.isSatisfied(this))
               return true;
         }
         s2.beforeFirst();
         if (!s1.next())
            return false;
      }
   }

   /**
    * Return the integer value of the specified field.
    * The value is obtained from whichever scan contains the field.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      if (s1.hasField(fldname))
         return s1.getInt(fldname);
      else
         return s2.getInt(fldname);
   }

   /**
    * Return the string value of the specified field.
    * The value is obtained from whichever scan contains the field.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      if (s1.hasField(fldname))
         return s1.getString(fldname);
      else
         return s2.getString(fldname);
   }

   /**
    * Return the value of the specified field.
    * The value is obtained from whichever scan contains the field.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (s1.hasField(fldname))
         return s1.getVal(fldname);
      else
         return s2.getVal(fldname);
   }

   /**
    * Returns true if the specified field is in either underlying scan.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   /**
    * Close both underlying scans.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      s1.close();
      s2.close();
   }
}
