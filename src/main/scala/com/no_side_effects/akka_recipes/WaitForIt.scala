package com.no_side_effects.akka_recipes

import akka.actor.{Actor, Cancellable, Stash}
import com.no_side_effects.akka_recipes.API.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
  * Created by siddhant.srivastava on 2/1/18.
  */
trait WaitForIt{
  this: Actor with Stash => {
  }

  def withTimeout[A](duration: FiniteDuration, pf: PartialFunction[Any, A], back: (A => Receive), onFail: => Receive)(implicit ex: ExecutionContext) = {
    val cancellable = context.system.scheduler.scheduleOnce(duration, self, Timeout)
    context.become(receiveOnTime(cancellable, pf, back, onFail))
  }

  def receiveOnTime[A](timer: Cancellable, pf: PartialFunction[Any, A], back: (A => Receive), onFail: => Receive): Receive = {
    case Timeout => context.become(onFail)
    case x => pf.isDefinedAt(x) match {
      case true =>
        context.become(back(pf.apply(x)))
        timer.cancel()
      case false => stash()
    }
  }
}
