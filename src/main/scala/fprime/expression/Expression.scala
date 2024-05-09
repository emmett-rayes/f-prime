package fprime.expression

import fprime.expression.Symbol.SymbolParser
import fprime.parser.combinators.*
import fprime.parser.{ParseError, Parser}
import fprime.parsing.{Parsable, Tokens, summonParser}

import scala.reflect.ClassTag
import scala.util.{Failure, NotGiven}

sealed trait Expression

object Expression:
    given UnusedParser[T, E](using NotGiven[T =:= Expression])(using
        tag: ClassTag[T],
        downcast: NotGiven[Conversion[E, T & E]],
        expression: Parsable[E],
    ): Parsable[T] with
        override lazy val parser: Parser[Tokens, T] = input =>
            Failure(
              ParseError(input, s"${tag.name} is not supported for this expression type.")
            )

    given SpecializedParser[T, E](using NotGiven[T =:= Expression])(using
        tag: ClassTag[T],
        downcast: Conversion[E, T & E],
        expression: Parsable[E],
    ): Parsable[T] with
        override lazy val parser: Parser[Tokens, T] = expression.parser.map(identity)

    given ExpressionParser(using
        variable: Parsable[Variable],
        abstraction: Parsable[Abstraction[?, ?]],
        application: Parsable[Application[?, ?]],
        ascription: Parsable[Ascription[?, ?]],
    ): Parsable[Expression] with
        private val parens = this.parser.between(Literal.parser("("), Literal.parser(")"))
        private var pending: Option[(Int, ClassTag[?])] = None
        private var level = 0

        extension [T](self: Parser[Tokens, T])
            private def nonRecur(using tag: ClassTag[T]): Parser[Tokens, T] = (input: Tokens) =>
                /*
                println(
                  "\t" * level + s"pending ${pending.map(_._2.name)} - parsing ${tag.name} on $input"
                )
                 */
                if pending.isDefined && pending.get._1 == input.size && pending.get._2.equals(
                      tag
                    )
                then Failure(ParseError(input, "Left-recursion detected."))
                else
                    val old = pending
                    pending = Some((input.size, tag))
                    level += 1
                    val result = self.parse(input)
                    pending = old.map((size, oldTag) =>
                        (result.map(_._1.size).getOrElse(size), oldTag)
                    )
                    level -= 1
                    result

        override lazy val parser: Parser[Tokens, Expression] =
            abstraction.parser.nonRecur
                .orElse(application.parser.nonRecur)
                .orElse(ascription.parser.nonRecur)
                .orElse(variable.parser.nonRecur)
                .orElse(parens.nonRecur)

case class Variable(symbol: Symbol, index: Int = -1) extends Expression

object Variable:
    given VariableParser: Parsable[Variable] with
        override lazy val parser: Parser[Tokens, Variable] =
            summonParser[Symbol].map(s => Variable(s))

case class Abstraction[P <: Expression, B <: Expression](parameter: P, body: B)
    extends Expression

object Abstraction:
    given AbstractionParser[P <: Expression, B <: Expression](using
        parameter: Parsable[P],
        body: Parsable[B],
        downcast: Conversion[Abstraction[P, B], B & Abstraction[P, B]], // shape preserving
    ): Parsable[Abstraction[P, B]] with

        private val parameters = parameter.parser.atLeast(1).thenSkip(Literal.parser("."))
        private val lambda =
            Literal.parser("λ") `orElse` Literal.parser("@") `orElse` Literal.parser("\\")

        override lazy val parser: Parser[Tokens, Abstraction[P, B]] =
            lambda
                .skipThen(parameters)
                .andThen(body.parser)
                .map((parameters, body) =>
                    parameters
                        .foldRight(body)((parameter: P, body: B) =>
                            Abstraction(parameter, body): B
                        )
                        .asInstanceOf[Abstraction[P, B]]
                    // downcast safety: cannot fail because we've upcasted in the last folding step
                )

case class Application[C <: Expression, A <: Expression](callable: C, argument: A)
    extends Expression

object Application:
    given ApplicationParser[C <: Expression, A <: Expression](using
        callable: Parsable[C],
        argument: Parsable[A],
        downcast: Conversion[Application[C, A], C & Application[C, A]], // shape preserving
    ): Parsable[Application[C, A]] with
        private val parens = this.parser.between(Literal.parser("("), Literal.parser(")"))

        override lazy val parser: Parser[Tokens, Application[C, A]] =
            callable.parser
                .andThen(argument.parser.atLeast(1))
                .map((head, tail) =>
                    tail.foldLeft(head)((callable, argument) =>
                        Application(callable, argument): C
                    ).asInstanceOf[Application[C, A]]
                )

case class Ascription[E <: Expression, T <: Expression](expression: E, `type`: T)
    extends Expression

object Ascription:
    given AscriptionParser[E <: Expression, T <: Expression](using
        expression: Parsable[E],
        `type`: Parsable[T],
    ): Parsable[Ascription[E, T]] with
        override lazy val parser: Parser[Tokens, Ascription[E, T]] =
            expression.parser
                .thenSkip(Literal.parser(":"))
                .andThen(`type`.parser)
                .map((e, t) => Ascription(e, t))

extension (tag: ClassTag[?])
    private def name: String =
        val string = tag.toString()
        val components = string.split('.')
        if components.isEmpty then tag.toString() else components.last
