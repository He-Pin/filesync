package sync.server

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout

import scala.concurrent.Future

/**
  * @author hepin1989
  **/
class FileUploadingServiceImpl(actorSystem: ActorSystem,
                               baseFolder: Path) extends FileUploadingService {
  private val managerRef: ActorRef = actorSystem.actorOf(UploadingManager.props(baseFolder))

  override def getWritableFileChannel(uniqTaskId: String,
                                      fileName: String,
                                      fileLength: Long,
                                      sliceIndex: Int,
                                      totalSliceCount: Int): Future[GetFileChannelResult] = {
    import akka.pattern.ask
    implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
    (managerRef ? GetFileChannel(uniqTaskId, fileName, fileLength, sliceIndex, totalSliceCount))
      .mapTo[GetFileChannelResult]
  }

  override def commitSlice(uniqTaskId: String, sliceIndex: Int, code: Int): Future[NotUsed] = {
    import akka.pattern.ask
    implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
    (managerRef ? SliceWriteDone(uniqTaskId, sliceIndex, code))
      .mapTo[NotUsed]
  }
}
