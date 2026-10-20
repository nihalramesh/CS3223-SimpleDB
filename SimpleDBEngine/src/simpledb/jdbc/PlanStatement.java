package simpledb.jdbc;

import java.sql.SQLException;

/** Statement extension used by SimpleIJ for displaying query plans. */
public interface PlanStatement {
   String explain(String qry) throws SQLException;
}
