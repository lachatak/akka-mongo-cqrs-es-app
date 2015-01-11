package org.kaloz.akkamongocqrses

import akka.actor.ActorSystem
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

object CqrsEs extends App {

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

  mongod.start()

  val system = ActorSystem("CqrsEs", config(freePort))

}
