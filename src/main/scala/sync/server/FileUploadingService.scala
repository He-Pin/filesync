package sync.server

import akka.NotUsed

import scala.concurrent.Future

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
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
