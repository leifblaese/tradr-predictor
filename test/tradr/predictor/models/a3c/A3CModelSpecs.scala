package tradr.predictor.models.a3c

import java.io.File

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.specs2.execute.Success
import org.specs2.mutable.Specification

class A3CModelSpecs extends Specification {

  "We can get data from cassandra" >> {

    val conf = ConfigFactory.parseFile(new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf"))
    val data = A3CPredictor.getCassandraData(DateTime.now().getMillis, conf)
    println(data)

    Success("Yeah")
  }


}
