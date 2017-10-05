package tradr.predictor.controller


import javax.inject.Inject

import com.typesafe.config.ConfigFactory
import play.api.Logger
import play.api.mvc.{AbstractController, ControllerComponents}
import tradr.predictor.models.a3c.{A3CModel, A3CPredictor}

import scala.concurrent.{ExecutionContext, Future}



class PredictorController  @Inject()(cc: ControllerComponents) extends AbstractController(cc)  {

  implicit val ec = ExecutionContext.global



  /**
    * Predict the action for a given time. Based on the time submitted, the predictor will collect
    * the necessary data from cassandra and will then make a prediction based on it.
    * @param time Long, timestamp for which the prediction should take place
    * @param id id of the model that should do the prediction
    * @return Json with the information about the prediction and who did it.
    */
  def predict(time: String, id: String) = Action.async { implicit request =>
    Future {
      Logger.info(s"time $time")
      val prediction: String = A3CPredictor.predict(time.toLong, id)

      Ok(prediction)
    }
  }


  /**
    * Train a given network with a trade
    * We submit a JSON string with the information about the trade that we want to train on
    * and train the network. The trained network will be written to Disk (HDFS). When
    * training is finished, a controller should be notified
    *
    * @param trade Json with information of the trade
    * @param id id of the model that should be trained (for ensemble training)
    * @return
    */
  def train(time: Long, id: String, trade: String) = Action.async { implicit request =>
    implicit val ec = ExecutionContext.global

    Future {


      Ok("")
    }
  }
}
