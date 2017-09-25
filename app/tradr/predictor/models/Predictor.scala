package tradr.predictor.models

trait Predictor {
  // predictor returns json of prediction
  def predict(model: Model): String

  // predictor returns json with information about training
  def train(model: Model): String

}
