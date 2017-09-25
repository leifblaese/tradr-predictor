package tradr.predictor.models.a3c

import java.net.{InetAddress, InetSocketAddress, Socket}

import com.datastax.oss.driver.api.core.Cluster
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.typesafe.config.{Config, ConfigFactory}
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers.{ConvolutionLayer, DenseLayer}
import org.deeplearning4j.nn.gradient.{DefaultGradient, Gradient}
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.api.ops.impl.transforms.Log
import org.nd4j.linalg.factory.Nd4j
import play.api.libs.json.Json
import tradr.common.{PricingPoint, Trade}
import tradr.predictor.models.{Model, Predictor}

import scala.collection.JavaConverters._
import scala.collection.mutable


object A3CPredictor extends Predictor {

  /**
    * Create a computation graph in order to get a new network.
    * Depends on "tradr.predictor.a3c.inputsize" configuration
    * @param conf
    * @return
    */
  private[this] def getComputationGraph(conf: Config) = {
    val inputSize = conf.getInt("tradr.predictor.a3c.inputsize")

    new NeuralNetConfiguration.Builder()
      .seed(123)
      .graphBuilder()
      .addInputs("frame")
      .addLayer(
        "layer1",
        new ConvolutionLayer
        .Builder(1, 5)
          .weightInit(WeightInit.XAVIER)
          .nIn(1)
          .stride(5, 1)
          .nOut(20)
          .activation(Activation.RELU)
          .build(),
        "frame")
      .addLayer(
        "layer2",
        new ConvolutionLayer
        .Builder(1, 5)
          .nIn(20)
          .weightInit(WeightInit.XAVIER)
          .stride(1, 2)
          .nOut(20)
          .activation(Activation.RELU)
          .build(),
        "layer1")
      .addLayer("fc",
        new DenseLayer
        .Builder()
          .weightInit(WeightInit.XAVIER)
          .activation(Activation.RELU)
          .nIn(2480)
          .nOut(100)
          .build(),
        "layer2")
      .addLayer(
        "actionProbabilities",
        new DenseLayer.Builder()
          .nIn(100)
          .weightInit(WeightInit.XAVIER)
          .nOut(4)
          .activation(Activation.SOFTMAX)
          .build(),
        "fc")
      .addLayer(
        "valueFunction",
        new DenseLayer.Builder()
          .weightInit(WeightInit.XAVIER)
          .nIn(100)
          .nOut(1)
          .activation(Activation.IDENTITY)
          .build(),
        "fc")
      .setOutputs("actionProbabilities", "valueFunction")
      .setInputTypes(InputType.convolutionalFlat(1, inputSize, 1))
      .build()
  }


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

  /**
    * Introduce some random noise in the network in case you want to have multiple agents
    * for training that are loaded from the same pre-trained network but should then not behave
    * the same
    * @param network
    */
  def introduceVariation(network: ComputationGraph): Unit = {

    val paramTable = network.paramTable().asScala
    paramTable.foreach{
      case (key, param) =>
        val newParam = Nd4j
          .rand(param.ordering(), param.shape())
          .mul(1.0e-6)
          .add(param)
        network.setParam(key, newParam)
    }
  }




  private[this] def getCassandraCluster(conf: Config) = {

    val cassandraIp = conf.getString("cassandra.ip")
    val cassandraPort = conf.getString("cassandra.connectorPort").toInt

    //    val inetAddress = InetAddress.getByName(s"${cassandraIp}:${cassandraPort}")
    val inetSocketAddress = InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)

    Cluster
      .builder()
      .addContactPoint(inetSocketAddress)
      .build()
  }

  /**
    * Convert the data from cassandra into a (multidimensional) frame
    */
  private[this] def convertToFrame(pricingPoints: Seq[PricingPoint]) = {
    pricingPoints
      .sortBy(p => p.timestamp)
      .map(_.value)
      .toArray
  }

  /**
    * Request Cassandra to get all the data needed for a prediction
    * @param time
    * @param cluster
    * @param conf
    * @return
    */
  private[this] def getCassandraData(time: Long, cluster: Cluster, conf: Config): Seq[PricingPoint] = {
    val cassandra = getCassandraCluster(conf)
    val session = cassandra.connect()

    val keyspace = conf.getString("cassandra.currencyKeyspace")
    val tablename = conf.getString("cassandra.currencyTable")

    session.execute(s"USE KEYSPACE $keyspace")

    // Maybe async execution? However, we have no batch, only one statement to execute
    // and we can't do anything without the data so it might as well be this simple request
    val resultSet = session.execute(s"SELECT * from $keyspace.$tablename WHERE timestamp < $time")

    // Convert into a pricing point seq, then further into a frame (Array[Double])
    resultSet
      .iterator()
      .asScala
      .toSeq
      .map(row => PricingPoint(
        timestamp = row.getLong(0),
        currencyPair = row.getString(1),
        value = row.getDouble(2)))
  }


  /**
    * Entry point to predict the actions for the given frame
    *
    * @return
    */
  def predict(id: String, time: Long, conf: Config): String = {
    // Load the latest version of the model
    // @todo cache the model instead of loading it (play's caching)
    val model = A3CModel.load(id, conf)

    // Get the current frame of data
    val cluster = getCassandraCluster(conf)
    val currentPricingPoints = getCassandraData(time, cluster, conf)
    val currentFrame = convertToFrame(currentPricingPoints)



    // Predict probability distribution over the actions
    val predictions: Map[String, Array[Double]] = model.predict(currentFrame)

    // @todo import recent version of the tradr-common package
    val predictorResults = PredictorResult(
      id = 0L,
      timestamp = System.currentTimeMillis(),
      modelId = id,
      results = predictions
    )
    
    Json.stringify(Json.toJson[PredictorResult](predictionResult))
  }

}
