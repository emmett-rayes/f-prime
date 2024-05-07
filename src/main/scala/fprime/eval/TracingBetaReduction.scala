package fprime.eval

import fprime.expression.Expression

import scala.collection.mutable.ArrayBuffer

trait TracingBetaReduction:
    this: BetaReduction =>

    def trace[E <: Expression & R, R <: Expression](
        expression: E,
        normalize: Boolean = false,
    ): List[R] =
        val trace = ArrayBuffer[R]()
        var current = expression
        while true do
            trace.append(current)
            reduceOnce(current, normalize) match
                case None          => return trace.toList
                case Some(reduced) => current = reduced
        throw RuntimeException("unreachable!")
