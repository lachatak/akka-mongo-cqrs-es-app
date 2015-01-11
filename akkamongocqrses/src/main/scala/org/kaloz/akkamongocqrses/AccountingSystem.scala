package org.kaloz.akkamongocqrses

import akka.actor.{Actor, ActorLogging}
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.kaloz.akkamongocqrses.AccountProtocol._

import scalaz.Scalaz._

final case class AccountState(accounts: Map[String, Account] = Map.empty) {
  def update(account: Account) = copy(accounts = accounts + (account.accountNumber.accountId -> account))

  def get(accountId: String) = accounts.get(accountId)
}

class AccountingSystem extends PersistentActor with ActorLogging with StreamEvtPublisher {

  import org.kaloz.akkamongocqrses.AccountProtocol._

  var state = AccountState()

  def updateInternalState(account: Account): Unit =
    state = state.update(account)

  override def persistenceId: String = "persistent-accounting-system"

  override def receiveRecover: Receive = {
    case evt: AccountOperationEvt => handleEvt(evt)
    case SnapshotOffer(_, snapshot: AccountState) => state = snapshot
  }

  override def handleEvt: PartialFunction[DomainEvt, DomainEvt] = {
    case evt: AccountCreatedEvt =>
      updateInternalState(Account(evt.accountNumber, evt.accountHolder, 0, List(StateHistoryItem(evt.timestamp, State.Active))))
      log.info(s"New account ${evt.accountNumber} has been created")
      evt
    case evt: AccountDeactivatedEvt =>
      updateInternalState(state.get(evt.accountId).map(account => account.copy(stateHistory = StateHistoryItem(evt.timestamp, State.Deactived) :: account.stateHistory)).get)
      log.info(s"Account ${evt.accountId} has been deactivated")
      evt
    case evt: AccountReactivatedEvt =>
      updateInternalState(state.get(evt.accountId).map(account => account.copy(stateHistory = StateHistoryItem(evt.timestamp, State.Active) :: account.stateHistory)).get)
      log.info(s"Account ${evt.accountId} has been reactivated")
      evt
  }

  def createAccount(cmd: CreateAccountCmd): DomainValidation[AccountCreatedEvt] = {
    state.get(cmd.accountNumber.accountId) match {
      case Some(_) => s"Account ${cmd.accountNumber.accountId} already exists!".failureNel
      case None => Account.create(cmd.accountNumber, cmd.accountHolder)
    }
  }

  def deactivateAccount(cmd: DeactivateAccountCmd): DomainValidation[AccountDeactivatedEvt] = generateEvent(cmd.accountId) {
    account => Account.deactivate(account)
  }

  def reactivateAccount(cmd: ReactivateAccountCmd): DomainValidation[AccountReactivatedEvt] = generateEvent(cmd.accountId) {
    account => Account.reactivate(account)
  }

  def generateEvent[A <: AccountOperationEvt](accountId: String)(fn: Account => DomainValidation[A]): DomainValidation[A] =
    state.get(accountId) match {
      case Some(account) => fn(account)
      case None => s"Account $accountId does not exist!".failureNel
    }

  override def receiveCommand: Receive = {
    case cmd: CreateAccountCmd => createAccount(cmd).fold(
      err => publishEvt(AccountCreationFailedEvt(err)),
      evt => persist(evt)(handleAndThenPublishEvt)
    )
    case cmd: DeactivateAccountCmd => deactivateAccount(cmd).fold(
      err => publishEvt(AccountDeactivationFailedEvt(err)),
      evt => persist(evt)(handleAndThenPublishEvt)
    )
    case cmd: ReactivateAccountCmd => reactivateAccount(cmd).fold(
      err => publishEvt(AccountReactivationFailedEvt(err)),
      evt => persist(evt)(handleAndThenPublishEvt)
    )
  }

}

trait EvtPublisher {


  def handleEvt: PartialFunction[DomainEvt, DomainEvt]

  def publishEvt: PartialFunction[DomainEvt, Unit]

  def handleAndThenPublishEvt: PartialFunction[DomainEvt, Unit] = handleEvt andThen publishEvt
}

trait StreamEvtPublisher extends EvtPublisher {

  self: Actor with ActorLogging =>

  override def publishEvt: PartialFunction[DomainEvt, Unit] = {
    case evt =>
      context.system.eventStream.publish(evt)
      log.info(s"$evt is published!")
  }
}

//trait MediatorEvtPublisher extends EvtPublisher {
//
//  self: Actor =>
//
//  private val mediator = DistributedPubSubExtension(context.system).mediator
//
//  val publish: PartialFunction[DomainEvt, Unit] = {
//    case evt => mediator ! evt
//  }
//
//}


