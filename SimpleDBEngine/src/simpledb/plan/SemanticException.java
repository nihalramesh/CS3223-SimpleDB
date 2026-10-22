package simpledb.plan;

/**
 * A runtime exception indicating that an SQL statement is
 * syntactically valid but does not make sense against the
 * database catalog, e.g. it refers to a table that does
 * not exist or compares values of different types.
 */
@SuppressWarnings("serial")
public class SemanticException extends RuntimeException {
   public SemanticException(String message) {
      super(message);
   }
}
