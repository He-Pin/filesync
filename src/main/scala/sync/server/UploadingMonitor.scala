package sync.server

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path}

import akka.actor.{Actor, Props}
import sync.server.UploadingMonitor.UploadingTaskDone

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
class UploadingMonitor(baseFolder: Path,
                       uniqTaskId: String,
                       fileName: String,
                       fileLength: Long) extends Actor {
  private var randomAccessFile: RandomAccessFile = _
  private var fileChannel: FileChannel = _
  private var filePath: Path = _
  private var sliceInTheFlyCount = 0
  private var allSliceCount = 0
  private var sliceCompletedCount = 0

  override def receive: Receive = {
    case GetFileChannel(uniqTaskId, fileName, fileLength, sliceIndex, totalSliceCount) =>
      sender() ! GetFileChannelResult(fileChannel, filePath)
      if (allSliceCount == 0) {
        //TODO check
        allSliceCount = totalSliceCount
      }
      sliceInTheFlyCount += 1
    case SliceWriteDone(uniqTaskId, sliceIndex, code, msg) =>
      sliceInTheFlyCount -= 1
      sliceCompletedCount += 1

      println(s"slice: $sliceIndex of task:$uniqTaskId done " +
        s"with code: $code,msg: $msg,slice in the fly count:$sliceInTheFlyCount")
      if (sliceCompletedCount == allSliceCount) {
        //all done
        fileChannel.force(true)
        fileChannel.close()
        randomAccessFile.close()
        println(s"task:$uniqTaskId all slice count:$allSliceCount done,saved to $filePath,committed.")
        context.parent ! UploadingTaskDone(uniqTaskId, filePath, fileLength)
        context.stop(self)
      }
  }

  override def preStart(): Unit = {
    filePath = baseFolder.resolve(s"$uniqTaskId-$fileName").toAbsolutePath
    if (Files.notExists(filePath.getParent)) {
      Files.createDirectories(filePath.getParent)
    }
    randomAccessFile = new RandomAccessFile(
      filePath.toString, "rw")
    randomAccessFile.setLength(fileLength)
    fileChannel = randomAccessFile.getChannel
    super.preStart()
  }
}

object UploadingMonitor {

  def props(baseFolder: Path,
            uniqTaskId: String,
            fileName: String,
            fileLength: Long): Props = {
    Props(new UploadingMonitor(baseFolder, uniqTaskId, fileName, fileLength))
  }

  private[server] sealed trait Event

  private[server] final case class UploadingTaskDone(uniqTaskId: String,
                                                     filePath: Path,
                                                     fileLength: Long) extends Event

}
