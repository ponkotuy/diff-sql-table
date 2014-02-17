package com.geishatokyo.diffsql.parser

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.geishatokyo.diffsql.ast._
import com.geishatokyo.diffsql.ast.DataType
import com.geishatokyo.diffsql.ast.Table
import com.geishatokyo.diffsql.ast.Column
import com.geishatokyo.diffsql.mysql.MySQLParser
import com.geishatokyo.diffsql.ast.ColumnOption._

/**
 * Created by takeshita on 14/02/17.
 */
class TableParserTest extends FlatSpec with ShouldMatchers {

  val parser = new MySQLParser()


  "TableParser" should "parse create table sql generated by mysql command 'show create table'" in {
    val t = parser.parseSql(mysqlGenerated)(0).asInstanceOf[Table]

    assert(t.name === "friendLink","Fail to parse name")
    assert(t.fields === List(
      Column("id",DataType("BIGINT",List(20)),List(NotNull,AutoIncrement)),
      Column("ownerId",DataType("BIGINT",List(20)),List(NotNull)),
      Column("friendId",DataType("BIGINT",List(20)),List(NotNull)),
      Column("status",DataType("INT",List(11)),List(NotNull)),
      Column("friendship",DataType("INT",List(11)),List(NotNull)),
      Column("updated",DataType("DateTime",Nil),List(NotNull)),
      Key.PrimaryKey(List("id"),None,None)
    ),"Fail to parse columns and indexes")

    assert(t.options === List(
      TableOption.Engine("InnoDB"),
      TableOption.AutoIncrement(3),
      TableOption.Charset("utf8")
    ))
  }


  val mysqlGenerated = """
CREATE TABLE `friendlink` (
`id` bigint(20) NOT NULL AUTO_INCREMENT,
`ownerId` bigint(20) NOT NULL,
`friendId` bigint(20) NOT NULL,
`status` int(11) NOT NULL,
`friendship` int(11) NOT NULL,
`updated` datetime NOT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8"""


  "TableParser" should "parse create table sql generated by squeryl" in {
    val t = parser.parseSql(squerylGenerated)(0).asInstanceOf[Table]


    assert(t.name === "PushInformation","Fail to parse name")
    assert(t.fields === List(
      Column("endDate",DataType("DateTime",Nil),List(NotNull)),
      Column("id",DataType("BIGINT",Nil),List(PrimaryKey,NotNull)),
      Column("message",DataType("VARCHAR",List(128)),List(NotNull)),
      Column("pushed",DataType("BOOLEAN",Nil),List(Default(false),NotNull)),
      Column("beginDate",DataType("DateTime",Nil),List(NotNull))
    ),"Fail to parse columns and indexes")

    assert(t.options === Nil)
  }

  val squerylGenerated = """create table PushInformation (
     endDate datetime not null,
     id bigint primary key not null,
     message varchar(128) not null,
     pushed boolean default false not null,
     beginDate datetime not null
 );"""

}