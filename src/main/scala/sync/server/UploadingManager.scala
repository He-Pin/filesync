package sync.server

import java.nio.channels.FileChannel
import java.nio.file.Path

import akka.actor.Status.Failure
import akka.actor.{Actor, Props}

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
class UploadingManager(baseFolder: Path) extends Actor {
  override def receive: Receive = {
    case command@GetFileChannel(uniqTaskId, fileName, fileLength, _, _) =>
      val uploadingMonitor = context.child(uniqTaskId)
        .getOrElse(context.actorOf(UploadingMonitor.props(
          baseFolder,
          uniqTaskId,
          fileName,
          fileLength
        ), uniqTaskId))
      uploadingMonitor.forward(command)
    case msg@SliceWriteDone(uniqTaskId, _, _, _) =>
      context.child(uniqTaskId) match {
        case Some(ref) =>
          ref.forward(msg)
        case None =>
          sender() ! Failure(new IllegalArgumentException(s"no monitor found for $uniqTaskId"))
      }
    case msg =>
      println(msg)
  }
}

object UploadingManager {
  def props(baseFolder: Path): Props = {
    Props(new UploadingManager(baseFolder))
  }
}

private[server] sealed trait Command

private[server] final case class GetFileChannel(uniqTaskId: String,
                                                fileName: String,
                                                fileLength: Long,
                                                sliceIndex: Int,
                                                totalSliceCount: Int) extends Command

private[server] sealed trait Result

private[server] final case class GetFileChannelResult(fileChannel: FileChannel,
                                                      filePath: Path) extends Result

private[server] sealed trait Event

private[server] final case class SliceWriteDone(uniqTaskId: String,
                                                sliceIndex: Int,
                                                code: Int,
                                                msg: String = "") extends Event