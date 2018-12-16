package sync.client

import java.net.InetSocketAddress

import akka.Done
import akka.actor.ActorSystem
import sync.Config

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
object Client {
  def main0(args: Array[String]): Unit = {
    val startTime = System.currentTimeMillis()
    val arguments = args
    println(
      """
        |usage:
        |input server address:ip:port connections
        |
    """.stripMargin)

    val actorSystem = ActorSystem("file-sync")

    val addr = if (arguments.isEmpty) {
      println("input server address in: ip:port please.")
      scala.io.StdIn.readLine()
    } else {
      arguments(0)
    }
    val Array(ip, port) = addr.split(':')
    val connections = arguments.drop(1).headOption.map(_.toInt).getOrElse(4)
    //
    val serverAddress = new InetSocketAddress(ip, port.toInt)
    val filePath = Config.generateTo
    val donePromise = Promise[Done]()
    val fileSynchronizer = actorSystem.actorOf(props = FileSynchronizer.props(
      serverAddress = serverAddress,
      filePath = filePath,
      sliceCount = connections,
      donePromise = donePromise
    ))
    Await.result(donePromise.future, Duration.Inf)
    println(s"time = ${System.currentTimeMillis() - startTime} ms")
    //TODO manager
    //TODO while with byte
  }
}
