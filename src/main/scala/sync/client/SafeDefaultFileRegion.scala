package sync.client

import java.nio.channels.FileChannel

import io.netty.channel.DefaultFileRegion

/**
  * @author hepin1989
  **/
class SafeDefaultFileRegion(file: FileChannel, position: Long, count: Long) extends
  DefaultFileRegion(file, position, count) {
  override def deallocate(): Unit = {
    //deallocate by hand, otherwise will fail
    //the underling file is released by an Akka actor once the uploading is done.
    //super.deallocate()
  }
}
