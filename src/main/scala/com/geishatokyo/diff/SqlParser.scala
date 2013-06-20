package com.geishatokyo.diff

import PartialFunction._

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

import scala.util.Try

import scala.collection.immutable.ListSet

sealed trait Definition
case class Column(name: String, dataType: DataType, options: Set[String]) extends Definition {
  override def toString = s"""$name $dataType ${options.mkString(" ")}"""
  override def equals(a: Any) = cond(this -> a) {
    case (a: Column, b: Column) =>
      a.name == b.name && a.dataType == b.dataType
  }
}
case class Primary(names: List[String]) extends Definition
case class IndexKey(name: Option[String], columns: List[String]) extends Definition

case class TableOption(key: String, value: String) {
  override def toString = s"$key=$value"
}

object Definition {
  def columns(defs: List[Definition]) = {
    val primary = defs.collect {
      case p: Primary => p.names
    }.flatten.toSet
    defs collect {
      case c: Column => c
    } map {
      case c if primary(c.name) => c.copy(options = c.options + "PRIMARY KEY")
      case c => c
    }
  }
}

case class Table(name: String, columns: Set[Column], options: Set[TableOption]) {
  def alter(table: Table) = {
    val add = table.columns diff columns
    val drop = columns diff table.columns
    val opts = table.options diff options
    if (add.isEmpty && drop.isEmpty && opts.isEmpty)
      throw new RuntimeException("no difference")
    else
      s"ALTER TABLE $name " +
        (add.map("ADD " +).toList :::
          drop.map("DROP " +).toList :::
          opts.toList).mkString(",")
  }
}

trait SqlParser extends RegexParsers with DataTypes { self =>

  case class CaseInsensitive(string: String) {
    def re = ("(?i)" + string).r
    def regex = self.regex(re)
  }

  implicit def i(s: String) = CaseInsensitive(s)

  case class Concat(parser: Parser[String]) {
    def ~~(value: Parser[String]) = parser ~ value ^^ {
      case a ~ b =>  s"$a $b"
    }
  }

  implicit def regex2concat(r: Regex) = Concat(r)

  implicit def parser2concat(p: Parser[String]) = Concat(p)

  def opts(parser: Parser[String]) = opt(parser) ^^ (_ getOrElse "")

  def appl[A](p: Parser[A]) = """\(""".r ~> p <~ """\)""".r

  def sum[A](list: List[Parser[A]]) = list.reduce(_ | _)

  val symbol = """[\w`]+""".r

  val string = """'[^']+'""".r

  val binary = "0".r | "1".r

  val tableOptions: List[(Parser[String], Regex)] =
    List(
      "ENGINE".regex -> symbol,
      opts("DEFAULT".re) ~~ ("CHARACTER".re ~~ "SET".re | "CHARSET".re) -> symbol
    )

  val tableOption =
    sum(tableOptions. map {
      case (key, value) => key ~ opt("=") ~ value ^^ {
        case key ~ equal ~ value => TableOption(key.toUpperCase, value)
      }
    })

  val columnOptions: List[Parser[String]] =
    List(
      """NOT\s+NULL""".re,
      "NULL".re,
      """DEFAULT""".re ~~ string,
      "AUTO_INCREMENT".re,
      "UNIQUE".re ~~ opts("KEY".re),
      opts("PRIMARY".re) ~~ "KEY".re
    )

  val columnDefinition =
    symbol ~ dataType ~ rep(sum(columnOptions)) ^^ {
      case name ~ typ ~ opts => Column(name.toLowerCase, typ, ListSet(opts:_*))
    }

  val createDinition =
    columnDefinition |
    """PRIMARY\s+KEY""".re ~ "(" ~> repsep(symbol, ",".r) <~ ")" ^^ Primary.apply | 
    ("KEY".re | "INDEX".re) ~> (opt(symbol) <~ "(") ~ repsep(symbol, ",".r) <~ ")" ^^ {
      case name ~ cols => IndexKey(name, cols)
    }

  val createTable = "CREATE".re ~ "TABLE".re ~ opt("""IF\s+NOT\s+EXISTS""".re) ~> symbol

  val createTableStatement = createTable ~ appl(repsep(createDinition, ",".r)) ~ rep(tableOption) <~ opt(";".r) ^^ {
    case name ~ defs ~ opts =>
      Table(name, ListSet(Definition.columns(defs): _*), ListSet(opts: _*))
  }

  def parseSql(s: String) = Try(parseAll(createTableStatement, s).get)

  def diff(before: String, after: String) = for {
    x <- parseSql(before)
    y <- parseSql(after)
    z <- Try(x alter y)
  } yield z

  def diffOp(a: String, b: String) = diff(b, a)

}

object SqlParser extends SqlParser
