package services

import java.io.FileInputStream

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.Feature.Type
import com.google.cloud.vision.v1._
import com.google.protobuf.ByteString
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class Vision @Inject()(conf: Configuration, ws: WSClient)(implicit ec: ExecutionContext) {
  private val key = conf.get[String]("dash.gcp.key")

  private lazy val settings = ImageAnnotatorSettings
    .newBuilder()
    .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.fromStream(new FileInputStream(key))))
    .build()

  private val featureTextDetection: Feature = Feature.newBuilder().setType(Type.TEXT_DETECTION).build()

  def annotate(url: String): Future[List[EntityAnnotation]] = {
    try {
      ws.url(url).get().flatMap {
        case r if r.status == 200 =>
          Future {
            scala.concurrent.blocking {
              val client = ImageAnnotatorClient.create(settings)
              try {
                val image = Image.newBuilder().setContent(ByteString.copyFrom(r.bodyAsBytes.toByteBuffer)).build()
                val request = AnnotateImageRequest
                  .newBuilder()
                  .addFeatures(featureTextDetection)
                  .setImage(image)
                  .build()

                val response = client.batchAnnotateImages(List(request).asJava)
                for (res        <- response.getResponsesList.asScala.toList;
                     annotation <- res.getTextAnnotationsList.asScala)
                  yield annotation
              } finally {
                client.close()
              }
            }
          }
        case r =>
          Future.failed(new Exception(s"Image fetch failed: ${r.status} ${r.statusText} - ${r.body}"))
      }
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }
}
