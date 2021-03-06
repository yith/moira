package moira.expression

import moira.unit.{SIDim, PhysicalQuantity}

sealed trait BinOpType
object BinOpType {
  case object Add extends BinOpType
  case object Sub extends BinOpType
  case object Mul extends BinOpType
  case object Div extends BinOpType
}

// binary operation
case class BinOp(op: BinOpType, e1: Expr, e2: Expr) extends Expr {
  def bind(bindings: Bindings) = {
    BinOp(op, e1.bind(bindings), e2.bind(bindings))
  }

  def unify(x: String, ys: Seq[String]): Expr = {
    BinOp(op, e1.unify(x, ys), e2.unify(x, ys))
  }

  lazy val vars = e1.vars ++ e2.vars

  lazy val simplified: Expr = {
    val s1 = e1.simplified
    val s2 = e2.simplified
    (s1, s2) match {
      case (Value(pq1), Value(pq2)) => {
        val ans: PhysicalQuantity = op match {
          case BinOpType.Add => pq1 + pq2
          case BinOpType.Sub => pq1 - pq2
          case BinOpType.Mul => pq1 * pq2
          case BinOpType.Div => pq1 / pq2
        }
        Value(ans)
      }
      case _ => BinOp(op, s1, s2)
    }
  }

  def dim(varDims: Map[String, SIDim]) = {
    val d1 = e1.dim(varDims)
    val d2 = e2.dim(varDims)

    op match {
      case BinOpType.Add | BinOpType.Sub => {
        if (d1 == d2) {
          d1
        } else {
          throw new DimensionInconsistencyException(this,
            s"Incompatible dimensions: $d1 and $d2")
        }
      }
      case BinOpType.Mul => d1 * d2
      case BinOpType.Div => d1 / d2
    }
  }
}
