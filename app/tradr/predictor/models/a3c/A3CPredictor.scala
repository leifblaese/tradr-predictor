package tradr.predictor.models.a3c

import java.net.{InetAddress, InetSocketAddress, Socket}

import com.datastax.oss.driver.api.core.addresstranslation.AddressTranslator
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json.Json
import tradr.common.predictor.{Predictor, PredictorResult}
import tradr.common.trading.Trade
import tradr.model.a3c.A3CModel



object A3CPredictor extends Predictor {


  /**
    * Entry point to predict the actions for the given frame
    *
    * @return
    */
  def predict(time: Long): String = {
    val conf = ConfigFactory.load()

    val id = "model1" /// !!!!!!!!!!!!!!!!!!!!!! obvious quickfix should be parameter

    // Load the latest version of the model
    // @todo cache the model instead of loading it (play's caching)
    val model = A3CModel.load(id, conf)

    // Get the current frame of data
    val currentFrame = A3CModel.getFrame(time, conf)

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

    val model = initialModel.train(Array(trade))
    A3CModel.save(model, conf, id)



    tradeJson
  }


}
