package com.lmlt.actor.example.courier.realtime.service.load.test;

import com.typesafe.config.ConfigFactory
import io.gatling.core.scenario.Simulation
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.asynchttpclient.DefaultAsyncHttpClient

import scala.concurrent.duration._

class RecommendationSimulation extends Simulation {
  val conf = ConfigFactory.load()
  val host = conf.getString("application.courier-realtime.server.elb")

  before {
    println("Simulation start")
    val client = new DefaultAsyncHttpClient()
    client.prepareGet(s"${host}/")
  }

  val httpConf = http
    .baseURL(conf.getString("application.courier-realtime.server.elb")) // 5
    .acceptEncodingHeader("gzip, deflate")

  val scn = scenario("Recommendation")
    .exec(http("recommendation")
    .get("/service/recommendations"))
    .pause(conf.getLong("application.load.req-period-sec") second)

  setUp(
    scn.inject(rampUsers(conf.getInt("application.load.users")).over(conf.getLong("application.load.ramp-period-sec") second))
  ).protocols(httpConf)
}
