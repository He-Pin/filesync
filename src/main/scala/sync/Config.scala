package sync

import java.nio.file.Paths

import com.typesafe.config.ConfigFactory

/**
  * @author hepin1989
  **/
object Config {
  private val underling = ConfigFactory.load("conf/filesync.conf")

  lazy val userHome = Paths.get(underling.getString("user.home"))
    .toAbsolutePath

  //TODO make if configurable
  lazy val generateTo = userHome.resolve("source.text")

  lazy val saveTo = userHome.resolve("save")

}
