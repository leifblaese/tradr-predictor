package tradr.predictor.models.a3c

import java.io.File

import com.typesafe.config.Config
import org.deeplearning4j.nn.conf.{ComputationGraphConfiguration, NeuralNetConfiguration, Updater}
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers.{ConvolutionLayer, DenseLayer}
import org.deeplearning4j.nn.gradient.{DefaultGradient, Gradient}
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import play.api.libs.json.Json
import tradr.common.trading.Trade
import tradr.common.PricingPoint
import tradr.common.predictor.PredictorResult
import tradr.predictor.models.Model

import scala.collection.mutable
import collection.JavaConverters._

object A3CModel {

  /**
    * Load a network from disk
    * @param id ID of the model that is loaded
    * @param conf Config, has the save file location stored as "tradr.predictor.network.saveFile"
    * @return
    */
  def load(id: String, conf: Config): A3CModel = {
    val saveFile: String = conf.getString("tradr.predictor.a3c.saveFile") + id

    println(s"Loading a3c network from $saveFile")
    val f = new File(saveFile)
    if (f.exists()) {
      val network = ModelSerializer.restoreComputationGraph(saveFile)
      network.init()
      A3CModel(network)
    } else {
      create(conf)
    }
  }

  def create(conf: Config): A3CModel = {

    val graphConf = getComputationGraph(conf)
    val net = new ComputationGraph(graphConf)
    net.init()
    A3CModel(net)
  }

  def save(model: A3CModel, conf: Config, id: String): Unit = {

    val saveFile: String = conf.getString("tradr.predictor.a3c.saveFile") + id

    ModelSerializer.writeModel(
      model.network,
      saveFile,
      true
    )
  }
  /**
    * Create a computation graph in order to get a new network.
    * Depends on "tradr.predictor.a3c.inputsize" configuration
    * @param conf
    * @return
    */
  def getComputationGraph(conf: Config): ComputationGraphConfiguration = {
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
          .nIn(1200)
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
    * Compute the gradient map (gradient for each variable) from the trade.
    * We do this explicitely so that save one forward pass
    * @param network
    * @param trade
    * @param gamma
    * @param profit
    * @return
    */
  def computeGradientMap(network: ComputationGraph,
                                       trade: Trade,
                                       gamma: Double,
                                       profit: Double): mutable.Map[String, INDArray] = {
    val r = profit/trade.tradeSequence.size
    var initR = trade.tradeSequence.last.valuePrediction.head
    val initialGradient: collection.mutable.Map[String, INDArray] = collection.mutable.Map()

    val totalGradient = trade
      .tradeSequence
      .indices
      .reverse
      .drop(1)
      .foreach{
        //@ TODO: DO WE NEED TO CHANGE THE COMPUTATION OF R? SHOULDN'T IT BE A FOLD OVER ALL ELEMENTS, INSTEAD OF A FOREACH
        case i =>
          val partialTrade = trade.tradeSequence(i)
          val td = 1.0 + Math.log(trade.tradeSequence.last.time.toDouble - partialTrade.time.toDouble)
          val R = initR * Math.pow(gamma,td) + i * r

          val actionProb = partialTrade.actionProbabilities
          val valuePred = partialTrade.valuePrediction.head

          val actionProbError = actionProb.map(Math.log).map(_ * (R - valuePred))
          val valueFunError = Math.pow(R - valuePred, 2.0)

          // Do a backward pass through the network
          val currentGradient: Gradient = network
            .backpropGradient(
              Nd4j.create(actionProbError),
              Nd4j.create(Array(valueFunError))
            )


          val gradForVar = currentGradient.gradientForVariable()
          gradForVar
            .asScala
            .foreach{
              case (key, grad) =>
                if (!initialGradient.contains(key)) {
                  initialGradient.update(key, grad)
                } else {
                  initialGradient.update(key, initialGradient(key).add(grad))
                }
            }
      }
    initialGradient
  }

  /**
    * Compute the gradient for a given gradient map
    * @param gradientMap
    * @return
    */
  def toGradient(gradientMap: collection.mutable.Map[String, INDArray]):
  DefaultGradient = {

    val gradient = new DefaultGradient()
    gradientMap.foreach{
      case (name, grad) => gradient.setGradientFor(name, grad)
    }
    gradient
  }


}

case class A3CModel(
                   network: ComputationGraph,
                    gamma: Double = 0.99
                   ) extends Model {

  /**
    * Predict for a given frame and return the action probabilities
    * @return
    */
  def predict(frame: Array[Double]): Map[String, Array[Double]] = {
    // Convert to a mllib vector
    val indFrame = Nd4j.create(frame)
    val indResults = network.output(indFrame)

    indResults
      .map(indarray => indarray.data().asDouble())
      .zipWithIndex
      .map{
        case (arr, 0) => "probabilities" -> arr
        case (arr, 1) => "valueFun" -> arr
      }
      .toMap
  }

//  /**
//    * Train the network on one complete Trade (i.e. sequence of buys and sells).
//    * Unit return because memory is updated in place
//    * @param a3c
//    * @param trade
//    */
//  def train(a3c: A3CModel, trade: Trade): Unit = {
//    val network = a3c.network
//    // Get a partial trade
//    val partialTrade = trade.tradeSequence.head
//
//    val profit = Trade.computeProfit(trade)
//    val gradientMap = computeGradientMap(a3c.network, trade, a3c.gamma, profit)
//    val gradient = toGradient(gradientMap)
//    network.update(gradient)
//  }

  /**
    * Train the model on a given trade
    * @param json
    * @return
    */
  def train(json: String): A3CModel = {
    val trade = Json.parse(json).as[Trade]

    val partialTrade = trade.tradeSequence.head
    val profit = Trade.computeProfit(trade)
    val gradientMap = A3CModel.computeGradientMap(network, trade, gamma, profit)
    val gradient = A3CModel.toGradient(gradientMap)
    network.update(gradient)

    this
  }
}









