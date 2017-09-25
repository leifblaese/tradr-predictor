//package tradr.A3C
//
//import org.nd4j.linalg.api.ndarray.INDArray
//
//import scala.collection.JavaConverters._
//import scala.collection.mutable
//
//object ParameterAveraging  {
//
//
//  def getAverageParameters(traders: Seq[Trader], numTraders: Int) = {
//    val summedGradients = traders
//      .map(trader => trader.a3c.network.paramTable())
//      .reduce[java.util.Map[String, INDArray]]{ case (jGradMap1:  java.util.Map[String, INDArray], jGradMap2: java.util.Map[String, INDArray]) =>
//        val gradMap1 = jGradMap1.asScala
//        val gradMap2 = jGradMap2.asScala
//        gradMap1.foreach {
//          case (key, grad) =>
//            gradMap2.update(key, gradMap1(key).add(grad))
//        }
//        gradMap2.asJava
//      }
//
//    summedGradients
//      .asScala
//      .mapValues { case grads => grads.div(numTraders.toDouble) }
//      .toMap
//
//  }
//
//
//  def updateParameters(
//                        traders: Seq[Trader],
//                        meanParameters: Map[String, INDArray],
//                        l2param: Double) = {
//
//    traders.foreach { trader =>
//      val network = trader.a3c.network
//      meanParameters.foreach{
//        case (key, value) =>
//          val p = network.getParam(key)
//          val pNew = p.mul(l2param).add(value.mul(1-l2param))
//          network.setParam(key, pNew)
//      }
//    }
//  }
//
//
//}
//
//case class ParameterAveraging(gradient: scala.collection.mutable.Map[String, INDArray]) {
//  def reset() = this.copy(mutable.Map())
//}
