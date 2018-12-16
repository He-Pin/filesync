package sync.client

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Path

import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}

import scala.concurrent.Promise

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
class ClientFilePumpingChannel(filePath: Path,
                               totalSliceCount: Int,
                               sliceIndex: Int,
                               randomAccessFile: RandomAccessFile,
                               fileChannel: FileChannel,
                               start: Long,
                               sliceLength: Long,
                               uniqTaskId: String,
                               donePromise: Promise[(Int, Channel)]) extends ChannelDuplexHandler {
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    println(s"channel active for slice index:$sliceIndex position:$start length:$sliceLength")
    //连接成功后，发送第一个指令，格式：
    //协议长度-长度-任务id-长度-文件名-文件长度-总分片-当前分片Index-当前分片位置-当前分片大小
    //Short-Byte-String-Byte-String-Long-Byte-Byte-Long-Long
    //--2-----1-----x-----1-----x-----8---1----1-----8----8-
    val fileNameBytes = filePath.getFileName.toString.getBytes
    val taskIdBytes = uniqTaskId.getBytes

    val protocol = ctx.alloc().directBuffer(
      2 + 1 + taskIdBytes.length + 1 + fileNameBytes.length + 8 + 1 + 1 + 8 + 8)
    protocol.writerIndex(2)
    protocol.writeByte(taskIdBytes.length)
      .writeBytes(taskIdBytes)
      .writeByte(fileNameBytes.length)
      .writeBytes(fileNameBytes)
      .writeLong(randomAccessFile.length())
      .writeByte(totalSliceCount) //不支持超过128的分片
      .writeByte(sliceIndex)
      .writeLong(start)
      .writeLong(sliceLength)

    protocol.setShort(0, protocol.readableBytes() - 2)
    //
    ctx.channel().writeAndFlush(protocol).addListener(new GenericFutureListener[Future[_ >: Void]] {
      override def operationComplete(future: Future[_ >: Void]): Unit = {
        if (future.isSuccess) {
          //连接后发送的第一个包之后，才开始发送文件本身
          println(
            s"""
               |starting to send...
               |fileName:   ${filePath.getFileName}
               |slice:      $sliceIndex
               |total:      $totalSliceCount
               |length:     $sliceLength
               |position:   $start
               |uniqTaskId: $uniqTaskId
       """.stripMargin)

          ctx.writeAndFlush(new SafeDefaultFileRegion(fileChannel, start, sliceLength))

          //          ctx.executor().scheduleAtFixedRate(
          //            () => ctx.channel().flush(),
          //            10,
          //            10,
          //            TimeUnit.MILLISECONDS
          //          )
        } else {
          future.cause().printStackTrace()
        }
      }
    })
    super.channelActive(ctx)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val byteBuf = msg.asInstanceOf[ByteBuf]
    //第一个字节作为ack
    donePromise.trySuccess((byteBuf.readByte().toInt, ctx.channel()))
    //TODO
    ctx.channel().close()
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    println(s"channel inactive for slice index:$sliceIndex position:$start length:$sliceLength")
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    super.exceptionCaught(ctx, cause)
  }
}
