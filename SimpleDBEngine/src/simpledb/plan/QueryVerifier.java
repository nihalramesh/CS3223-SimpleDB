package simpledb.plan;

import static java.sql.Types.INTEGER;
import java.util.*;
import simpledb.tx.Transaction;
import simpledb.materialize.AggregationFn;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.Schema;

/**
 * Checks that a parsed SQL statement makes sense against the
 * catalog before it is planned or executed.
 * Without these checks, a bad statement either crashes deep inside
 * the planner (e.g. a NullPointerException), silently returns wrong
 * results (a predicate on an unknown field is never applied), or
 * corrupts the database (a string longer than its field overwrites
 * the next record).
 * Every problem is reported by throwing a {@link SemanticException}
 * with a message that names the offending table, field or value.
 */
public class QueryVerifier {
   private MetadataMgr mdm;

   public QueryVerifier(MetadataMgr mdm) {
      this.mdm = mdm;
   }

// Queries

   /**
    * Verifies a query:
    * every table or view in the from clause exists and is listed once;
    * every field in the select, where, group by and order by clauses
    * exists in exactly one of those tables;
    * aggregated fields exist and have a suitable type; and
    * both sides of every comparison have the same type.
    * @param data the parsed query
    * @param tx the calling transaction
    * @return the schema of the query's output records
    */
   public Schema verifyQuery(QueryData data, Transaction tx) {
      // Collect the fields of all tables in the from clause.
      // A field name found in more than one table is ambiguous,
      // since SimpleDB has no way to qualify it with a table name.
      Schema source = new Schema();
      Set<String> ambiguous = new HashSet<>();
      Set<String> seen = new HashSet<>();
      for (String tblname : data.tables()) {
         if (!seen.add(tblname))
            throw new SemanticException("table " + tblname
                  + " appears more than once in the from clause");
         Schema sch = schemaOf(tblname, tx);
         for (String fldname : sch.fields()) {
            if (source.hasField(fldname))
               ambiguous.add(fldname);
            else
               source.add(fldname, sch);
         }
      }
      Scope scope = new Scope(source, ambiguous, String.join(", ", data.tables()));

      checkPredicate(data.pred(), scope);

      for (AggregationFn fn : data.aggregates()) {
         scope.check(fn.aggregatedFieldName());
         try {
            // rejects e.g. sum or avg of a varchar field
            fn.addField(new Schema(), source);
         }
         catch (RuntimeException e) {
            throw new SemanticException(e.getMessage());
         }
      }

      for (String fldname : data.groupFields())
         scope.check(fldname);

      Schema output = new Schema();
      for (String fldname : data.fields()) {
         AggregationFn fn = aggregateNamed(data, fldname);
         if (fn != null)
            fn.addField(output, source);
         else {
            scope.check(fldname);
            output.add(fldname, source);
         }
      }

      for (String fldname : data.sortFields())
         if (aggregateNamed(data, fldname) == null)
            scope.check(fldname);

      return output;
   }

// Updates

   /**
    * Verifies an insert, delete, modify or create statement.
    * @param data the parsed statement, as returned by Parser.updateCmd()
    * @param tx the calling transaction
    */
   public void verifyUpdate(Object data, Transaction tx) {
      if (data instanceof InsertData)
         verifyInsert((InsertData) data, tx);
      else if (data instanceof DeleteData)
         verifyDelete((DeleteData) data, tx);
      else if (data instanceof ModifyData)
         verifyModify((ModifyData) data, tx);
      else if (data instanceof CreateTableData)
         verifyCreateTable((CreateTableData) data, tx);
      else if (data instanceof CreateViewData)
         verifyCreateView((CreateViewData) data, tx);
      else if (data instanceof CreateIndexData)
         verifyCreateIndex((CreateIndexData) data, tx);
   }

