package com.lmlt.actor.example.courier.realtime.service
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lmlt.actor.example.courier.realtime.service.message.{Coordinates, CourierDistanceMessage, CourierLocationMessage, CourierRecommendationMessage, GridCoordinates, Place}
import spray.json.DefaultJsonProtocol

object JSONSupport extends SprayJsonSupport with DefaultJsonProtocol {
  // The formats must be defined from bottom up according to the hierarchy of nesting.  Wrong order will result in implicit not found exception.
  implicit val coordinatesFormat = jsonFormat(Coordinates.apply, "longitude", "latitude")
  implicit val placesFormat = jsonFormat(Place.apply, "placeId", "coordinates")
  implicit val courierLocationMessageFormat = jsonFormat(CourierLocationMessage.apply, "courierId", "coordinates", "prevCoordinates")
  implicit val placesMessageFormat = jsonFormat(PlacesMessage, "places")
  implicit val gridCoordinatesFormat = jsonFormat(GridCoordinates.apply, "x", "y")
  implicit val courierDistanceMessageFormat = jsonFormat(CourierDistanceMessage.apply, "courierId", "distance")
  implicit val courierRecommendationMessageFormat = jsonFormat(CourierRecommendationMessage.apply, "couriers", "grids")

}
