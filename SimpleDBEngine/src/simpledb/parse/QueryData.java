package simpledb.parse;

import java.util.*;

import simpledb.materialize.AggregationFn;
import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 * @author Edward Sciore
 */
public class QueryData {
   private List<String> fields;
   private Collection<String> tables;
   private Predicate pred;
   private List<String> groupFields;
   private List<AggregationFn> aggregates;
   private List<String> sortFields;
   private List<Boolean> sortDirections; // true = asc, false = desc
   private boolean distinct;

   /**
    * Saves the field and table list, predicate,
    * grouping and aggregation information, and sort order.
    * The field list contains output field names, so an
    * aggregate such as count(sid) appears as "countofsid".
    */
   public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
         List<String> groupFields, List<AggregationFn> aggregates,
         List<String> sortFields, List<Boolean> sortDirections, boolean distinct) {
      this.fields = fields;
      this.tables = tables;
      this.pred = pred;
      this.groupFields = groupFields;
      this.aggregates = aggregates;
      this.sortFields = sortFields;
      this.sortDirections = sortDirections;
      this.distinct = distinct;
   }

   /**
    * Returns the fields mentioned in the select clause.
    * @return a list of field names
    */
   public List<String> fields() {
      return fields;
   }

   /**
    * Returns the tables mentioned in the from clause.
    * @return a collection of table names
    */
   public Collection<String> tables() {
      return tables;
   }

   /**
    * Returns the predicate that describes which
    * records should be in the output table.
    * @return the query predicate
    */
   public Predicate pred() {
      return pred;
   }

   /**
    * Returns true if the query needs a groupby operator,
    * i.e. it has a group by clause or uses an aggregate.
    */
   public boolean hasGroupBy() {
      return !groupFields.isEmpty() || !aggregates.isEmpty();
   }

   public List<String> groupFields() {
      return groupFields;
   }

   /**
    * Returns the aggregation functions mentioned in the
    * select and order by clauses, without duplicates.
    */
   public List<AggregationFn> aggregates() {
      return aggregates;
   }

   public boolean hasOrderBy() {
      return sortFields.size() > 0;
   }

   public List<String> sortFields() {
      return sortFields;
   }

   public List<Boolean> sortDirections() {
      return sortDirections;
   }

   public boolean isDistinct() {
      return distinct;
   }

   public String toString() {
      String result = "select " + (distinct ? "distinct " : "");
      for (String fldname : fields)
         result += sqlName(fldname) + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      result += " from ";
      for (String tblname : tables)
         result += tblname + ", ";
      result = result.substring(0, result.length()-2); //remove final comma
      String predstring = pred.toString();
      if (!predstring.equals(""))
         result += " where " + predstring;
      if (!groupFields.isEmpty())
         result += " group by " + String.join(", ", groupFields);
      if (hasOrderBy()) {
         result += " order by ";
         for (int i = 0; i < sortFields.size(); i++)
            result += sqlName(sortFields.get(i))
                  + (sortDirections.get(i) ? " asc" : " desc") + ", ";
         result = result.substring(0, result.length()-2); //remove final comma
      }
      return result;
   }

   // Maps an aggregate's output field name (e.g. "countofsid")
   // back to its SQL form (e.g. "count(sid)").
   private String sqlName(String fldname) {
      for (AggregationFn fn : aggregates)
         if (fn.fieldName().equals(fldname))
            return fn.toString();
      return fldname;
   }
}