   /**
    * The table must exist, the field and value lists must have the
    * same length, each field must exist and be listed once, and each
    * value must have the field's type and fit in the field.
    */
   private void verifyInsert(InsertData data, Transaction tx) {
      String tblname = data.tableName();
      Schema sch = tableSchema(tblname, tx);
      List<String> flds = data.fields();
      List<Constant> vals = data.vals();
      if (flds.size() != vals.size())
         throw new SemanticException("insert into " + tblname + " lists "
               + flds.size() + " fields but " + vals.size() + " values");
      Scope scope = new Scope(sch, Collections.emptySet(), tblname);
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < flds.size(); i++) {
         String fldname = flds.get(i);
         if (!seen.add(fldname))
            throw new SemanticException("field " + fldname + " is listed more than once");
         scope.check(fldname);
         checkAssignment(fldname, sch, vals.get(i));
      }
   }

   /**
    * The table must exist and the predicate must be valid for it.
    */
   private void verifyDelete(DeleteData data, Transaction tx) {
      String tblname = data.tableName();
      Schema sch = tableSchema(tblname, tx);
      checkPredicate(data.pred(), new Scope(sch, Collections.emptySet(), tblname));
   }

   /**
    * The table and target field must exist, the new value must have
    * the target field's type (and fit in it, if it is a constant),
    * and the predicate must be valid for the table.
    */
   private void verifyModify(ModifyData data, Transaction tx) {
      String tblname = data.tableName();
      Schema sch = tableSchema(tblname, tx);
      Scope scope = new Scope(sch, Collections.emptySet(), tblname);
      String target = data.targetField();
      scope.check(target);
      Expression newval = data.newValue();
      if (newval.isFieldName()) {
         int type = scope.typeOf(newval);
         if (type != sch.type(target))
            throw new SemanticException("cannot set " + target + " (" + typeName(sch.type(target))
                  + ") to " + newval + " (" + typeName(type) + ")");
      }
      else
         checkAssignment(target, sch, newval.asConstant());
      checkPredicate(data.pred(), scope);
   }

   /**
    * The name must be new and short enough for the catalog,
    * and the field names must be distinct and short enough.
    */
   private void verifyCreateTable(CreateTableData data, Transaction tx) {
      String tblname = data.tableName();
      checkNewName("table", tblname, tx);
      Set<String> seen = new HashSet<>();
      for (String fldname : data.newSchema().fields()) {
         checkNameLength("field", fldname);
         if (!seen.add(fldname))
            throw new SemanticException("field " + fldname + " is defined more than once");
      }
   }

   /**
    * The name must be new and short enough for the catalog,
    * the definition must fit in the catalog, and the
    * defining query must itself be valid.
    */
   private void verifyCreateView(CreateViewData data, Transaction tx) {
      String viewname = data.viewName();
      checkNewName("view", viewname, tx);
      String viewdef = data.viewDef();
      if (viewdef.length() > MetadataMgr.MAX_VIEWDEF)
         throw new SemanticException("definition of view " + viewname + " is "
               + viewdef.length() + " characters long; the maximum is " + MetadataMgr.MAX_VIEWDEF);
      verifyQuery(new Parser(viewdef).query(), tx);
   }

   /**
    * The index name must be short enough for the catalog, the table
    * and field must exist, and the field must not already be indexed.
    */
   private void verifyCreateIndex(CreateIndexData data, Transaction tx) {
      checkNameLength("index", data.indexName());
      String tblname = data.tableName();
      String fldname = data.fieldName();
      Schema sch = tableSchema(tblname, tx);
      new Scope(sch, Collections.emptySet(), tblname).check(fldname);
      if (mdm.getIndexInfo(tblname, tx).containsKey(fldname))
         throw new SemanticException("field " + fldname + " of " + tblname + " is already indexed");
   }

