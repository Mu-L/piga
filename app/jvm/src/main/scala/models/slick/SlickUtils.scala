package models.slick

import java.time.{ZoneId, LocalDateTime => JavaLocalDateTime}

import common.time.{LocalDateTime, LocalDateTimes}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.higherKinds

object SlickUtils {

  // ********** db helpers ********** //
  val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("db.default.slick")
  val dbApi = dbConfig.profile.api

  import dbApi._

  val database = Database.forConfig("db.default")

  def dbRun[T](query: DBIO[T]): T = {
    Await.result(database.run(query), Duration.Inf)
  }

  def dbRun[T, C[T]](query: Query[_, T, C]): C[T] = dbRun(query.result)

  // ********** datetime helpers ********** //
  implicit val localDateTimeToSqlDateMapper: ColumnType[LocalDateTime] = {
    val zone = ZoneId.of("Europe/Paris") // This is arbitrary. It just has to be the same in both directions
    def toSql(localDateTime: LocalDateTime) = {
      val javaDate = JavaLocalDateTime.of(localDateTime.toLocalDate, localDateTime.toLocalTime)
      val instant = javaDate.atZone(zone).toInstant
      java.sql.Timestamp.from(instant)
    }
    def toLocalDateTime(sqlTimestamp: java.sql.Timestamp) = {
      val javaDate = sqlTimestamp.toInstant.atZone(zone).toLocalDateTime
      LocalDateTimes.ofJavaLocalDateTime(javaDate)
    }
    MappedColumnType.base[LocalDateTime, java.sql.Timestamp](toSql, toLocalDateTime)
  }
}
