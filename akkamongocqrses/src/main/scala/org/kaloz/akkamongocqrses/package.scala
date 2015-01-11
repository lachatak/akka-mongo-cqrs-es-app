package org.kaloz

import java.text.SimpleDateFormat

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.kaloz.akkamongocqrses.AccountProtocol.{AccountCreatedEvt, AccountDeletedEvt, AccountReactivatedEvt}

import scalaz.Scalaz._
import scalaz._

package object akkamongocqrses {

  type DomainValidation[R] = ValidationNel[String, R]

  trait ValidationKey {
    def failNel = this.toString.failureNel
  }

  object CommonValidations {

    def checkString(value: String, err: ValidationKey): DomainValidation[String] =
      if (StringUtils.isEmpty(value)) err.failNel else value.success

    def checkDate(date: String): DomainValidation[DateTime] =
      try {
        new DateTime(new SimpleDateFormat("ddMMyyyyHHmmss").parse(date)).success
      } catch {
        case e: Exception => e.getMessage.failureNel
      }
  }

  object Currency extends Enumeration {
    type Currency = Value
    val HUF, EUR, GBP, USD = Value
  }

  object State extends Enumeration {
    type State = Value
    val Active, Deleted = Value

    implicit class StateValue(state: Value) {
      def delete = state match {
        case Active => Deleted.success
        case Deleted => "Account is already deleted".failureNel
      }

      def reactivate = state match {
        case Active => "Account is already active".failureNel
        case Deleted => Active.success
      }
    }

  }

  object Type extends Enumeration {
    type AccountType = Value
    val Current, Saving, Loan = Value
  }

  sealed case class AccountNumber(accountId: String, accountType: Type.Value, currency: Currency.Value)

  object AccountNumber {

    import org.kaloz.akkamongocqrses.CommonValidations._

    case object AccountIdRequired extends ValidationKey

    def validate(accountNumber: AccountNumber): DomainValidation[AccountNumber] =
      checkString(accountNumber.accountId, AccountIdRequired) match {
        case Success(_) => accountNumber.success
        case Failure(errors) => errors.failure
      }
  }

  sealed case class AccountHolder(name: String, address: String)

  object AccountHolder {

    import org.kaloz.akkamongocqrses.CommonValidations._

    case object NameRequired extends ValidationKey

    case object AddressRequired extends ValidationKey

    def validate(accountHolder: AccountHolder): DomainValidation[AccountHolder] =
      (checkString(accountHolder.name, NameRequired) |@| checkString(accountHolder.address, AddressRequired)) {
        AccountHolder(_, _)
      }
  }

  case class StateHistoryItem(change: DateTime, state: State.Value)

  sealed case class Account(accountNumber: AccountNumber, accountHolder: AccountHolder, balance: BigDecimal, stateHistory: List[StateHistoryItem])

  object Account {

    def create(accountNumber: AccountNumber, accountHolder: AccountHolder): DomainValidation[AccountCreatedEvt] =
      (AccountNumber.validate(accountNumber) |@| AccountHolder.validate(accountHolder)) {
        case (accNum, accHolder) => AccountCreatedEvt(accNum, accHolder, new DateTime)
      }

    def delete(account: Account): DomainValidation[AccountDeletedEvt] = {
      import org.kaloz.akkamongocqrses.State._
      account.stateHistory.head.state.delete match {
        case Success(_) => AccountDeletedEvt(account.accountNumber.accountId, new DateTime).success
        case Failure(errors) => errors.failure
      }
    }

    def reactivate(account: Account): DomainValidation[AccountReactivatedEvt] = {
      import org.kaloz.akkamongocqrses.State._
      account.stateHistory.head.state.reactivate match {
        case Success(_) => AccountReactivatedEvt(account.accountNumber.accountId, new DateTime).success
        case Failure(errors) => errors.failure
      }
    }
  }

}