// Helpers

   /**
    * Returns the schema of the named table or view.
    * A view's schema is the output schema of its (verified) definition.
    */
   private Schema schemaOf(String name, Transaction tx) {
      String viewdef = mdm.getViewDef(name, tx);
      if (viewdef != null)
         return verifyQuery(new Parser(viewdef).query(), tx);
      return tableSchema(name, tx);
   }

   /**
    * Returns the schema of the named stored table.
    * The catalog returns an empty schema for an unknown table.
    */
   private Schema tableSchema(String tblname, Transaction tx) {
      Schema sch = mdm.getLayout(tblname, tx).schema();
      if (sch.fields().isEmpty()) {
         if (mdm.getViewDef(tblname, tx) != null)
            throw new SemanticException(tblname + " is a view; only tables can be modified or indexed");
         throw new SemanticException("table " + tblname + " does not exist");
      }
      return sch;
   }

   private void checkNewName(String kind, String name, Transaction tx) {
      checkNameLength(kind, name);
      if (!mdm.getLayout(name, tx).schema().fields().isEmpty())
         throw new SemanticException("table " + name + " already exists");
      if (mdm.getViewDef(name, tx) != null)
         throw new SemanticException("view " + name + " already exists");
   }

   private void checkNameLength(String kind, String name) {
      if (name.length() > MetadataMgr.MAX_NAME)
         throw new SemanticException(kind + " name " + name + " is longer than "
               + MetadataMgr.MAX_NAME + " characters");
   }

   /**
    * Both sides of every term must refer to valid fields
    * and have the same type.
    */
   private void checkPredicate(Predicate pred, Scope scope) {
      for (Term t : pred.terms()) {
         int ltype = scope.typeOf(t.lhs());
         int rtype = scope.typeOf(t.rhs());
         if (ltype != rtype)
            throw new SemanticException("cannot compare " + show(t.lhs()) + " (" + typeName(ltype)
                  + ") with " + show(t.rhs()) + " (" + typeName(rtype) + ")");
      }
   }

   /**
    * A constant stored in a field must have the field's type,
    * and a string must not be longer than the field allows.
    */
   private void checkAssignment(String fldname, Schema sch, Constant val) {
      int type = sch.type(fldname);
      if (val.type() != type)
         throw new SemanticException("cannot store " + show(new Expression(val)) + " (" + typeName(val.type())
               + ") in field " + fldname + " (" + typeName(type) + ")");
      if (type != INTEGER && val.asString().length() > sch.length(fldname))
         throw new SemanticException("value '" + val + "' is too long for field " + fldname
               + " (varchar(" + sch.length(fldname) + "))");
   }

   /**
    * Returns the aggregate whose output field has the specified name,
    * or null if the name is not an aggregate (e.g. "countofsid").
    */
   private AggregationFn aggregateNamed(QueryData data, String fldname) {
      for (AggregationFn fn : data.aggregates())
         if (fn.fieldName().equals(fldname))
            return fn;
      return null;
   }

   private static String typeName(int type) {
      return (type == INTEGER) ? "int" : "varchar";
   }

   /**
    * Shows an expression as it would be written in SQL,
    * so string constants appear in quotes.
    */
   private static String show(Expression e) {
      if (!e.isFieldName() && e.asConstant().type() != INTEGER)
         return "'" + e.asConstant() + "'";
      return e.toString();
   }

   /**
    * The fields that a statement can refer to.
    */
   private static class Scope {
      private Schema sch;
      private Set<String> ambiguous;
      private String tables;

      Scope(Schema sch, Set<String> ambiguous, String tables) {
         this.sch = sch;
         this.ambiguous = ambiguous;
         this.tables = tables;
      }

      /**
       * Throws an exception unless the field exists
       * in exactly one table of the scope.
       */
      void check(String fldname) {
         if (ambiguous.contains(fldname))
            throw new SemanticException("field " + fldname
                  + " is ambiguous: it appears in more than one of " + tables);
         if (!sch.hasField(fldname))
            throw new SemanticException("field " + fldname + " does not exist in " + tables);
      }

      /**
       * Returns the type of the expression, checking that
       * it is a valid field if it is not a constant.
       */
      int typeOf(Expression e) {
         if (e.isFieldName()) {
            check(e.asFieldName());
            return sch.type(e.asFieldName());
         }
         return e.asConstant().type();
      }
   }
}
