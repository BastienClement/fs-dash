package services

import com.google.cloud.vision.v1.Feature.Type
import com.google.cloud.vision.v1._
import javax.inject.Singleton

import scala.jdk.CollectionConverters._

@Singleton
class Vision {
  private val featureTextDetection: Feature = Feature.newBuilder().setType(Type.TEXT_DETECTION).build()

  def annotate(url: String): Unit = {

    println(System.getenv().asScala.mkString("\n"))
    println(System.getProperties.entrySet().asScala.mkString("\n"))

    val client = ImageAnnotatorClient.create()
    val request = AnnotateImageRequest
      .newBuilder()
      .addFeatures(featureTextDetection)
      .setImage(Image.newBuilder().setSource(ImageSource.newBuilder().setImageUri(url).build()).build())
      .build()

    try {
      val response = client.batchAnnotateImages(List(request).asJava)
      for (res        <- response.getResponsesList.asScala;
           annotation <- res.getTextAnnotationsList.asScala) {
        println(annotation.getDescription)
        println(annotation.getBoundingPoly)
      }
    } finally {
      client.close()
    }
  }
}
