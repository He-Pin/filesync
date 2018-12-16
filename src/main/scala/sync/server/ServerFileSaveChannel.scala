package sync.server

import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.charset.Charset

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.ReferenceCountUtil

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
class ServerFileSaveChannel(service: FileUploadingService) extends ChannelInboundHandlerAdapter {

  import ServerFileSaveChannel._

  private var fileChannel: FileChannel = _
  private var mappedFileBytes:MappedByteBuffer = _

  private var writtenBytesCount: Long = 0
  private var position: Long = 0
  private var protocolLength: Short = 0
  private var protocol: Protocol = _
  private var cache: ByteBuf = _


  //连接成功后，发送第一个指令，格式：
  //协议长度-长度-任务id-长度-文件名-文件长度-总分片-当前分片Index-当前分片位置-当前分片大小
  //Short-Byte-String-Byte-String-Long-Byte-Byte-Long-Long
  //--2-----1-----x-----1-----x-----8---1----1-----8----8-
  def readProtocolBody(body: ByteBuf): Protocol = {
    val uniqTaskIdLength = body.readByte().toInt
    val uniqTaskId = body.readCharSequence(uniqTaskIdLength, Charset.defaultCharset()).toString
    val fileNameLength = body.readByte().toInt
    val fileName = body.readCharSequence(fileNameLength, Charset.defaultCharset()).toString
    val fileLength = body.readLong()
    val totalSliceCount = body.readByte().toInt
    val sliceIndex = body.readByte().toInt
    val startPosition = body.readLong()
    val sliceLength = body.readLong()
    Protocol(
      uniqTaskId = uniqTaskId,
      fileName = fileName,
      fileLength = fileLength,
      sliceIndex = sliceIndex,
      totalSliceCount = totalSliceCount,
      startPosition = startPosition,
      sliceLength = sliceLength
    )
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val byteBuf = msg.asInstanceOf[ByteBuf]
    if (byteBuf.readableBytes() != 0) {
      if (protocol eq null) {
        handleProtocol(byteBuf, ctx)
      } else {
        write2File(byteBuf, ctx)
      }
      ReferenceCountUtil.release(msg)
    }
  }

  def handleProtocol(byteBuf: ByteBuf, ctx: ChannelHandlerContext): Unit = {
    if (protocolLength == 0) {
      //happy path
      if ((cache eq null) && byteBuf.readableBytes() >= 2) {
        protocolLength = byteBuf.readShort()
        if (byteBuf.readableBytes() >= protocolLength) {
          protocol = readProtocolBody(byteBuf)
          updatePositionAsProtocol(protocol)
          write2File(byteBuf, ctx)
        } else {
          if (cache eq null) {
            cache = ctx.alloc().directBuffer(32)
          }
          cache.writeBytes(byteBuf)
        }
      } else {
        if (cache eq null) {
          cache = ctx.alloc().buffer(64)
        }
        cache.writeBytes(byteBuf)
        if (cache.readableBytes() >= 2) {
          protocolLength = cache.readShort()
          if (cache.readableBytes() >= protocolLength) {
            protocol = readProtocolBody(cache)
            updatePositionAsProtocol(protocol)
            write2File(cache, ctx)
          }
        }
      }
    } else {
      cache.writeBytes(byteBuf)
      if (cache.readableBytes() >= protocolLength) {
        protocol = readProtocolBody(cache)
        updatePositionAsProtocol(protocol)
        write2File(cache, ctx)
      }
    }
  }

  private def updatePositionAsProtocol(protocol: Protocol): Unit = {
    position = protocol.startPosition
  }

  private def write2File(byteBuf: ByteBuf, ctx: ChannelHandlerContext): Unit = {
    if (byteBuf.readableBytes() != 0) {
      if (fileChannel eq null) {
        val future = service.getWritableFileChannel(
          uniqTaskId = protocol.uniqTaskId,
          fileName = protocol.fileName,
          fileLength = protocol.fileLength,
          sliceIndex = protocol.sliceIndex,
          totalSliceCount = protocol.totalSliceCount
        )
        val getFileChannelResult = Await.result(future, Duration.Inf)
        fileChannel = getFileChannelResult.fileChannel
      }

      var readableBytes = byteBuf.readableBytes()
      writtenBytesCount += readableBytes

      while (readableBytes > 0) {
        position += byteBuf.readBytes(fileChannel, position, readableBytes)
        readableBytes = byteBuf.readableBytes()
      }

      //mapped
//      if (mappedFileBytes eq null){
//        mappedFileBytes = fileChannel.map(MapMode.READ_WRITE,position,protocol.sliceLength)
//      }
//      while (readableBytes > 0) {
//        byteBuf.readBytes(mappedFileBytes)
//        readableBytes = byteBuf.readableBytes()
//      }
      //

      if (writtenBytesCount == protocol.sliceLength) {
        println(s"slice:${protocol.sliceIndex} of file:${protocol.fileName} done,commit to monitor.")
        //写入完成
        val code = byteBuf.alloc().ioBuffer(1)
        code.writeByte(0)
        ctx.channel().writeAndFlush(code)
        service.commitSlice(
          uniqTaskId = protocol.uniqTaskId,
          sliceIndex = protocol.sliceIndex,
          code = 0)
      }
      if (protocol.sliceLength - writtenBytesCount < 1000) {
        println(s"remaining :${protocol.sliceLength - writtenBytesCount}")
      }
    }
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"channel active: ${ctx.channel()}")
    super.channelActive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    super.exceptionCaught(ctx, cause)
  }
}

object ServerFileSaveChannel {

  //连接成功后，发送第一个指令，格式：
  //协议长度-长度-任务id-长度-文件名-文件长度-总分片-当前分片Index-当前分片位置-当前分片大小
  //Short-Byte-String-Byte-String-Long-Byte-Byte-Long-Long
  //--2-----1-----x-----1-----x-----8---1----1-----8----8-
  final case class Protocol(uniqTaskId: String,
                            fileName: String,
                            fileLength: Long,
                            sliceIndex: Int,
                            totalSliceCount: Int,
                            startPosition: Long,
                            sliceLength: Long)

}
