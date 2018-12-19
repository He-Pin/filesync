package sync

import scala.annotation.tailrec

/**
  * @author hepin1989
  **/
object FileSync {
  @tailrec
  def main(args: Array[String]): Unit = {
    printBanner()
    args.headOption match {
      case Some(head) => head match {
        case "gen" =>
          TestFIleGenerator.main0(args.drop(1))
        case "server" =>
          server.Server.main0(args.drop(1))
        case "connect" =>
          client.Client.main0(args.drop(1))
        case _ =>
          sys.error(s"unsupported role:$head")
          printBanner()
      }
      case None =>
        println("Nothing in arguments")
        printBanner()
        println("please input by hands now.")
        print("$>")
        val inputs = scala.io.StdIn.readLine().split(' ')
        main(inputs)
    }

  }

  def printBanner(): Unit = {
    println(
      """
        |usage:
        |
        |gen                            to generate file
        |server                         run as server
        |connect ip:port connections    run as client and connect to remote server
        |
      """.stripMargin)
  }
}
