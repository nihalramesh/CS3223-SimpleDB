package simpledb.parse;

import java.util.*;

import simpledb.materialize.*;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 * @author Edward Sciore
 */
public class Parser {
   private Lexer lex;
   
   public Parser(String s) {
      lex = new Lexer(s);
   }
   
// Methods for parsing predicates, terms, expressions, constants, and fields
   
   public String field() {
      return lex.eatId();
   }
   
   public Constant constant() {
      if (lex.matchStringConstant())
         return new Constant(lex.eatStringConstant());
      else
         return new Constant(lex.eatIntConstant());
   }
   
   public Expression expression() {
      if (lex.matchId())
         return new Expression(field());
      else
         return new Expression(constant());
   }
   
   public Term term() {
	   Expression lhs = expression();
	   String operator = lex.eatOpr();
	   Expression rhs = expression();
	   return new Term(lhs, operator, rhs);
	}
   
   public Predicate predicate() {
      Predicate pred = new Predicate(term());
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         pred.conjoinWith(predicate());
      }
      return pred;
   }
   
// Methods for parsing queries
   
   public QueryData query() {
      lex.eatKeyword("select");
      List<String> fields = new ArrayList<>();
      List<AggregationFn> aggregates = new ArrayList<>();
      selectList(fields, aggregates);
      lex.eatKeyword("from");
      Collection<String> tables = tableList();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      List<String> groupFields = new ArrayList<>();
      if (lex.matchKeyword("group")) {
         lex.eatKeyword("group");
         lex.eatKeyword("by");
         groupFields = fieldList();
      }
      List<String> sortFields = new ArrayList<>();
      List<Boolean> sortDirections = new ArrayList<>();

      if (lex.matchKeyword("order")) {
         lex.eatKeyword("order");
         lex.eatKeyword("by");
         orderList(sortFields, sortDirections, aggregates);
      }
      checkGrouping(fields, groupFields, aggregates, sortFields);
      return new QueryData(fields, tables, pred, groupFields, aggregates,
                           sortFields, sortDirections);
   }
   
   private void selectList(List<String> fields, List<AggregationFn> aggregates) {
      fields.add(selectItem(aggregates));
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         selectList(fields, aggregates);
      }
   }
   
   /**
    * Parses either a field name or an aggregate such as
    * count(sid), returning the name of the output field.
    * A new aggregate is added to the list of aggregates.
    */
   private String selectItem(List<AggregationFn> aggregates) {
      AggregationFn fn = aggregate();
      if (fn == null)
         return field();
      for (AggregationFn existing : aggregates)
         if (existing.fieldName().equals(fn.fieldName()))
            return fn.fieldName();
      aggregates.add(fn);
      return fn.fieldName();
   }
   
   private AggregationFn aggregate() {
      String fnname;
      if (lex.matchKeyword("sum"))
         fnname = "sum";
      else if (lex.matchKeyword("count"))
         fnname = "count";
      else if (lex.matchKeyword("avg"))
         fnname = "avg";
      else if (lex.matchKeyword("min"))
         fnname = "min";
      else if (lex.matchKeyword("max"))
         fnname = "max";
      else
         return null;
      lex.eatKeyword(fnname);
      lex.eatDelim('(');
      String fldname = field();
      lex.eatDelim(')');
      switch (fnname) {
         case "sum":   return new SumFn(fldname);
         case "count": return new CountFn(fldname);
         case "avg":   return new AvgFn(fldname);
         case "min":   return new MinFn(fldname);
         default:      return new MaxFn(fldname);
      }
   }
   
   /**
    * In a query with grouping or aggregation, every
    * non-aggregate field in the select and order by
    * clauses must be a group by field.
    */
   private void checkGrouping(List<String> fields, List<String> groupFields,
         List<AggregationFn> aggregates, List<String> sortFields) {
      if (groupFields.isEmpty() && aggregates.isEmpty())
         return;
      List<String> outputs = new ArrayList<>(groupFields);
      for (AggregationFn fn : aggregates)
         outputs.add(fn.fieldName());
      List<String> used = new ArrayList<>(fields);
      used.addAll(sortFields);
      for (String fldname : used)
         if (!outputs.contains(fldname))
            throw new BadSyntaxException(
                  "field " + fldname + " must appear in the group by clause or be aggregated");
   }
   
   private Collection<String> tableList() {
      Collection<String> L = new ArrayList<String>();
      L.add(lex.eatId());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(tableList());
      }
      return L;
   }
   
   private void orderList(List<String> sortFields, List<Boolean> sortDirections,
         List<AggregationFn> aggregates) {
      sortFields.add(selectItem(aggregates));

      boolean asc = true;

      if (lex.matchKeyword("asc")) {
         lex.eatKeyword("asc");
      }
      else if (lex.matchKeyword("desc")) {
         lex.eatKeyword("desc");
         asc = false;
      }

      sortDirections.add(asc);

      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         orderList(sortFields, sortDirections, aggregates);
      }
   }
   
