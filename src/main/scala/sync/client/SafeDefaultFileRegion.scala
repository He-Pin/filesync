package sync.client

import java.nio.channels.FileChannel

import io.netty.channel.DefaultFileRegion

/**
  * @author 虎鸣 ,hepin.p@alibaba-inc.com
  **/
class SafeDefaultFileRegion(file: FileChannel, position: Long, count: Long) extends
  DefaultFileRegion(file, position, count) {
  override def deallocate(): Unit = {
    //deallocate by hand
    //super.deallocate()
  }
}
