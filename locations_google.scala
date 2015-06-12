#!/usr/bin/env scalas

/***
scalaVersion := "2.11.6"
resolvers += Resolver.url("typesafe-ivy-repo", url("http://typesafe.artifactoryonline.com/typesafe/releases"))(Resolver.ivyStylePatterns)
resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.bintrayRepo("mfglabs", "maven")

libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.4.0"
libraryDependencies += ("io.github.jto" %% "validation-json" % "1.0").exclude("org.tpolecat", "tut-core_2.11")
libraryDependencies += "com.mfglabs" %% "akka-stream-extensions" % "0.7.3"
*/

import akka.actor.ActorSystem
import akka.stream.{ActorFlowMaterializerSettings, ActorFlowMaterializer}
import akka.stream.scaladsl._

import com.ning.http.client.AsyncHttpClientConfig
import com.mfglabs.stream._

import org.slf4j._
import play.api.libs.ws.ning._
import play.api.libs.ws._
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import java.nio._

val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
root match {
  case root:ch.qos.logback.classic.Logger => root.setLevel(ch.qos.logback.classic.Level.WARN);
}

implicit val system = ActorSystem()
implicit val mat = ActorFlowMaterializer(ActorFlowMaterializerSettings(system).withInputBuffer(16, 16))
implicit val ecBlocking = ExecutionContextForBlockingOps(scala.concurrent.ExecutionContext.Implicits.global)

val client     = new NingWSClient(new NingAsyncHttpClientConfigBuilder().build)
val rootUrl    = "https://maps.googleapis.com/maps/api/geocode/json"
val inputFile  = "google_locations.csv"
val outputFile = file.Paths.get("google_output.csv")
val maxChunkSize = 5 * 1024 * 1024

val LineRegex  = """(\d+);(-?\d+\.?\d*);(-?\d+\.?\d*);.*""".r

def parseLine(line: String): Option[(Long, Double, Double)] = line match {
  case LineRegex(id, lat, long) => Some( (id.toLong, lat.toDouble, long.toDouble) )
  case _ => println(line); None
}

case class AddressComponent(long_name: String, types: Set[String])

object AddressComponent {
  import play.api.libs.json._
  import play.api.data.mapping._
  import play.api.data.mapping.json.Rules._

  implicit val addressR = Rule.gen[JsValue, AddressComponent]
}

def reverseGeocoding(latitude: Double, longitude: Double): Future[Option[(String, String)]] = {
  import play.api.libs.json._
  import play.api.data.mapping._
  import play.api.data.mapping.json.Rules._

  client.url(rootUrl)
        .withQueryString("latlng" -> s"$latitude,$longitude")
        .get()
        .map { r =>
          r.status match {
            case  200 =>
              (r.json \\ "address_components").headOption.map { js =>
                From[JsValue, Seq[AddressComponent]](js) match {
                  case Success(components) =>
                    for {
                      locality   <- components.find(_.types.contains("locality"))
                      codePostal <- components.find(_.types.contains("postal_code"))
                    } yield (locality.long_name, codePostal.long_name)
                  case _ => None
                }
              }.getOrElse(None)
            case _ =>
              println(s"$latitude,$longitude")
              println(r.body)
              None
          }
        }
}

file.Files.deleteIfExists(outputFile)

val lines = SourceExt
  .fromFile(new java.io.File(inputFile), maxChunkSize = maxChunkSize)
  .via(FlowExt.rechunkByteStringBySeparator(akka.util.ByteString("\n"), maximumChunkBytes = maxChunkSize))
  .map(_.utf8String)
  .map(parseLine(_))
  .collect{ case Some(t) => t }
  .via(FlowExt.rateLimiter(300.millisecond))
  .mapAsync(1){ case (id, latitude, longitude) =>
    reverseGeocoding(latitude, longitude).map {
      case Some( (ville, codePostal) ) => s"$id;$ville;$codePostal\n"
      case None => s"$id;;\n"
    }
  }.grouped(10)
  .runWith(Sink.foreach{ lines =>
    val bytes = lines.mkString("").getBytes(charset.StandardCharsets.UTF_8)
    file.Files.write(outputFile, bytes, file.StandardOpenOption.CREATE, file.StandardOpenOption.APPEND)
  })

Await.result(lines, Duration.Inf)

client.close()
system.shutdown()