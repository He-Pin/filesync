package sync.client

import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.file.Path

import akka.actor.{Actor, ActorRef, Props}
import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelInitializer}

import scala.concurrent.{ExecutionContextExecutor, Future, Promise}

/**
  * @author hepin1989
  **/
class FileSynchronizationWorker(serverAddress: InetSocketAddress,
                                filePath: Path,
                                totalSliceCount: Int,
                                sliceIndex: Int,
                                randomAccessFile: RandomAccessFile,
                                fileChannel: FileChannel,
                                start: Long,
                                sliceLength: Long,
                                uniqTaskId: String) extends Actor {

  import FileSynchronizationWorker._

  override def receive: Receive = {
    case Start =>
      handleStartCommand()
  }

  private def handleStartCommand(): Unit = {
    val bootStrap = new Bootstrap
    bootStrap.group(group)
    val promise = Promise[(Int, Channel)]()
    bootStrap.channel(classOf[NioSocketChannel])
      .handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          val pipeline = ch.pipeline()
          pipeline.addLast(new ClientFilePumpingChannel(
            filePath = filePath,
            totalSliceCount = totalSliceCount,
            sliceIndex = sliceIndex,
            randomAccessFile = randomAccessFile,
            fileChannel = fileChannel,
            start = start,
            sliceLength = sliceLength,
            uniqTaskId = uniqTaskId,
            donePromise = promise
          ))
        }
      })
      .connect(serverAddress)
    import akka.pattern.pipe
    implicit val dispatcher: ExecutionContextExecutor = context.dispatcher
    promise.future.pipeTo(self)(ActorRef.noSender)
    context.become(waitingTransferDone(promise.future))
  }

  def waitingTransferDone(future: Future[(Int, Channel)]): Receive = {
    case (code: Int, channel: Channel) =>
      println(s"transfer done of slice :$sliceIndex,code:$code,channel:$channel")
      context.parent ! SliceDone(sliceIndex, code == 0, code)
  }

  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
}

object FileSynchronizationWorker {
  def props(serverAddress: InetSocketAddress,
            filePath: Path,
            totalSliceCount: Int,
            sliceIndex: Int,
            randomAccessFile: RandomAccessFile,
            fileChannel: FileChannel,
            start: Long,
            sliceLength: Long,
            uniqTaskId: String): Props = {
    Props(new FileSynchronizationWorker(
      serverAddress,
      filePath,
      totalSliceCount,
      sliceIndex,
      randomAccessFile,
      fileChannel,
      start,
      sliceLength,
      uniqTaskId
    ))
  }

  private lazy val group: NioEventLoopGroup = new NioEventLoopGroup()

  sealed trait Command

  private[client] case object Start

  sealed trait Event

  private[client] final case class SliceDone(sliceIndex: Int, isSuccess: Boolean, code: Int) extends Event

}
