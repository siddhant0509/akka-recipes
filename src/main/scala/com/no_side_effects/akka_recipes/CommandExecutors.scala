package com.no_side_effects.akka_recipes

import java.util.UUID
import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.no_side_effects.akka_recipes.API.{Initialize, Timeout}

import scala.concurrent.ExecutionContext
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


  case class CommandExecutors[A](actions: CommandActions[A], successAction: SuccessAction[A], failedAction: FailedAction[A])(implicit duration: FiniteDuration, executionContext: ExecutionContext) extends Actor with ActorLogging{

    val timer = context.system.scheduler.scheduleOnce(duration, self, Timeout)

    val (forwardActor, requests) = actions

    @scala.throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      log.debug("Starting Commands Executor  with path {}", self.path)
      self ! Initialize
      super.preStart()
    }


    override def receive: Receive = {
      case Initialize =>
        val requestExecutorActors = List.range(0, requests.size)
          .map(_ => UUID.randomUUID().toString)
          .zip(requests)
          .map{case (id, request) => (id, request, createCommandExecutor(request, id))}
        requestExecutorActors.foreach{case(_,_, actor) => context.watch(actor)}
        context.become(receiveResult(
          requestExecutorActors.map{case (id, req, _) => (id, req)}.toMap,
          requestExecutorActors.map{case (id, _, actor) => (actor, id)}.toMap,
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
      val (id, _) = s.req
      outBoundRequest.get(id).foreach(deployRequest => {
        if(outBoundRequest.size - 1 == 0) {
          context.parent ! (result ++ Seq(s))
          context.stop(self)
        }
        else
          context.become(receiveResult(outBoundRequest - id, actors, result ++ Seq(s)))
      })
    }


    def createCommandExecutor(req: A, id: String): ActorRef = {
      context.actorOf(props(
        (forwardActor, (id, req)),
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



  case class CommandExecutor[A](action: CommandAction[A], successAction: SuccessAction[A], failedAction: FailedAction[A])(implicit duration: FiniteDuration, executionContext: ExecutionContext) extends Actor with ActorLogging{

    val timer = context.system.scheduler.scheduleOnce(duration, self, Timeout)

    val (forwardActor, idr @ (id, request)) = action

    @scala.throws[Exception](classOf[Exception])
    override def preStart(): Unit = {
      log.debug("Command Executor Started on path {}", self.path)
      log.info("Sending request {}", action)
      forwardActor ! request
    }

    override def receive: Receive = {
      case Timeout =>
        log.error("Command Execution timeout")
        context.parent ! FailedCommandExecution(idr, new TimeoutException("Timeout while executing command"))
      case x => failedAction.orElse(successAction).apply((idr, x)) match {
        case None => context.parent ! SuccessfulCommandExecution(idr)
        case Some(succ) => context.parent ! succ
      }
    }

    @scala.throws[Exception](classOf[Exception])
    override def postStop(): Unit = {
      log.debug("Command Executor for req {} stopped", (id, request))
      timer.cancel()
      super.postStop()
    }
  }
}