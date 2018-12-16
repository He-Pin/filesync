package sync

import java.nio.charset.Charset

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
object TestFIleGenerator {
  def main0(args: Array[String]): Unit = {
    //generating file
    implicit val system = ActorSystem("fileSync")
    implicit val materilizer: ActorMaterializer = ActorMaterializer()

    //Source
    val charset = Charset.forName("ASCII")
    val contentSource = Source.fromIterator(() => (1 to 100000000).toIterator)
      .map(str => ByteString(new StringBuffer(str.toString).append('\n').toString, charset))
      .buffer(10000, overflowStrategy = OverflowStrategy.backpressure)
      .conflate((bs1, bs2) => bs1 ++ bs2)
      .buffer(8, overflowStrategy = OverflowStrategy.backpressure)
      .async

    //sink
    val fileSink = FileIO.toPath(Config.generateTo)
    println(s"starting generate file from content source to :[${Config.generateTo}].")
    //run
    val startTime = System.currentTimeMillis()
    val mat = contentSource.runWith(fileSink)
    val fileIOResult = Await.result(mat, Duration.Inf)
    println("final result :" + fileIOResult)
    materilizer.shutdown()
    Await.result(system.terminate(), Duration.Inf)
    println(s"times :${(System.currentTimeMillis() - startTime) / 1000}s")
  }
}
