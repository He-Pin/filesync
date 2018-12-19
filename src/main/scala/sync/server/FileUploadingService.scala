package sync.server

import akka.NotUsed

import scala.concurrent.Future

/**
  * @author hepin1989
  **/
trait FileUploadingService {

  def getWritableFileChannel(uniqTaskId: String,
                             fileName: String,
                             fileLength: Long,
                             sliceIndex: Int,
                             totalSliceCount: Int): Future[GetFileChannelResult]

  def commitSlice(uniqTaskId: String,
                  sliceIndex: Int,
                  code: Int): Future[NotUsed]
}
