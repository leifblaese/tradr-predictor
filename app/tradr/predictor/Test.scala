package tradr.predictor

import java.io.File
import java.net.InetSocketAddress

import com.datastax.oss.driver.api.core.metadata.Node
import com.typesafe.config.ConfigFactory
import tradr.predictor.models.a3c.A3CPredictor

object Test extends App {

  def mappingfun = {
    (inetSocketAddress: InetSocketAddress) => {
    val cassandraIp = conf.getString("cassandra.ip")
    val cassandraPort = conf.getString("cassandra.connectorPort").toInt
    println(s"Connecting to cassandra at $cassandraIp:$cassandraPort")
    InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)
    }
  }

  val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
  val cassandra = A3CPredictor.getCassandraCluster(conf)
  val data = A3CPredictor.getCassandraData(10L, conf)

  println(data)


//  val cassandraIp = conf.getString("cassandra.ip")
//  val cassandraPort = conf.getString("cassandra.connectorPort").toInt
//  println(s"Connecting to cassandra at $cassandraIp:$cassandraPort")
//  val inetSocketAddress = InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)
//  val nodeInet = cassandra.getMetadata.getNodes.entrySet().toArray.head
//  val defaultInetSocketAddress = InetSocketAddress.createUnresolved("0.0.0.0", 9042)
//  val node = cassandra.getMetadata.getNodes.get(defaultInetSocketAddress)

//  println(Option(node).isDefined)
//  println(node.getCassandraVersion)
//  println(node.getConnectAddress)

//  println(cassandra.getName)
//  cassandra.getMetadata.getNodes.put(inetSocketAddress)
//  println(cassandra.getMetadata.getNodes)

//
//val session = cassandra.connect()
//    session.execute("USE KEYSPACE tradr")
//    session.close()


}
