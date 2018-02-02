package com.swiggy.projectR.recipes

import java.util.UUID
import java.util.concurrent.TimeoutException

import akka.actor.AbstractActor.ActorContext
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import com.swiggy.projectR.API
import com.swiggy.projectR.API.Initialize

import scala.concurrent.duration.FiniteDuration

/**
  * Created by siddhant.srivastava on 1/31/18.
  */

object CommandExecutors{

  type CommandExecutionRequest[A] = (String, A)
  type CommandExecutionRequests[A] = (String, Seq[A])

  sealed trait CommandExecutionResult[+A]{
    val req: CommandExecutionRequest[A]
  }
  case class SuccessfulCommandExecution[A](req: CommandExecutionRequest[A]) extends CommandExecutionResult[A]
  case class FailedCommandExecution[A](req: CommandExecutionRequest[A], ex: Throwable) extends CommandExecutionResult[A]

  type CommandAction[A] = (ActorRef, CommandExecutionRequest[A])
  type CommandActions[A] = (ActorRef, Seq[A])

  type SuccessAction[A] = (PartialFunction[(CommandExecutionRequest[A], Any), Option[_ <: CommandExecutionResult[A]]])
  type FailedAction[A] = PartialFunction[(CommandExecutionRequest[A], Any), Option[_ <: CommandExecutionResult[A]]]

  def props[A](action: CommandAction[A], successAction: SuccessAction[A], failedAction: FailedAction[A])(implicit duration: FiniteDuration) =
    Props(CommandExecutor(action, successAction, failedAction))


  case class CommandExecutors[A](actions: CommandActions[A], successAction: SuccessAction[A], failedAction: FailedAction[A])(implicit duration: FiniteDuration) extends Actor with ActorLogging{

    import concurrent.ExecutionContext.Implicits.global
    val timer = context.system.scheduler.scheduleOnce(duration, self, API.Timeout)

    @scala.throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      log.debug("Starting Commands Executor  with path {}", self.path)
      self ! Initialize
      super.preStart()
    }


    override def receive: Receive = {
      case Initialize =>
        val requestExecutorActors = List.range(0, actions._2.size)
          .map(_ => UUID.randomUUID().toString)
          .zip(actions._2)
          .map(t => (t._1, t._2, createCommandExecutor(t._2, t._1)))
        requestExecutorActors.foreach(t => context.watch(t._3))
        context.become(receiveResult(
          requestExecutorActors.map(t => (t._1, t._2)).toMap,
          requestExecutorActors.map(t => (t._3, t._1)).toMap,
          List()
        ))
    }

    def receiveResult(outBoundRequest: Map[String, A], actors: Map[ActorRef, String], result: Seq[CommandExecutionResult[A]]): Receive = {
      case f : SuccessfulCommandExecution[A] => onModelDeploymentStatus(f, outBoundRequest, actors, result)
      case s : FailedCommandExecution[A] => onModelDeploymentStatus(s, outBoundRequest, actors, result)
      case Terminated(creator) =>
        (for{
          id <- actors.get(creator)
          deployRequest <- outBoundRequest.get(id)
        } yield FailedCommandExecution((id, deployRequest), new TimeoutException("Model Creator Failed with timeout")))
          .foreach(f => onModelDeploymentStatus(f, outBoundRequest, actors, result))
      case Timeout => context.parent ! result
        context.stop(self)
    }

    def onModelDeploymentStatus(s: CommandExecutionResult[A], outBoundRequest: Map[String, A], actors: Map[ActorRef, String], result: Seq[CommandExecutionResult[A]]): Unit = {
      outBoundRequest.get(s.req._1).foreach(deployRequest => {
        if(outBoundRequest.size - 1 == 0) {
          context.parent ! (result ++ Seq(s))
          context.stop(self)
        }
        else
          context.become(receiveResult(outBoundRequest - s.req._1, actors, result ++ Seq(s)))
      })
    }


    def createCommandExecutor(req: A, id: String): ActorRef = {
      context.actorOf(props(
        (actions._1, (id, req)),
        successAction,
        failedAction
      ), "command-executor-" + id)
    }


    @scala.throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
      log.debug("Stopping Commands Executor with actor path {}", self.path)
      timer.cancel()
      super.postStop()
    }

  }



  case class CommandExecutor[A](action: CommandAction[A], successAction: SuccessAction[A], failedAction: FailedAction[A])(implicit duration: FiniteDuration) extends Actor with ActorLogging{
    import concurrent.ExecutionContext.Implicits.global

    val timer = context.system.scheduler.scheduleOnce(duration, self, API.Timeout)

    @scala.throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      log.debug("Command Executor Started on path {}", self.path)
      log.info("Sending request {}", action._2._2)
      action._1 ! action._2._2
    }

    override def receive: Receive = {
      case API.Timeout =>
        log.error("Command Execution timeout")
        context.parent ! FailedCommandExecution(action._2, new TimeoutException("Timeout while executing command"))
      case x => failedAction.orElse(successAction).apply((action._2, x)) match {
        case None => context.parent ! SuccessfulCommandExecution(action._2)
        case Some(succ) => context.parent ! succ
      }
    }

    @scala.throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
      log.debug("Command Executor for req {} stopped", action._2)
      timer.cancel()
      super.postStop()
    }
  }
}