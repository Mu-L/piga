package common.testing

import java.time.Instant
import java.time.Month.JANUARY

import common.time.{Clock, LocalDateTime}

final class FakeClock extends Clock {

  @volatile private var currentTime: LocalDateTime = FakeClock.defaultTime

  override def now = currentTime
  override def nowInstant = Instant.ofEpochMilli(192803921)

  def setTime(time: LocalDateTime) = {
    currentTime = time
  }
}

object FakeClock {
  val defaultTime: LocalDateTime = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
}
