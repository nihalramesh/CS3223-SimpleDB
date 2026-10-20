package simpledb.jdbc.network;

import java.sql.*;
import simpledb.jdbc.StatementAdapter;
import simpledb.jdbc.PlanStatement;

/**
 * An adapter class that wraps RemoteStatement.
 * Its methods do nothing except transform RemoteExceptions
 * into SQLExceptions.
 * @author Edward Sciore
 */
public class NetworkStatement extends StatementAdapter implements PlanStatement {
   private RemoteStatement rstmt;

   public NetworkStatement(RemoteStatement s) {
      rstmt = s;
   }

   public ResultSet executeQuery(String qry) throws SQLException {
      try {
         RemoteResultSet rrs = rstmt.executeQuery(qry);
         return new NetworkResultSet(rrs);
      }
      catch(Exception e) {
         throw new SQLException(e);
      }
   }

   public int executeUpdate(String cmd) throws SQLException {
      try {
         return rstmt.executeUpdate(cmd);
      }
      catch(Exception e) {
         throw new SQLException(e);
      }
   }

   public String explain(String qry) throws SQLException {
      try {
         return rstmt.explain(qry);
      }
      catch(Exception e) {
         throw new SQLException(e);
      }
   }

   public void close() throws SQLException {
      try {
         rstmt.close();
      }
      catch(Exception e) {
         throw new SQLException(e);
      }
   }
}

