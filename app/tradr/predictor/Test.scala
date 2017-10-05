package tradr.predictor

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.util

import com.datastax.oss.driver.api.core.metadata.Node
import com.typesafe.config.ConfigFactory
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.graph.ComputationGraph
import org.nd4j.linalg.factory.Nd4j
import tradr.predictor.models.a3c.{A3CModel, A3CPredictor}
import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.{LearningRatePolicy, MultiLayerConfiguration, NeuralNetConfiguration, Updater}
import org.deeplearning4j.nn.conf.NeuralNetConfiguration.ListBuilder
import org.deeplearning4j.nn.conf.distribution.UniformDistribution
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers.{ConvolutionLayer, DenseLayer, OutputLayer, SubsamplingLayer}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.lossfunctions.LossFunctions

object Test extends App {

  def test1 = {
  val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
  val cassandra = A3CPredictor.getCassandraCluster(conf)
  val data = A3CPredictor.getCassandraData(10L, conf)

  println(data)

  }



  def test2 = {

    val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
    val inputSize = conf.getInt("tradr.predictor.a3c.inputsize")
    val graphConf = A3CModel.getComputationGraph(conf)
    val graph = new ComputationGraph(graphConf)
    graph.init()
    println(Option(graph).isDefined)
//    val input = Array.fill(inputSize)(Array.fill(1)(1.0))
    val input = Array.fill(inputSize)(1.0)
    val indInput = Nd4j.create(input)
    println(Option(indInput).isDefined)
    println(indInput)
    println(indInput.shape().toSeq)
    val output = graph.output(indInput)
    println(Option(output).isDefined)
    val outputDouble = output.map(o => o.data().asDouble().toSeq).toSeq
    println(outputDouble)
  }
  test2

//  private val log = LoggerFactory.getRootLogger(classOf[])


//  val cassandraIp = conf.getString("cassandra.ip")
//  val cassandraPort = conf.getString("cassandra.connectorPort").toInt
//  println(s"Connecting to cassandra at $cassandraIp:$cassandraPort")
//  val inetSocketAddress = InetSocketAddress.createUnresolved(cassandraIp, cassandraPort)
//  val nodeInet = cassandra.getMetadata.getNodes.entrySet().toArray.head
//  val defaultInetSocketAddress = InetSocketAddress.createUnresolved("0.0.0.0", 9042)
//  val node = cassandra.getMetadata.getNodes.get(defaultInetSocketAddress)

//  println(Option(node).isDefined)
//  println(node.getCassandraVersion)
//  println(node.getConnectAddress)

//  println(cassandra.getName)
//  cassandra.getMetadata.getNodes.put(inetSocketAddress)
//  println(cassandra.getMetadata.getNodes)

//
//val session = cassandra.connect()
//    session.execute("USE KEYSPACE tradr")
//    session.close()


}
