package com.ibogomolov
package service

import com.ibogomolov.model.NetworkMap
import com.vividsolutions.jts.geom.Coordinate
import org.wololo.geojson.{FeatureCollection, GeoJSONFactory, LineString}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object NetworkService {

  private var nextId = 1
  private val edgesBuffer = new ListBuffer[(Int, Int)]()
  private val nodesMap = mutable.Map[Int, Coordinate]()
  private val nodesMapReverse = mutable.Map[Coordinate, Int]()

  def loadFromGeojsonFile(path: String): NetworkMap = {
    clear()

    val source = scala.io.Source.fromFile(path)
    val json = source.getLines.mkString
    source.close()
    val featureCollection = GeoJSONFactory.create(json).asInstanceOf[FeatureCollection]

    featureCollection.getFeatures.foreach(f => {
      val bidirectional = f.getProperties.get("bidir").asInstanceOf[Int]
      f.getGeometry.getType match {
        case "LineString" => {
          val line = f.getGeometry.asInstanceOf[LineString].getCoordinates
          var startId = getId(line(0))
          for (i <- 1 until line.length) {
            val endId = getId(line(i))
            edgesBuffer += ((startId, endId))
            if (bidirectional == 1) {
              edgesBuffer += ((endId, startId))
            }
            startId = endId
          }
        }
      }
    })

    NetworkMap(nodesMap.toMap, edgesBuffer.toList)
  }

  def getId(ar: Array[Double]): Int = {
    val coordinate = new Coordinate(ar(0), ar(1))
    if (nodesMapReverse.contains(coordinate)) {
      nodesMapReverse(coordinate)
    } else {
      val id = nextId
      nextId += 1
      nodesMap += id -> coordinate
      nodesMapReverse += coordinate -> id
      id
    }
  }

  private def clear(): Unit = {
    nextId = 1
    edgesBuffer.clear()
    nodesMap.clear()
    nodesMapReverse.clear()
  }

}
