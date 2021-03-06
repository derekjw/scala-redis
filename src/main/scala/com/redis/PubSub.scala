package com.redis

object Util {
  object Break extends RuntimeException;
  def break { throw Break }
  def whileTrue(block: => Unit) {
    try {
      while (true)
        try {
          block
        } catch { case Break => return }
    }
  }
}

sealed trait PubSubMessage
case class S(channel: String, noSubscribed: Int) extends PubSubMessage
case class U(channel: String, noSubscribed: Int) extends PubSubMessage
case class M(origChannel: String, message: String) extends PubSubMessage

import Util._
trait PubSub { self: Redis =>
  var pubSub: Boolean = _

  class Consumer(fn: PubSubMessage => Any) extends Runnable {

    def start () {
      val myThread = new Thread(this) ;
      myThread.start() ;
    }

    def run {
      whileTrue {
        asList match {
          case Some(Some(msgType) :: Some(channel) :: Some(data) :: Nil) =>
            msgType match {
              case "subscribe" => fn(S(channel, data.toInt))
              case "unsubscribe" if (data.toInt == 0) => 
                println("for break")
                fn(U(channel, data.toInt))
                break
              case "unsubscribe" => 
                fn(U(channel, data.toInt))
              case "message" => 
                fn(M(channel, data))
              case x => throw new RuntimeException("unhandled message: " + x)
            }
          case _ => break
        }
      }
    }
  }

  def subscribe(channel: String, channels: String*)(fn: PubSubMessage => Any) {
    if (pubSub == true) { // already pubsub ing
      subscribeRaw(channel, channels: _*)
    } else {
      pubSub = true
      subscribeRaw(channel, channels: _*)
      new Consumer(fn).start
    }
  }

  def subscribeRaw(channel: String, channels: String*) {
    send("SUBSCRIBE", channel :: channels.toList)(())
  }

  def unsubscribe = {
    send("UNSUBSCRIBE")(())
  }

  def unsubscribe(channel: String, channels: String*) = {
    send("UNSUBSCRIBE", channel :: channels.toList)(())
  }

  def publish(channel: String, msg: String) = {
    send("PUBLISH", List(channel, msg))(())
  }
}
