package common.time

import java.time.{Instant, LocalDate, LocalTime, ZoneId}

import com.google.inject._
import models.accounting.config.Config

final class JvmClock @Inject()(accountingConfig: Config) extends Clock {
  val zone = ZoneId.of(accountingConfig.constants.zoneId)

  override def now = {
    val date = LocalDate.now(zone)
    val time = LocalTime.now(zone)
    LocalDateTime.of(date, time)
  }

  override def nowInstant = Instant.now()
}
