package org.kaloz.akkamongocqrses

import akka.persistence.PersistentView

class TransactionHistory extends PersistentView {
  override def viewId: String = "transaction-history"

  override def persistenceId: String = "persistent-accounting-system"

  override def receive: Receive = ???
}
