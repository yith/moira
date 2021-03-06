package moira.expression

import moira.unit.CommonUnits
import moira.unit.PhysicalQuantity
import moira.unit.SIUnit

import scala.util.parsing.combinator._
import moira.expression.function.{Integrate,Pow,UnaryMathFuncall}

/*
 * expr ::= term { "+" term | "-" term }.
 * term ::= power { "*" power | "/" power }.
 * power ::= factor { "^" factor }.
 * factor ::= floatingPointNumber | funcall | variable | "(" expr ")".
 * variable ::= ident [ unit ].
 * funcall ::= ident "(" args ")".
 * args ::= [ expr { "," expr } ].
 */
object Parser extends JavaTokenParsers {

  lazy val expr: Parser[Expr] = term~rep("+"~term | "-"~term) ^^ {
    case t~ts => ts.foldLeft(t) { (e, t) =>
      t match {
        case "+"~t => BinOp(BinOpType.Add, e, t)
        case "-"~t => BinOp(BinOpType.Sub, e, t)
      }
    }
  }

  lazy val term: Parser[Expr] = factor~rep("*"~factor | "/"~factor) ^^ {
    case p~ps => ps.foldLeft(p) { (e, p) =>
      p match {
        case "*"~p => BinOp(BinOpType.Mul, e, p)
        case "/"~p => BinOp(BinOpType.Div, e, p)
      }
    }
  }

  lazy val factor: Parser[Expr] = (floatingPointNumber~opt(unit) ^^ {
    case s~None => Value(PhysicalQuantity(s.toDouble))
    case s~Some(u) => Value(PhysicalQuantity(s.toDouble, u))
  } // value
  | funcall
  | "$"~>ident ^^ (Var(_))  // variable
  | "("~>expr<~")"
  )

  lazy val unit: Parser[SIUnit] = (CommonUnits.nameToUnit.toList.sortBy {
    kv => -kv._1.length // sort by length in descending order to avoid ambiguity
  } map {
    kv => { kv._1 ^^ (_ => kv._2) }
  }).reduce(_ | _)

  lazy val funcall: Parser[Expr] = (ident<~"(")~repsep(expr, ",")<~")" ^^ {
    case name~as => {
      import moira.expression.function.UnaryMathFuncType._

      name match {
        case "pow" if as.size == 2 => Pow(as(0), as(1))
        case "int" if as.size == 4 => {
          as(1) match {
            case v@Var(_) => Integrate(as(0), v, as(2), as(3))
          }
        }
        case "sin"   if as.size == 1 => UnaryMathFuncall(Sin, as(0))
        case "cos"   if as.size == 1 => UnaryMathFuncall(Cos, as(0))
        case "tan"   if as.size == 1 => UnaryMathFuncall(Tan, as(0))
        case "sinh"  if as.size == 1 => UnaryMathFuncall(Sinh, as(0))
        case "cosh"  if as.size == 1 => UnaryMathFuncall(Cosh, as(0))
        case "tanh"  if as.size == 1 => UnaryMathFuncall(Tanh, as(0))
        case "exp"   if as.size == 1 => UnaryMathFuncall(Exp, as(0))
        case "log"   if as.size == 1 => UnaryMathFuncall(Log, as(0))
        case "abs"   if as.size == 1 => UnaryMathFuncall(Abs, as(0))
        case "floor" if as.size == 1 => UnaryMathFuncall(Floor, as(0))
        case "ceil"  if as.size == 1 => UnaryMathFuncall(Ceil, as(0))
      }
    }
  }

  lazy val rel: Parser[Rel] = equality | inequality

  lazy val equality: Parser[Rel] = expr~"="~expr ^^ {
    case lhs~"="~rhs => Rel(RelType.Eq, lhs, rhs)
  }

  lazy val inequality: Parser[Rel] = (
      expr~">"~expr ^^ { case lhs~">"~rhs => Rel(RelType.Gt, lhs, rhs) }
    | expr~"<"~expr ^^ { case lhs~"<"~rhs => Rel(RelType.Lt, lhs, rhs)}
    | expr~">="~expr ^^ { case lhs~">="~rhs => Rel(RelType.GtEq, lhs, rhs) }
    | expr~"<="~expr ^^ { case lhs~"<="~rhs => Rel(RelType.LtEq, lhs, rhs)}
    )

  def parseExpr(str: String): Option[Expr] = {
    parseAll(expr, str) match {
      case Success(result, _) => Some(result)
      case _ => None
    }
  }

  def parseRel(str: String): Option[Rel] = {
    parseAll(rel, str) match {
      case Success(result, _) => Some(result)
      case _ => None
    }
  }
}
