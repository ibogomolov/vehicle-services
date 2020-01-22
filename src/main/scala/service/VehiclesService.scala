package com.ibogomolov
package service

import scala.math._
import akka.actor.ActorSystem
import com.vividsolutions.jts.geom.{GeometryFactory, LineString, Coordinate}

import model.{NetworkMap, VehicleMessage}

class VehiclesService(network: NetworkMap, vehicles: Int)(implicit AS: ActorSystem) {
  import VehiclesService._

  private val rng = new scala.util.Random(0)

  private val names: Array[String] = ('a' to 'z').take(vehicles).map(_.toString).toArray

  private val locations: Array[NetworkLocation] = (0 to vehicles).map { _ =>
    val edge = network.edges(rng.nextInt(network.edges.size))
    NetworkLocation(edge, 0.0)
  }.toArray


  // Hacky simulation system.
  (new Thread {
    override def run {
      val geometryFactory = new GeometryFactory()
      while (true) {
        Thread.sleep(250)
        locations.synchronized {
          (0 until vehicles).foreach { i =>
            val NetworkLocation((source, target), pos0) = locations(i)

            val distance = (1 + rng.nextInt(8)).toDouble / 100
            val pos1 = min(pos0 + distance, 1.0)

            if (pos1 >= 1.0) {
              val edges = network.edges.filter { case (src, _) => src == target }
              val edge = edges(rng.nextInt(edges.size))
              locations.update(i, NetworkLocation(edge, 0.0))
            } else {
              locations.update(i, NetworkLocation((source, target), pos1))
            }

            val coordinate = {
              val sourceCoord = network.nodes(source)
              val targetCoord = network.nodes(target)

              val dx = sourceCoord.x - targetCoord.x
              val dy = sourceCoord.y - targetCoord.y
              val length = sqrt(dx*dx + dy*dy) * pos1

              def round(d: Double) = rint(d * 100) / 100

              val theta = atan2(targetCoord.y - sourceCoord.y, targetCoord.x - sourceCoord.x)

              new Coordinate(
                round(sourceCoord.x + (cos(theta) * length)),
                round(sourceCoord.y + (sin(theta) * length))
              )
            }

            AS.eventStream.publish(VehicleMessage(names(i), coordinate))
          }
        }
      }
    }
  }).start
}

object VehiclesService {
  case class NetworkLocation(edge: (Int, Int), position: Double)

}
