package simpledb.query;

import static java.sql.Types.INTEGER;
import static java.sql.Types.VARCHAR;

/**
 * The class that denotes values stored in the database.
 * @author Edward Sciore
 */
public class Constant implements Comparable<Constant> {
   private Integer ival = null;
   private String  sval = null;
   
   public Constant(Integer ival) {
      this.ival = ival;
   }
   
   public Constant(String sval) {
      this.sval = sval;
   }
   
   public int asInt() {
      return ival;
   }
   
   public String asString() {
      return sval;
   }

   /**
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
      return (ival != null) ? ival.equals(c.ival) : sval.equals(c.sval);
   }
   
   public int compareTo(Constant c) {
      return (ival != null) ? ival.compareTo(c.ival) : sval.compareTo(c.sval);
   }
   
   public int hashCode() {
      return (ival != null) ? ival.hashCode() : sval.hashCode();
   }
   
   public String toString() {
      return (ival != null) ? ival.toString() : sval.toString();
   }   
}
