enablePlugins(JavaAppPackaging)
enablePlugins(UniversalPlugin)
enablePlugins(LinuxPlugin)

name := "FileSync"

version := "0.1"

scalaVersion := "2.12.8"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-stream
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.19"
// https://mvnrepository.com/artifact/io.netty/netty-all
libraryDependencies += "io.netty" % "netty-all" % "4.1.32.Final"

maintainer := "your.name@company.org"

import LinuxPlugin._

mapGenericFilesToLinux