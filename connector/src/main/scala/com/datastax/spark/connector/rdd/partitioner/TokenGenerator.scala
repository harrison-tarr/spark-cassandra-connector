package com.datastax.spark.connector.rdd.partitioner

import com.datastax.driver.core.MetadataHook
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.spark.connector.cql.{CassandraConnector, TableDef}
import com.datastax.spark.connector.util.Logging
import com.datastax.spark.connector.util.PatitionKeyTools._
import com.datastax.spark.connector.writer.{BoundStatementBuilder, RowWriter}

/**
  * A utility class for determining the token of a given key. Uses a bound statement to determine
  * the routing key and the uses that with the TokenFactory to determine the hashed Token.
  */
private[connector] class TokenGenerator[T] (
  connector: CassandraConnector,
  tableDef: TableDef,
  rowWriter: RowWriter[T]) extends Serializable with Logging {

  val protocolVersion = connector.withSessionDo { session =>
    session.getContext.getProtocolVersion
  }

  //Makes a PreparedStatement which we use only to generate routing keys on the client
  val stmt = connector.withSessionDo { session => prepareDummyStatement(session, tableDef) }
  val metadata = connector.withSessionDo(_.getMetadata)

  val routingKeyGenerator = new RoutingKeyGenerator(
    tableDef,
    tableDef.partitionKey.map(_.columnName))

  val boundStmtBuilder = new BoundStatementBuilder(
    rowWriter,
    stmt,
    protocolVersion = protocolVersion)

  def getTokenFor(key: T): Token = {
    MetadataHook.newToken(metadata, routingKeyGenerator.apply(boundStmtBuilder.bind(key).stmt))
  }

  def getStringTokenFor(key: T): String = {
    MetadataHook.newTokenAsString(metadata, routingKeyGenerator.apply(boundStmtBuilder.bind(key).stmt))
  }
}
