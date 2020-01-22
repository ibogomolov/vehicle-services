package com.ibogomolov

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.ibogomolov.model.{NetworkMap, TopologicalMap, VehicleMessage}
import com.ibogomolov.service.{NetworkService, VehiclesService}
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, LineString}

import scala.collection.mutable


object WebServer {
  implicit val AS = ActorSystem("system")
  implicit val AM = ActorMaterializer()

  var network = NetworkService.loadFromGeojsonFile("src/main/resources/lc-track0-21781.geojson")

  var networkSimple = NetworkMap(
    nodes = Map(
      (1, new Coordinate(0, 0)),
      (2, new Coordinate(10, 0)),
      (3, new Coordinate(0, 10)),
      (4, new Coordinate(10, 10))
    ),
    edges = List(
      (1, 3),
      (3, 4),
      (4, 2),
      (2, 1)
    )
  )

  val vehicles = new VehiclesService(network, 1)

  private val gf = new GeometryFactory()
  val stations = initStations()
  private val segmentsWithLines = initSegmentsWithLines()
  val segments = initSegments()
  private val vehiclesCoordinatesMap: mutable.Map[String, Coordinate] = mutable.HashMap()
  private val vehiclesDataMap: mutable.Map[String, (LineString, Double, (Int, Int))] = mutable.HashMap()

  private def initStations(): Map[Int, String] = {
    val nodes = network.nodes
    val stationNames = ('A' to 'Z').take(nodes.size).map(_.toString)
    nodes.keySet.toList.zip(stationNames).toMap
  }

  private def initSegmentsWithLines(): List[((Int, Int), LineString)] = {
    val NetworkMap(nodes, edges) = network
    val edgeLines = edges.map { case (src, tgt) => gf.createLineString(Array(nodes(src), nodes(tgt))) }
    edges.zip(edgeLines)
  }

  private def initSegments(): List[(Int, Int, Double)] = {
    val maxSegmentLength = segmentsWithLines.map { case (_, line) => line.getLength }.max
    segmentsWithLines.map { case ((src, tgt), ls) => (src, tgt, ls.getLength / maxSegmentLength) }
  }

  private def generateVehiclesList(): List[((Int, Int), String, Double)] = {
    vehiclesCoordinatesMap.map {
      case (name, coordinate) =>
        val vehiclePoint = gf.createPoint(coordinate)

        if (!vehiclesDataMap.contains(name)) {
          // init the segment, position may be incorrect

          val (vehicleSegment, vehicleLine) = segmentsWithLines.minBy {
            case (_, line) => vehiclePoint.distance(line)
          }
          val position = vehiclePoint.distance(vehicleLine.getStartPoint) / vehicleLine.getLength
          vehiclesDataMap += name -> (vehicleLine, position, vehicleSegment)
          (vehicleSegment, name, position)
        } else {
          // main part, vehicle goes on the segment

          val (currVehicleLine, prevPosition, currVehicleSegment) = vehiclesDataMap(name)
          if (vehiclePoint.distance(currVehicleLine) < 0.01) {
            // vehicle goes on the segment

            val currPosition = vehiclePoint.distance(currVehicleLine.getStartPoint) / currVehicleLine.getLength
            if (currPosition < prevPosition) {
              // wrong reversed segment

              val (reverseVehicleSegment, reverseVehicleLine) = segmentsWithLines.find { case (_, line) =>
                line.getStartPoint.equalsExact(currVehicleLine.getEndPoint) &&
                  line.getEndPoint.equalsExact(currVehicleLine.getStartPoint)
              }.get
              val newPosition = vehiclePoint.distance(reverseVehicleLine.getStartPoint) / reverseVehicleLine.getLength
              vehiclesDataMap += name -> (reverseVehicleLine, newPosition, reverseVehicleSegment)
              (reverseVehicleSegment, name, newPosition)
            } else {
              // all good, progressing towards target station

              vehiclesDataMap += name -> (currVehicleLine, currPosition, currVehicleSegment)
              (currVehicleSegment, name, currPosition)
            }
          } else {
            // target station reached, change to next segment

            val (nextVehicleSegment, nextVehicleLine) = segmentsWithLines.filter {
              case (_, line) => line.getStartPoint.equalsExact(currVehicleLine.getEndPoint)
            }.minBy {
              case (_, line) => vehiclePoint.distance(line)
            }
            val position = vehiclePoint.distance(nextVehicleLine.getStartPoint) / nextVehicleLine.getLength
            vehiclesDataMap += name -> (nextVehicleLine, position, nextVehicleSegment)
            (nextVehicleSegment, name, position)
          }
        }
    }.toList
  }

  class Consumer extends Actor {
    AS.eventStream.subscribe(context.self, classOf[VehicleMessage])

    def receive = {
      case VehicleMessage(name, coordinate) =>
        vehiclesCoordinatesMap += name -> coordinate
        println(name, coordinate)
        wsActor ! CoordinatesUpdateMessage()
    }
  }

  case class CoordinatesUpdateMessage()

  val consumer = AS.actorOf(Props[Consumer])

  val (wsActor, wsSource) = Source.actorRef[CoordinatesUpdateMessage](0, OverflowStrategy.dropBuffer).preMaterialize()

  def main(args: Array[String]) {
    val route =
      path("health") {
        get {
          complete {
            "WebServer is up!"
          }
        }
      } ~
        path("topology") {
          get {
            complete {
              TopologicalMap(stations, segments, generateVehiclesList()).toString
            }
          }
        } ~
        path("location") {
          get {
            handleWebSocketMessages(wsLocationFlow)
          }
        }

    Http().bindAndHandle(route, "localhost", 8080)
  }

  val wsLocationFlow: Flow[Message, Message, _] =
    Flow.fromSinkAndSource(
      Sink.ignore,
      wsSource.map(_ =>
        TextMessage(generateVehiclesList().map {
          case (seg, name, pos) => f"Vehicle $name is at $pos%.2f of segment $seg"
        }.mkString("; "))
      )
    )

}
