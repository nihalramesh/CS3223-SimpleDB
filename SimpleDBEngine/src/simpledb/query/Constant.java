package simpledb.query;

import static java.sql.Types.INTEGER;
import static java.sql.Types.VARCHAR;

/**
 * The class that denotes values stored in the database.
 * <p>
 * Integers and doubles are both numeric, and are compared by
 * value: 2 equals 2.0, and 2 is less than 2.5. This lets an
 * integer constant be compared with a double field, and an
 * integer value be stored in a double field.
 * @author Edward Sciore
 */
public class Constant implements Comparable<Constant> {
   private Integer ival = null;
   private String  sval = null;
   private Double  dval = null;

   public Constant(Integer ival) {
      this.ival = ival;
   }

   public Constant(String sval) {
      this.sval = sval;
   }

   public Constant(Double dval) {
      this.dval = dval;
   }

   public int asInt() {
      return ival;
   }

   public String asString() {
      return sval;
   }

   /**
    * Returns the value as a double.
    * An integer value is converted to a double.
    */
   public double asDouble() {
      return (dval != null) ? dval : ival;
   }

   private boolean isNumeric() {
      return ival != null || dval != null;
    * Returns the SQL type of the constant,
    * as defined in java.sql.Types.
    * @return INTEGER or VARCHAR
    */
   public int type() {
      return (ival != null) ? INTEGER : VARCHAR;
   }

   public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass())
         return false;
      Constant c = (Constant) obj;
      if (dval != null || c.dval != null)
         return isNumeric() && c.isNumeric() && asDouble() == c.asDouble();
      return (ival != null) ? ival.equals(c.ival) : sval.equals(c.sval);
   }

   public int compareTo(Constant c) {
      if (dval != null || c.dval != null)
         return Double.compare(asDouble(), c.asDouble());
      return (ival != null) ? ival.compareTo(c.ival) : sval.compareTo(c.sval);
   }

   /**
    * A double with a whole-number value has the same hash code
    * as the equal integer, so that equal constants always hash
    * to the same index bucket or hash join partition.
    */
   public int hashCode() {
      if (dval != null) {
         double d = dval;
         if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
            return Integer.hashCode((int) d);
         return dval.hashCode();
      }
      return (ival != null) ? ival.hashCode() : sval.hashCode();
   }

   public String toString() {
      if (dval != null)
         return dval.toString();
      return (ival != null) ? ival.toString() : sval.toString();
   }
}
