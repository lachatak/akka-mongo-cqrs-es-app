package org.kaloz.akkamongocqrses

import org.joda.time.DateTime

import scalaz.NonEmptyList

object AccountProtocol {

  sealed trait AccountOperationCmd

  case class CreateAccountCmd(accountNumber: AccountNumber, accountHolder: AccountHolder) extends AccountOperationCmd

  case class DeactivateAccountCmd(accountId: String) extends AccountOperationCmd

  case class ReactivateAccountCmd(accountId: String) extends AccountOperationCmd

  case class AccountTransactionCmd(accountNumber: String, amount: BigDecimal, partnerAccount: String, partnerName: String) extends AccountOperationCmd

  sealed trait DomainEvt

  sealed trait AccountOperationEvt extends DomainEvt

  case class AccountCreatedEvt(accountNumber: AccountNumber, accountHolder: AccountHolder, timestamp: DateTime) extends AccountOperationEvt

  case class AccountCreationFailedEvt(errors: NonEmptyList[String]) extends AccountOperationEvt with ErrorEvt

  case class AccountDeactivatedEvt(accountId: String, timestamp: DateTime) extends AccountOperationEvt

  case class AccountDeactivationFailedEvt(errors: NonEmptyList[String]) extends AccountOperationEvt with ErrorEvt

  case class AccountReactivatedEvt(accountId: String, timestamp: DateTime) extends AccountOperationEvt

  case class AccountReactivationFailedEvt(errors: NonEmptyList[String]) extends AccountOperationEvt with ErrorEvt

  case class AccountTransactionProcessedEvt(accountNumber: String, balance: BigDecimal, partnerAccount: String, partnerName: String, timestamp: DateTime) extends AccountOperationEvt

  sealed trait ErrorEvt {
    val errors: NonEmptyList[String]
  }

}
