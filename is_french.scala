#!/usr/bin/env scalas

/***
scalaVersion := "2.10.4"
resolvers += Resolver.url("typesafe-ivy-repo", url("http://typesafe.artifactoryonline.com/typesafe/releases"))(Resolver.ivyStylePatterns)
libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.4.0"
*/

import com.ning.http.client.AsyncHttpClientConfig
import org.slf4j._
import play.api.libs.ws.ning._
import play.api.libs.ws._
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.collection.JavaConverters._
import java.nio._

val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
root match {
  case root:ch.qos.logback.classic.Logger => root.setLevel(ch.qos.logback.classic.Level.WARN);
}

val client     = new NingWSClient(new NingAsyncHttpClientConfigBuilder().build)
val rootUrl    = "http://demo.twofishes.net/static/geocoder.html"
val inputFile  = "output_all.csv"
val outputFile = file.Paths.get("output_french.csv")

val LineRegex  = """(\d+);[^;]*;(-?\d+\.?\d*);(-?\d+\.?\d*);.*""".r

val countryCodes = Set("FR", "YT", "RE", "GP", "GF", "MQ", "PM", "TF", "WF", "PF", "NC", "MF", "BL")

def parseLine(line: String): Option[(Long, Double, Double)] = line match {
  case LineRegex(id, lat, long) => Some( (id.toLong, lat.toDouble, long.toDouble) )
  case _ => println(line); None
}

def isFrench(latitude: Double, longitude: Double): Future[Option[Boolean]] = {
  client.url(rootUrl)
        .withQueryString("ll" -> s"$latitude,$longitude")
        .get()
        .map { r =>
          r.status match {
            case  200 =>
              for {
                js <- (r.json \\ "cc").headOption
                cc <- js.asOpt[String]
              } yield countryCodes.contains(cc)
            case _ =>
              println(s"$latitude,$longitude")
              println(r.body)
              None
          }
        }
}

file.Files.deleteIfExists(outputFile)

Source.fromFile(inputFile).getLines.toSeq.grouped(100).foreach { lines =>
  val filtered = lines.map(parseLine).flatten

  Await.result(
    Future.traverse(filtered){ case (id, latitude, longitude) =>
      isFrench(latitude, longitude).map {
        case Some(f) => s"$id;$f\n"
        case None    => s"$id;\n"
      }
    }.map { lines =>
      val bytes = lines.mkString("").getBytes(charset.StandardCharsets.UTF_8)
      file.Files.write(outputFile, bytes, file.StandardOpenOption.CREATE, file.StandardOpenOption.APPEND)
    }.recover {
      case e => e.printStackTrace
    },
    duration.Duration.Inf
  )
}

client.close()