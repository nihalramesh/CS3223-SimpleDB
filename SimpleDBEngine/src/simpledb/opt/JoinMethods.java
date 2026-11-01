package simpledb.opt;

import java.util.*;

/**
 * The join methods the query planner is allowed to use.
 * By default all of them are allowed. Restricting them makes the
 * planner choose a different plan for the same query, which is
 * used to compare join algorithms in the experiments.
 * If none of the allowed methods can be used for a join (e.g. only
 * indexjoin is allowed but the join field has no index), the
 * planner falls back to a nested loop join.
 */
public class JoinMethods {
   public static final String INDEX  = "indexjoin";
   public static final String HASH   = "hashjoin";
   public static final String MERGE  = "mergejoin";
   public static final String NESTED = "nestedloopjoin";
   public static final List<String> ALL = Arrays.asList(INDEX, HASH, MERGE, NESTED);

   private static Set<String> allowed = new HashSet<>(ALL);

   /**
    * Allows only the specified join methods.
    * @param methods the names of the allowed methods, e.g. "hashjoin"
    */
   public static void allowOnly(String... methods) {
      Set<String> s = new HashSet<>();
      for (String m : methods) {
         if (!ALL.contains(m))
            throw new IllegalArgumentException("unknown join method " + m
                  + "; expected one of " + ALL);
         s.add(m);
      }
      allowed = s;
   }

   /**
    * Allows every join method again.
    */
   public static void allowAll() {
      allowed = new HashSet<>(ALL);
   }

   public static boolean isAllowed(String method) {
      return allowed.contains(method);
   }

   /**
    * Returns the allowed methods, in a fixed order.
    */
   public static String describe() {
      List<String> names = new ArrayList<>();
      for (String m : ALL)
         if (allowed.contains(m))
            names.add(m);
      return String.join(", ", names);
   }
}
