package tradr.predictor.models.a3c

import java.net.{InetAddress, InetSocketAddress, Socket}

import com.datastax.oss.driver.api.core.{Cluster, CqlIdentifier}
import com.datastax.oss.driver.api.core.addresstranslation.AddressTranslator
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.typesafe.config.{Config, ConfigFactory}
import org.deeplearning4j.util.ModelSerializer
import play.api.Logger
import play.api.libs.json.Json
import tradr.common.PricingPoint
import tradr.common.predictor.PredictorResult
import tradr.common.trading.Trade
import tradr.predictor.models.{Model, Predictor}

import scala.collection.JavaConverters._
import scala.collection.mutable


class DockerTranslator extends AddressTranslator {

  override def close(): Unit = {

  }

  override def translate(address: InetSocketAddress): InetSocketAddress = {
      address
  }
}

object A3CPredictor extends Predictor {


  /**
    * Save a trained network to disk. Per default it overwrites the last saved network
    * @todo recursively read in last version of the model with id "id"
    * @param a3c
    * @param conf
    */
  def save(a3c: A3CModel, conf: Config, id: String) = {
    val saveFile: String = conf.getString("tradr.predictor.a3c.saveFile") + id

    val saveUpdater = true
    ModelSerializer.writeModel(a3c.network, saveFile, saveUpdater)
  }

//  /**
//    * Introduce some random noise in the network in case you want to have multiple agents
//    * for training that are loaded from the same pre-trained network but should then not behave
//    * the same
//    * @param network
//    */
//  def introduceVariation(network: ComputationGraph): Unit = {
//
//    val paramTable = network.paramTable().asScala
//    paramTable.foreach{
//      case (key, param) =>
//        val newParam = Nd4j
//          .rand(param.ordering(), param.shape())
//          .mul(1.0e-6)
//          .add(param)
//        network.setParam(key, newParam)
//    }
//  }


  def getCassandraCluster(conf: Config) = {

    val cassandraIp = conf.getString("cassandra.ip")
    val cassandraPort = conf.getString("cassandra.connectorPort").toInt
    println(s"Connecting to cassandra at $cassandraIp:$cassandraPort")
    //    val inetAddress = InetAddress.getByName(s"${cassandraIp}:${cassandraPort}")
    val inetSocketAddress = InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)
    println(inetSocketAddress)

    val socketList = new java.util.ArrayList[InetSocketAddress]()
    socketList.add(inetSocketAddress)



    val defaultConfig = new DefaultDriverConfigLoader()


    Cluster
      .builder()
      .addContactPoint(inetSocketAddress)
      .build()
  }

  /**
    * Convert the data from cassandra into a (multidimensional) frame
    */
  def convertToFrame(pricingPoints: Seq[PricingPoint], inputSize: Int) = {
    val f = pricingPoints
      .sortBy(p => p.timestamp)
      .map(_.value)
      .toArray

    if (f.size < inputSize) {
      val rem = inputSize - f.size
      Array.fill(rem)(1.0) ++ f
    } else {
      f
    }

  }

  /**
    * Request Cassandra to get all the data needed for a prediction
    * @param time
    * @param conf
    * @return
    */
  def getCassandraData(time: Long, conf: Config): Seq[PricingPoint] = {
    val cassandra = getCassandraCluster(conf)

    val keyspace = conf.getString("cassandra.keyspace")
    val tablename = conf.getString("cassandra.currencyTable")

    val cqlKeyspace: CqlIdentifier = CqlIdentifier.fromInternal(s"$keyspace")
    val session = cassandra.connect(cqlKeyspace)


    // Maybe async execution? However, we have no batch, only one statement to execute
    // and we can't do anything without the data so it might as well be this simple request
    val resultSet = session.execute(s"SELECT * from $keyspace.$tablename WHERE timestamp < $time and instrument = 'EURUSD' ALLOW FILTERING;")

    var results = Seq[PricingPoint]()
    val it = resultSet.iterator()
    while (it.hasNext) {
      val row = it.next
      val point = PricingPoint(
              timestamp = row.getLong(0),
              currencyPair = row.getString(1),
              value = row.getDouble(2))
      results = results :+ point
    }

    // Convert into a pricing point seq, then further into a frame (Array[Double])
//    val results = resultSet
//      .iterator()
//      .asScala
//      .toSeq
//      .map(row => PricingPoint(
//        timestamp = row.getLong(0),
//        currencyPair = row.getString(1),
//        value = row.getDouble(2)))
//    session.close()
    results
  }


  /**
    * Entry point to predict the actions for the given frame
    *
    * @return
    */
  def predict(time: Long, id: String): String = {
    val conf = ConfigFactory.load()

    // Load the latest version of the model
    // @todo cache the model instead of loading it (play's caching)
    val model = A3CModel.load(id, conf)

    // Get the current frame of data
    val currentPricingPoints = getCassandraData(time, conf)

    val inputSize = conf.getInt("tradr.predictor.inputSize")
    val currentFrame = convertToFrame(currentPricingPoints, inputSize)

    // Predict probability distribution over the actions
    val predictions: Map[String, Array[Double]] = model.predict(currentFrame)

    // @todo import recent version of the tradr-common package
    val predictorResults = PredictorResult(
      predictionId = 0L,
      timestamp = System.currentTimeMillis(),
      modelId = id,
      results = predictions
    )


    Json.stringify(Json.toJson[PredictorResult](predictorResults))
  }


  /**
    * Train the model and store it. Return Information about the training, i.e.
    * loss, etc.
    *
    * @param time
    * @param id
    * @param tradeJson
    * @return
    */
  def train(time: Long, id: String, tradeJson: String) = {
    val conf = ConfigFactory.load()

    val trade = Json.parse(tradeJson).as[Trade]
    val initialModel = A3CModel.load(id, conf)

    val model = initialModel.train(tradeJson)
    A3CModel.save(model, conf, id)



    tradeJson
  }


}
