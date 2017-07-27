package kamon

import kamon.util.MeasurementUnit
import kamon.util.MeasurementUnit.time
import kamon.util.MeasurementUnit.information

package object datadog {

  def readTimeUnit(unit: String): MeasurementUnit = unit match {
    case "s"    => time.seconds
    case "ms"   => time.milliseconds
    case "µs"   => time.microseconds
    case "ns"   => time.nanoseconds
    case other  => sys.error(s"Invalid time unit setting [$other], the possible values are [s, ms, µs, ns]")
  }

  def readInformationUnit(unit: String): MeasurementUnit = unit match {
    case "b"    => information.bytes
    case "kb"   => information.kilobytes
    case "mb"   => information.megabytes
    case "gb"   => information.gigabytes
    case other  => sys.error(s"Invalid information unit setting [$other], the possible values are [b, kb, mb, gb]")
  }

}
