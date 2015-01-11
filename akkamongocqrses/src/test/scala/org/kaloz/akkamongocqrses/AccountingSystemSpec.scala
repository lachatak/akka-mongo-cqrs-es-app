package org.kaloz.akkamongocqrses

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.extract.UUIDTempNaming
import de.flapdoodle.embed.process.io.directories.PlatformTempDir
import de.flapdoodle.embed.process.io.{NullProcessor, Processors}
import de.flapdoodle.embed.process.runtime.Network
import org.kaloz.akkamongocqrses.AccountProtocol._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._

object AccountingSystemSpec {

  def config(port: Int) = ConfigFactory.parseString(
    s"""
      |akka.persistence.journal.plugin = "casbah-journal"
      |akka.persistence.snapshot-store.plugin = "casbah-snapshot-store"
      |akka.persistence.journal.max-deletion-batch-size = 3
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |akka.persistence.view.auto-update-interval = 1s
      |casbah-journal.mongo-journal-url = "mongodb://localhost:$port/store.messages"
      |casbah-journal.mongo-journal-write-concern = "journaled"
      |casbah-journal.mongo-journal-write-concern-timeout = 10000
      |casbah-snapshot-store.mongo-snapshot-url = "mongodb://localhost:$port/store.snapshots"
      |casbah-snapshot-store.mongo-snapshot-write-concern = "journaled"
      |casbah-snapshot-store.mongo-snapshot-write-concern-timeout = 10000
    """.stripMargin)

  lazy val freePort = Network.getFreeServerPort
}

class AccountingSystemSpec extends TestKit(ActorSystem("test", AccountingSystemSpec.config(AccountingSystemSpec.freePort)))
with ImplicitSender
with WordSpecLike
with MustMatchers
with BeforeAndAfterAll {

  import org.kaloz.akkamongocqrses.AccountingSystemSpec._

  lazy val host = "localhost"
  lazy val port = freePort
  lazy val localHostIPV6 = Network.localhostIsIPv6()

  val artifactStorePath = new PlatformTempDir()
  val executableNaming = new UUIDTempNaming()
  val command = Command.MongoD
  val version = Version.Main.PRODUCTION

  // Used to filter out console output messages.
  val processOutput = new ProcessOutput(
    Processors.named("[mongod>]", new NullProcessor),
    Processors.named("[MONGOD>]", new NullProcessor),
    Processors.named("[console>]", new NullProcessor))

  val runtimeConfig: IRuntimeConfig =
    new RuntimeConfigBuilder()
      .defaults(command)
      .processOutput(processOutput)
      .artifactStore(new ArtifactStoreBuilder()
      .defaults(command)
      .download(new DownloadConfigBuilder()
      .defaultsForCommand(command)
      .artifactStorePath(artifactStorePath))
      .executableNaming(executableNaming))
      .build()

  val mongodConfig =
    new MongodConfigBuilder()
      .version(version)
      .net(new Net(port, localHostIPV6))
      .cmdOptions(new MongoCmdOptionsBuilder()
      .syncDelay(1)
      .useNoPrealloc(false)
      .useSmallFiles(false)
      .useNoJournal(false)
      .enableTextSearch(true)
      .build())
      .build()

  lazy val mongodStarter = MongodStarter.getInstance(runtimeConfig)
  lazy val mongod = mongodStarter.prepare(mongodConfig)
  lazy val mongodExe = mongod.start()

  val duration = 10.seconds
  var accountingSystem: ActorRef = _

  override def beforeAll() = {
    mongodExe
    accountingSystem = system.actorOf(Props[AccountingSystem])
  }

  override def afterAll() = {
    system.shutdown()
    system.awaitTermination(duration)
    mongod.stop()
    mongodExe.stop()
  }

  val invalidAccountId = "xxx-xxx-xxx"
  val validAccountHolderName = "Krisztian Lachata"
  val validAddress = "UK"

  "The AccountingSystem" must {
    "when issued a CreateAccountCmd command with an invalid accountId, generate an AccountCreationFailedEvt event" in {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountCreationFailedEvt])
      accountingSystem ! CreateAccountCmd(AccountNumber(invalidAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      val result = probe.expectMsgType[AccountCreationFailedEvt]

      result.errors must have size 1
    }
    "when issued a validated CreateAccountCmd command, generate a persisted AccountCreatedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountCreatedEvt])
      accountingSystem ! CreateAccountCmd(AccountNumber(validAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      val result = probe.expectMsgType[AccountCreatedEvt]

      result.accountNumber mustEqual (AccountNumber(validAccountId, Type.Current, Currency.GBP))
      result.accountHolder mustEqual (AccountHolder(validAccountHolderName, validAddress))
    }
    "when issued a CreateAccountCmd command where the account already exists, generate an AccountCreationFailedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountCreationFailedEvt])
      val cmd = CreateAccountCmd(AccountNumber(validAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      accountingSystem ! cmd
      accountingSystem ! cmd
      val result = probe.expectMsgType[AccountCreationFailedEvt]

      result.errors must have size 1
    }
    "when issued a DeactivateAccountCmd command where the account doesn't exist, generate an AccountDeactivationFailedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountDeactivationFailedEvt])
      accountingSystem ! DeactivateAccountCmd(validAccountId)
      val result = probe.expectMsgType[AccountDeactivationFailedEvt]

      result.errors must have size 1
    }
    "when issued a valid DeactivateAccountCmd command where the account exists, generate a persisted AccountDeactivatedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountDeactivatedEvt])
      accountingSystem ! CreateAccountCmd(AccountNumber(validAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      accountingSystem ! DeactivateAccountCmd(validAccountId)
      probe.expectMsgType[AccountDeactivatedEvt]
    }
    "when issued a ReactivateAccountCmd command where the account doesn't exist, generate an AccountReactivationFailedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountReactivationFailedEvt])
      accountingSystem ! ReactivateAccountCmd(validAccountId)
      val result = probe.expectMsgType[AccountReactivationFailedEvt]

      result.errors must have size 1
    }
    "when issued a ReactivateAccountCmd command where the account is active, generate an AccountReactivationFailedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountReactivationFailedEvt])
      accountingSystem ! CreateAccountCmd(AccountNumber(validAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      accountingSystem ! ReactivateAccountCmd(validAccountId)
      val result = probe.fishForMessage(3 second) {
        case evt: AccountReactivationFailedEvt => true
        case _ => false
      }

      result.asInstanceOf[AccountReactivationFailedEvt].errors must have size 1
    }
    "when issued a valid ReactivateAccountCmd command where the account is deactivated, generate an AccountReactivatedEvt event" in new scope {
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[AccountReactivatedEvt])
      accountingSystem ! CreateAccountCmd(AccountNumber(validAccountId, Type.Current, Currency.GBP), AccountHolder(validAccountHolderName, validAddress))
      accountingSystem ! DeactivateAccountCmd(validAccountId)
      accountingSystem ! ReactivateAccountCmd(validAccountId)
      probe.fishForMessage(3 second) {
        case evt: AccountReactivatedEvt => true
        case _ => false
      }
    }
  }

  private trait scope {

    val validAccountId: String = {
      val r = new scala.util.Random
      "xxxxxxxx-xxxxxxxx-xxxxxxxx".map {
        case x: Char if x == 'x' => r.nextInt(9).toString
        case x => x.toString
      }.mkString("")
    }
  }

}

