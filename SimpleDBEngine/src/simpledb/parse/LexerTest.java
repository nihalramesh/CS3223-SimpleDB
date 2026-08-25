package simpledb.parse;

import java.util.Scanner;

/**
 * Tests SQL expressions of the form:
 *
 * expression operator expression
 *
 * Supported expressions are identifiers, integers, and strings.
 */
public class LexerTest {

   public static void main(String[] args) {
      Scanner sc = new Scanner(System.in);

      System.out.println("Enter comparisons, or enter exit to stop:");

      while (sc.hasNextLine()) {
         String input = sc.nextLine();

         if (input.equalsIgnoreCase("exit"))
            break;

         try {
            Lexer lex = new Lexer(input);

            String lhs = readExpression(lex);
            String operator = lex.eatOpr();
            String rhs = readExpression(lex);

            System.out.println(
               "Successfully read: "
               + lhs + " " + operator + " " + rhs
            );
         }
         catch (BadSyntaxException e) {
            System.out.println("Invalid expression: " + input);
         }
      }

      sc.close();
   }

   private static String readExpression(Lexer lex) {
      if (lex.matchId())
         return lex.eatId();

      if (lex.matchIntConstant())
         return Integer.toString(lex.eatIntConstant());

      if (lex.matchStringConstant())
         return "'" + lex.eatStringConstant() + "'";

      throw new BadSyntaxException();
   }
}