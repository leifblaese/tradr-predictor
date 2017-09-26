package tradr.predictor.models.a3c

import com.typesafe.config.Config
import org.deeplearning4j.nn.gradient.{DefaultGradient, Gradient}
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.api.ops.impl.transforms.Log
import org.nd4j.linalg.factory.Nd4j
import play.api.libs.json.Json
import tradr.common.trading.Trade
import tradr.common.PricingPoint
import tradr.common.predictor.PredictorResult
import tradr.predictor.models.Model
import tradr.predictor.models.a3c.A3CModel.{computeGradientMap, toGradient}

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
    val network = ModelSerializer.restoreComputationGraph(saveFile)
    network.init()

    A3CModel(network)
  }


  def save(model: Model, conf: Config): Unit = {

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

case class A3CModel(network: ComputationGraph,
               gamma: Double = 0.99) extends Model {

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
  def train(json: String): Model = {
    val trade = Json.parse(json).as[Trade]

    val partialTrade = trade.tradeSequence.head
    val profit = Trade.computeProfit(trade)
    val gradientMap = computeGradientMap(network, trade, gamma, profit)
    val gradient = toGradient(gradientMap)
    network.update(gradient)

    this
  }
}









