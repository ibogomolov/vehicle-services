package com.ibogomolov
package model

import com.vividsolutions.jts.geom.Coordinate

/**
 * A network map encode the reperesentation of the road network together with point of interests.
 **/
case class NetworkMap(
  // Nodes with their coordinate.
  // Int        - Node identifier.
  // Coordinate - Location of the node.
  nodes: Map[Int, Coordinate],
  // Directed edges connectiong nodes.
  // Int    - Source station identitifer
  // Int    - Target station identitifer
  edges: List[(Int, Int)]
)

/**
 * A topological map encode an abstract representation of the transport network.
 *
 * Only stations, segments (connections between stations) and vehicles are displayed.
 *
 * Segment size is relative (fixed or proportional) and does not reflect the scale of the real world network.
 *
 * Example: https://en.wikipedia.org/wiki/Topological_map
**/
case class TopologicalMap(
                           // Stations with their name.
                           // Int    - Station Identifier
                           // String - Station Name
                           stations: Map[Int, String],
                           // Directed segments connecting stations.
                           // Int    - Source station identitifer
                           // Int    - Target station identitifer
                           // Double - Relative size (between 0.0 and 1.0)
                           segments: List[(Int, Int, Double)],
                           // Vehicles with their name and positions.
                           // (Int, Int) - Segment identifier (source, target) where the vehicle is currently located
                           // String     - Name of the vehicle
                           // Double     - Position of the vehicle relative to this segment (between 0.0 and 1.0)
                           vehicles: List[((Int, Int), String, Double)]
)

/**
 * A vehicle message encode all information sent periodically by a running vehicle.
 **/
case class VehicleMessage(
  name: String,
  coordinate: Coordinate
)
