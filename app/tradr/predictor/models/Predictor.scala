package tradr.predictor.models

trait Predictor {
  // predictor returns json of prediction
  def predict(time: Long, id: String) : String

  // predictor returns json with information about training
  def train(time: Long, id: String, trade: String): String

}
