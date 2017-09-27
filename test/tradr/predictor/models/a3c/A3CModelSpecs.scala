package tradr.predictor.models.a3c

import java.io.File

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.specs2.execute.Success
import org.specs2.mutable.Specification
import tradr.predictor.models.a3c.A3CPredictor.getCassandraCluster

class A3CModelSpecs extends Specification {


  "Specs2 is working" >> {
    1 must be_==(1)
  }
//
//  "We can connect to cassandra" >> {
//    val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
//    val cassandra = getCassandraCluster(conf)
//    val session = cassandra.connect()
//
//    println(session.getKeyspace.asCql())
//    Success("Yeah")
//  }
//
//  "We can get data from cassandra" >> {
//
//    val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
//    val data = A3CPredictor.getCassandraData(DateTime.now().getMillis, conf)
//    println(data)
//
//    Success("Yeah")
//  }


}