// Methods for parsing the various update commands
   
   public Object updateCmd() {
      if (lex.matchKeyword("insert"))
         return insert();
      else if (lex.matchKeyword("delete"))
         return delete();
      else if (lex.matchKeyword("update"))
         return modify();
      else
         return create();
   }
   
   private Object create() {
      lex.eatKeyword("create");
      if (lex.matchKeyword("table"))
         return createTable();
      else if (lex.matchKeyword("view"))
         return createView();
      else
         return createIndex();
   }
   
// Method for parsing delete commands
   
   public DeleteData delete() {
      lex.eatKeyword("delete");
      lex.eatKeyword("from");
      String tblname = lex.eatId();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new DeleteData(tblname, pred);
   }
   
// Methods for parsing insert commands
   
   public InsertData insert() {
      lex.eatKeyword("insert");
      lex.eatKeyword("into");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      List<String> flds = fieldList();
      lex.eatDelim(')');
      lex.eatKeyword("values");
      lex.eatDelim('(');
      List<Constant> vals = constList();
      lex.eatDelim(')');
      return new InsertData(tblname, flds, vals);
   }
   
   private List<String> fieldList() {
      List<String> L = new ArrayList<String>();
      L.add(field());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(fieldList());
      }
      return L;
   }
   
   private List<Constant> constList() {
      List<Constant> L = new ArrayList<Constant>();
      L.add(constant());
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         L.addAll(constList());
      }
      return L;
   }
   
// Method for parsing modify commands
   
   public ModifyData modify() {
      lex.eatKeyword("update");
      String tblname = lex.eatId();
      lex.eatKeyword("set");
      String fldname = field();
      lex.eatDelim('=');
      Expression newval = expression();
      Predicate pred = new Predicate();
      if (lex.matchKeyword("where")) {
         lex.eatKeyword("where");
         pred = predicate();
      }
      return new ModifyData(tblname, fldname, newval, pred);
   }
   
// Method for parsing create table commands
   
   public CreateTableData createTable() {
      lex.eatKeyword("table");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      Schema sch = fieldDefs();
      lex.eatDelim(')');
      return new CreateTableData(tblname, sch);
   }
   
   private Schema fieldDefs() {
      Schema schema = fieldDef();
      if (lex.matchDelim(',')) {
         lex.eatDelim(',');
         Schema schema2 = fieldDefs();
         schema.addAll(schema2);
      }
      return schema;
   }
   
   private Schema fieldDef() {
      String fldname = field();
      return fieldType(fldname);
   }
   
   private Schema fieldType(String fldname) {
      Schema schema = new Schema();
      if (lex.matchKeyword("int")) {
         lex.eatKeyword("int");
         schema.addIntField(fldname);
      }
      else {
         lex.eatKeyword("varchar");
         lex.eatDelim('(');
         int strLen = lex.eatIntConstant();
         lex.eatDelim(')');
         schema.addStringField(fldname, strLen);
      }
      return schema;
   }
   
// Method for parsing create view commands
   
   public CreateViewData createView() {
      lex.eatKeyword("view");
      String viewname = lex.eatId();
      lex.eatKeyword("as");
      QueryData qd = query();
      return new CreateViewData(viewname, qd);
   }
   
   
//  Method for parsing create index commands
   
   public CreateIndexData createIndex() {
      lex.eatKeyword("index");
      String idxname = lex.eatId();
      lex.eatKeyword("on");
      String tblname = lex.eatId();
      lex.eatDelim('(');
      String fldname = field();
      lex.eatDelim(')');
      // create index name on table(field) [using hash|b-tree], defaulting to hash when omitted.
      String idxtype = "hash";
      if (lex.matchKeyword("using")) {
         lex.eatKeyword("using");
         if (lex.matchKeyword("hash"))
            lex.eatKeyword("hash");
         else {
            lex.eatKeyword("btree");
            idxtype = "btree";
         }
      }
      return new CreateIndexData(idxname, tblname, fldname, idxtype);
   }
}

