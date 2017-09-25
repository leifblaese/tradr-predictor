//package tradr.predictor
//
//import javax.inject.Inject
//
//import com.typesafe.config.{Config, ConfigFactory}
//import play.api.mvc.{AbstractController, ControllerComponents}
//
//
//// Cache controller gets ntework from Cache or loads it from disk.
//class CacheController @Inject()(cache: AsyncCacheApi, cc: ControllerComponents)  {
//
//  var a3c: Option[A3CPredictor] = None
//
//
//  val conf = ConfigFactory.load()
//  val a3cKey = config.getString("tradr.predictor.a3cCacheKey")
//
//
//  def getA3C = if (a3c.isDefined) a3c.get else cache.getOrElse("A3CPredictor", A3CPredictor.load(conf))
//  def getFrame =
//
//
//}
