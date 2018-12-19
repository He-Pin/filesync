package sync.server


import akka.actor.ActorSystem
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import sync.Config

/**
  * @author hepin1989
  **/
object Server {
  def main0(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("fileSync-server")
    val fileUploadingService = new FileUploadingServiceImpl(
      actorSystem,
      Config.saveTo)

    val group = new NioEventLoopGroup()
    val bootstrap = new ServerBootstrap()

    bootstrap.childHandler(new ChannelInitializer[SocketChannel] {
      override def initChannel(ch: SocketChannel): Unit = {
        val pipeline = ch.pipeline()
        pipeline.addLast(new ServerFileSaveChannel(fileUploadingService))
      }
    })
    bootstrap.group(group, group)
      .channel(classOf[NioServerSocketChannel])
      .localAddress("0.0.0.0", 0)
    val channelFuture = bootstrap.bind()
    val channel = channelFuture.awaitUninterruptibly().channel()
    println(s"server start at :[$channel]")
  }
}
