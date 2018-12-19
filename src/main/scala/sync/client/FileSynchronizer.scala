package sync.client

import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.UUID

import akka.Done
import akka.actor.{Actor, Props}
import sync.client.FileSynchronizationWorker.SliceDone

import scala.concurrent.Promise

/**
  * @author hepin1989
  **/
class FileSynchronizer(serverAddress: InetSocketAddress,
                       filePath: Path,
                       sliceCount: Int,
                       donePromise: Promise[Done]) extends Actor {
  private var randomAccessFile: RandomAccessFile = _
  private var fileChannel: FileChannel = _

  import FileSynchronizer._

  override def receive: Receive = {
    case StartSync =>
      println(s"start sync file :$filePath with slice count $sliceCount")
      //start with x child to sync
      handleStartSyncCommand()
  }

  def handleStartSyncCommand(): Unit = {
    randomAccessFile = new RandomAccessFile(filePath.toFile, "r")
    fileChannel = randomAccessFile.getChannel
    val fileLength = randomAccessFile.length()
    val step: Long = fileLength / sliceCount
    var currentPosition: Long = 0
    val uniqTaskId = UUID.randomUUID().toString
    for (sliceIndex <- 0 until sliceCount) {
      val start = currentPosition
      val sliceLength = if (sliceIndex != sliceCount - 1) step else fileLength - currentPosition
      //
      val childProps = FileSynchronizationWorker.props(
        serverAddress = serverAddress,
        filePath = filePath,
        totalSliceCount = sliceCount,
        sliceIndex = sliceIndex,
        randomAccessFile = randomAccessFile,
        fileChannel = fileChannel,
        start = start,
        sliceLength = sliceLength,
        uniqTaskId = uniqTaskId
      )
      val child = context.actorOf(childProps, s"slice-$sliceIndex")
      context.watch(child)
      currentPosition += step
    }
    //
    context.become(waitingSubTaskAllComplete(uniqTaskId, sliceCount))
  }

  def waitingSubTaskAllComplete(uniqTaskId: String, remaining: Int): Receive = {
    case SliceDone(sliceIndex, isSuccess, code) =>
      println(s"slice: $sliceIndex of task: $uniqTaskId complete with $isSuccess,code :$code")
      if (!isSuccess) {
        donePromise.tryFailure(
          new IllegalStateException(s"slice $sliceIndex of task:$uniqTaskId is failure with code:$code"))
        context.stop(self)
      } else if (remaining - 1 == 0) {
        println(s"task: $uniqTaskId with task id all complete.")
        println(s"release file:$filePath")
        fileChannel.close()
        randomAccessFile.close()
        context.stop(self)
        donePromise.trySuccess(Done)
      } else {
        context.become(waitingSubTaskAllComplete(uniqTaskId, remaining - 1))
      }
  }

  override def preStart(): Unit = {
    super.preStart()
    //TODO check is file
    this.randomAccessFile = new RandomAccessFile(filePath.toFile, "r")
    this.fileChannel = randomAccessFile.getChannel
    self ! StartSync
  }
}

object FileSynchronizer {

  def props(serverAddress: InetSocketAddress,
            filePath: Path,
            sliceCount: Int,
            donePromise: Promise[Done]): Props = {
    Props(new FileSynchronizer(serverAddress: InetSocketAddress,
      filePath: Path,
      sliceCount: Int,
      donePromise))
  }

  sealed trait Command

  private[client] case object StartSync


}
