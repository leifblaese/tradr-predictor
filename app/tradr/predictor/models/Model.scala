package tradr.predictor.models

trait Model {

  /**
    * Predict actions for a given frame
    * @param frame
    * @return
    */
  def predict(frame: Array[Double]): Map[String, Array[Double]]


  /**
    * Train the coefficients of a model for a given frame
    * @param tradeString
    * @return
    */

  def train(tradeString: String): Model

}
