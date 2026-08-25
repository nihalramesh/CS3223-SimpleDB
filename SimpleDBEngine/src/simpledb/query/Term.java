package simpledb.query;

import simpledb.plan.Plan;
import simpledb.record.*;

/**
 * A term is a comparison between two expressions.
 * 
 * @author Edward Sciore
 */
public class Term {
   private Expression lhs;
   private Expression rhs;
   private String operator;

   /**
    * Creates a new equality term.
    *
    * This constructor is retained for compatibility with existing code.
    *
    * @param lhs the left-hand expression
    * @param rhs the right-hand expression
    */
   public Term(Expression lhs, Expression rhs) {
      this(lhs, "=", rhs);
   }

   /**
    * Creates a new term using the specified comparison operator.
    *
    * @param lhs      the left-hand expression
    * @param operator the comparison operator
    * @param rhs      the right-hand expression
    */
   public Term(Expression lhs, String operator, Expression rhs) {
      this.lhs = lhs;
      this.operator = operator;
      this.rhs = rhs;
   }

   /**
    * Determines whether this term is satisfied by the current record
    * of the specified scan.
    *
    * @param s the scan
    * @return true if the comparison is satisfied
    */
   public boolean isSatisfied(Scan s) {
      Constant lhsval = lhs.evaluate(s);
      Constant rhsval = rhs.evaluate(s);

      int comparison = lhsval.compareTo(rhsval);

      switch (operator) {
         case "=":
            return comparison == 0;

         case "<":
            return comparison < 0;

         case "<=":
            return comparison <= 0;

         case ">":
            return comparison > 0;

         case ">=":
            return comparison >= 0;

         case "!=":
         case "<>":
            return comparison != 0;

         default:
            throw new IllegalArgumentException(
               "Unsupported comparison operator: " + operator
            );
      }
   }

   /**
    * Calculates the extent to which selecting on the term reduces
    * the number of records output by a query.
    *
    * @param p the query's plan
    * @return the estimated reduction factor
    */
   public int reductionFactor(Plan p) {
      /*
       * The original estimation logic applies specifically to equality.
       * For non-equality comparisons, estimate that approximately half
       * of the records satisfy the predicate.
       */
      if (!operator.equals("="))
         return 2;

      String lhsName;
      String rhsName;

      if (lhs.isFieldName() && rhs.isFieldName()) {
         lhsName = lhs.asFieldName();
         rhsName = rhs.asFieldName();

         return Math.max(
            p.distinctValues(lhsName),
            p.distinctValues(rhsName)
         );
      }

      if (lhs.isFieldName()) {
         lhsName = lhs.asFieldName();
         return p.distinctValues(lhsName);
      }

      if (rhs.isFieldName()) {
         rhsName = rhs.asFieldName();
         return p.distinctValues(rhsName);
      }

      // Otherwise, this equality term compares two constants.
      if (lhs.asConstant().equals(rhs.asConstant()))
         return 1;
      else
         return Integer.MAX_VALUE;
   }

   /**
    * Determines whether this term has the form F=c for an equality
    * comparison, where F is the specified field and c is a constant.
    *
    * @param fldname the field name
    * @return the matching constant, or null
    */
   public Constant equatesWithConstant(String fldname) {
      /*
       * A non-equality predicate such as F>10 does not establish
       * that F is equal to 10.
       */
      if (!operator.equals("="))
         return null;

      if (lhs.isFieldName()
            && lhs.asFieldName().equals(fldname)
            && !rhs.isFieldName()) {
         return rhs.asConstant();
      }

      if (rhs.isFieldName()
            && rhs.asFieldName().equals(fldname)
            && !lhs.isFieldName()) {
         return lhs.asConstant();
      }

      return null;
   }

   /**
    * Determines whether this term has the form F1=F2, where F1 is
    * the specified field and F2 is another field.
    *
    * @param fldname the field name
    * @return the other field's name, or null
    */
   public String equatesWithField(String fldname) {
      /*
       * A comparison such as F1<F2 does not establish that the two
       * fields are equal.
       */
      if (!operator.equals("="))
         return null;

      if (lhs.isFieldName()
            && lhs.asFieldName().equals(fldname)
            && rhs.isFieldName()) {
         return rhs.asFieldName();
      }

      if (rhs.isFieldName()
            && rhs.asFieldName().equals(fldname)
            && lhs.isFieldName()) {
         return lhs.asFieldName();
      }

      return null;
   }

   /**
    * Determines whether both expressions apply to the specified schema.
    *
    * @param sch the schema
    * @return true if both expressions apply to the schema
    */
   public boolean appliesTo(Schema sch) {
      return lhs.appliesTo(sch) && rhs.appliesTo(sch);
   }

   /**
    * Returns the textual representation of this term.
    */
   @Override
   public String toString() {
      return lhs.toString() + operator + rhs.toString();
   }
}