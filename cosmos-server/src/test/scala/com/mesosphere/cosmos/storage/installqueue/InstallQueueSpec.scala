package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.InstallQueueError
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.PackageDefinition
import com.twitter.util.Await
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.KeeperException
import org.scalatest.Outcome
import org.scalatest.fixture

class InstallQueueSpec extends fixture.FreeSpec {

  import InstallQueueSpec._
  import InstallQueue._

  "Producer View" - {
    "Add an operation " - {
      "when no parent path exists" ignore { testParameters =>
        val (client, installQueue) = testParameters
        val addResult = Await.result(
          installQueue.add(coordinate1, universeInstall)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(client, coordinate1, Pending(universeInstall, None))
      }

      "when the parent path exists but the status does not" ignore { testParameters =>
        val (client, installQueue) = testParameters
        createParentPath(client)
        val addResult = Await.result(
          installQueue.add(coordinate1, universeInstall)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(
          client,
          coordinate1,
          Pending(universeInstall, None))
      }

      "on a coordinate that has a pending operation but no failures" ignore { testParameters =>
        val (client, installQueue) = testParameters
        val pendingUniverseInstall = Pending(universeInstall, None)

        insertPackageStatusIntoQueue(
          client,
          coordinate1,
          pendingUniverseInstall)

        val addResult = Await.result(
          installQueue.add(coordinate1, universeInstall)
        )
        assertResult(AlreadyExists)(addResult)

        checkInstallQueueContents(client,
          coordinate1,
          pendingUniverseInstall)
      }

      "on a coordinate that has a failed operation, but no pending operation" ignore { testParameters =>
        val (client, installQueue) = testParameters
        insertPackageStatusIntoQueue(
          client,
          coordinate1,
          Failed(OperationFailure(universeInstall, errorResponse1)))

        val addResult = Await.result(
          installQueue.add(coordinate1, universeInstall)
        )
        assertResult(Created)(addResult)

        checkInstallQueueContents(
          client,
          coordinate1,
          Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))
      }

      "on a coordinate that has an operation and a failure" ignore { testParameters =>
        val (client, installQueue) = testParameters

        val pendingUniverseInstallWithFailure =
          Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1)))

        insertPackageStatusIntoQueue(
          client,
          coordinate1,
          pendingUniverseInstallWithFailure)

        val addResult = Await.result(
          installQueue.add(coordinate1, universeInstall)
        )
        assertResult(AlreadyExists)(addResult)

        checkInstallQueueContents(
          client,
          coordinate1,
          pendingUniverseInstallWithFailure)
      }
    }

    "Add multiple non-conflicting operations" ignore { testParameters =>
      val (client, installQueue) = testParameters
      val addTwoOperations =
        for {
          add1 <- installQueue.add(coordinate1, universeInstall)
          add2 <- installQueue.add(coordinate2, universeInstall)
        } yield (add1, add2)

      val (add1, add2) = Await.result(addTwoOperations)
      assertResult(Created)(add1)
      assertResult(Created)(add2)

      checkInstallQueueContents(client, coordinate1, Pending(universeInstall, None))
      checkInstallQueueContents(client, coordinate2, Pending(universeInstall, None))
    }
  }

  "Processor view" - {
    "failure" - {
      "Fail an operation " - {
        "when no parent path exists" ignore { testParameters =>
          val (_, installQueue) = testParameters
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(notInQueueFailureMessageCoordinate1)(error.msg)
        }

        "when the parent path exists but the status does not" ignore { testParameters =>
          val (client, installQueue) = testParameters
          createParentPath(client)
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(notInQueueFailureMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has a pending operation but no failures" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(client, coordinate1, Pending(universeInstall, None))
          Await.result(
            installQueue.failure(coordinate1, errorResponse1)
          )
          checkInstallQueueContents(
            client,
            coordinate1,
            Failed(OperationFailure(universeInstall, errorResponse1)))
        }

        "on a coordinate that has a failed operation, but no pending operation" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Failed(OperationFailure(universeInstall, errorResponse1)))
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.failure(coordinate1, errorResponse1)
            )
          )
          assertResult(alreadyFailedFailureMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has an operation and a failure" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))
          Await.result(
            installQueue.failure(coordinate1, errorResponse2)
          )
          checkInstallQueueContents(
            client,
            coordinate1,
            Failed(OperationFailure(universeInstall, errorResponse2)))
        }
      }

      "Fail multiple non-conflicting operations" ignore { testParameters =>
        val (client, installQueue) = testParameters
        insertPackageStatusIntoQueue(client, coordinate1, Pending(universeInstall, None))
        insertPackageStatusIntoQueue(client, coordinate2, Pending(universeInstall, None))
        val failTwoOperations =
          for {
            _ <- installQueue.failure(coordinate1, errorResponse1)
            _ <- installQueue.failure(coordinate2, errorResponse1)
          } yield ()
        Await.result(failTwoOperations)
        checkInstallQueueContents(
          client,
          coordinate1,
          Failed(OperationFailure(universeInstall, errorResponse1)))
        checkInstallQueueContents(
          client,
          coordinate2,
          Failed(OperationFailure(universeInstall, errorResponse1)))
      }
    }

    "success" - {
      "Success on an operation " - {
        "when no parent path exists" ignore { testParameters =>
          val (_, installQueue) = testParameters
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.success(coordinate1)
            )
          )
          assertResult(notInQueueSuccessMessageCoordinate1)(error.msg)
        }

        "when the parent path exists but the status does not" ignore { testParameters =>
          val (client, installQueue) = testParameters
          createParentPath(client)
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.success(coordinate1)
            )
          )
          assertResult(notInQueueSuccessMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has a pending operation but no failures" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(client, coordinate1, Pending(universeInstall, None))
          Await.result(
            installQueue.success(coordinate1)
          )
          checkStatusDoesNotExist(client, coordinate1)
        }

        "on a coordinate that has a failed operation, but no pending operation" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Failed(OperationFailure(universeInstall, errorResponse1)))
          val error = intercept[InstallQueueError](
            Await.result(
              installQueue.success(coordinate1)
            )
          )
          assertResult(alreadyFailedSuccessMessageCoordinate1)(error.msg)
        }

        "on a coordinate that has an operation and a failure" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))
          Await.result(
            installQueue.success(coordinate1)
          )
          checkStatusDoesNotExist(client, coordinate1)
        }
      }

      "Success on multiple non-conflicting operations" ignore { testParameters =>
        val (client, installQueue) = testParameters
        insertPackageStatusIntoQueue(client, coordinate1, Pending(universeInstall, None))
        insertPackageStatusIntoQueue(client, coordinate2, Pending(universeInstall, None))
        val successOnTwoOperations =
          for {
            _ <- installQueue.success(coordinate1)
            _ <- installQueue.success(coordinate2)
          } yield ()
        Await.result(successOnTwoOperations)
        checkStatusDoesNotExist(client, coordinate1)
        checkStatusDoesNotExist(client, coordinate2)
      }
    }

    "next" - {
      "Next when " - {
        "no parent path exists" ignore { testParameters =>
          val (_, installQueue) = testParameters
          val nextPendingOperation = Await.result(
            installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "the parent path exists but there are no pending operations" ignore { testParameters =>
          val (client, installQueue) = testParameters
          createParentPath(client)
          val nextPendingOperation = Await.result(
            installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "there is a coordinate that has a pending operation but no failures" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Pending(universeInstall, None))
          val nextPendingOperation = Await.result(
            installQueue.next()
          )
          assertResult(
            Some(PendingOperation(coordinate1, universeInstall, None)))(
            nextPendingOperation)
        }

        "there is a coordinate that has a failed operation, but no pending operation" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Failed(OperationFailure(universeInstall, errorResponse1)))
          val nextPendingOperation = Await.result(
            installQueue.next()
          )
          assertResult(None)(nextPendingOperation)
        }

        "there is a coordinate that has an operation and a failure" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Pending(
              universeInstall,
              Some(OperationFailure(
                universeInstall, errorResponse1))))
          val nextPendingOperation = Await.result(
            installQueue.next()
          )
          assertResult(
            Some(PendingOperation(
              coordinate1,
              universeInstall,
              Some(OperationFailure(
                universeInstall, errorResponse1)))))(
            nextPendingOperation)
        }

        "there are multiple pending operations some of which have failed" ignore { testParameters =>
          val (client, installQueue) = testParameters
          insertPackageStatusIntoQueue(
            client,
            coordinate1,
            Pending(universeInstall, None))
          insertPackageStatusIntoQueue(
            client,
            coordinate2,
            Pending(universeInstall, None))
          insertPackageStatusIntoQueue(
            client,
            coordinate3,
            Failed(OperationFailure(universeInstall, errorResponse1)))
          insertPackageStatusIntoQueue(
            client,
            coordinate4,
            Failed(OperationFailure(universeInstall, errorResponse2)))
          insertPackageStatusIntoQueue(
            client,
            coordinate5,
            Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))

          val n1 = Await.result(installQueue.next())
          removePackageStatusFromQueue(client, coordinate1)

          val n2 = Await.result(installQueue.next())
          removePackageStatusFromQueue(client, coordinate2)

          val n3 = Await.result(installQueue.next())
          removePackageStatusFromQueue(client, coordinate5)

          val n4 = Await.result(installQueue.next())
          val n5 = Await.result(installQueue.next())

          assertResult(
            Some(PendingOperation(coordinate1, universeInstall, None)))(
            n1)
          assertResult(
            Some(PendingOperation(coordinate2, universeInstall, None)))(
            n2)
          assertResult(
            Some(PendingOperation(
              coordinate5,
              universeInstall,
              Some(OperationFailure(universeInstall, errorResponse1)))))(
            n3)
          assertResult(None)(n4)
          assertResult(None)(n5)
        }
      }

      "Calling next multiple times returns the same operation" ignore { testParameters =>
        val (client, installQueue) = testParameters
        insertPackageStatusIntoQueue(
          client,
          coordinate1,
          Pending(universeInstall, None))
        insertPackageStatusIntoQueue(
          client,
          coordinate2,
          Pending(universeInstall, None))
        insertPackageStatusIntoQueue(
          client,
          coordinate3,
          Failed(OperationFailure(universeInstall, errorResponse1)))
        insertPackageStatusIntoQueue(
          client,
          coordinate4,
          Failed(OperationFailure(universeInstall, errorResponse1)))
        insertPackageStatusIntoQueue(
          client,
          coordinate5,
          Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1))))

        val callNextTwice =
          for {
            n1 <- installQueue.next()
            n2 <- installQueue.next()
          } yield (n1, n2)
        val (n1, n2) = Await.result(callNextTwice)

        checkInstallQueueContents(client, coordinate1, Pending(universeInstall, None))
        removePackageStatusFromQueue(client, coordinate1)

        val callNextTwiceAgain =
          for {
            n3 <- installQueue.next()
            n4 <- installQueue.next()
          } yield (n3, n4)
        val (n3, n4) = Await.result(callNextTwiceAgain)

        assert(n1 == n2)
        assert(n3 == n4)
        assertResult(
          Some(PendingOperation(coordinate1, universeInstall, None)))(
          n1)
        assertResult(
          Some(PendingOperation(coordinate2, universeInstall, None)))(
          n3)
      }
    }
  }

  "Reader view" - {
    "ViewStatus should " +
      "return all pending and failed operations in the queue" ignore { testParameters =>
      val (client, installQueue) = testParameters
      val expectedState = Map(
        coordinate1 -> Pending(universeInstall, None),
        coordinate2 -> Pending(universeInstall, None),
        coordinate3 -> Failed(OperationFailure(universeInstall, errorResponse1)),
        coordinate4 -> Failed(OperationFailure(universeInstall, errorResponse1)),
        coordinate5 -> Pending(universeInstall, Some(OperationFailure(universeInstall, errorResponse1)))
      )

      expectedState.foreach { case (coordinate, status) =>
        insertPackageStatusIntoQueue(client, coordinate, status)
      }

      val pollCount = 10
      val expectedSize = 5
      val actualState = pollForViewStatus(installQueue, pollCount, expectedSize)

      assertResult(Some(expectedState))(actualState)
    }
  }

  "Install Queue" - {
    "When an operation is added on a failed coordinate," +
      " that coordinate must move to the back of the queue" ignore { testParameters =>
      val (_, installQueue) = testParameters
      val addOnCoordinate1 =
        Await.result(installQueue.add(coordinate1, universeInstall))
      assertResult(Created)(addOnCoordinate1)

      val addOnCoordinate2 =
        Await.result(installQueue.add(coordinate2, universeInstall))
      assertResult(Created)(addOnCoordinate2)

      val coordinate1Operation = Await.result(installQueue.next())
      assertResult(
        Some(PendingOperation(coordinate1, universeInstall, None)))(
        coordinate1Operation)

      Await.result(installQueue.failure(coordinate1, errorResponse1))
      val addOnCoordinate1AfterFailure =
        Await.result(installQueue.add(coordinate1, universeInstall))
      assertResult(Created)(addOnCoordinate1AfterFailure)

      val coordinate2Operation = Await.result(installQueue.next())
      assertResult(
        Some(
          PendingOperation(
            coordinate2,
            universeInstall,
            None)))(
        coordinate2Operation)
    }
  }

  type FixtureParam = (CuratorFramework, InstallQueue)

  override def withFixture(test: OneArgTest): Outcome = {
    val path = "/"
    val retries = 10
    val baseSleepTime = 1000
    val client = CuratorFrameworkFactory.newClient(
      path,
      new ExponentialBackoffRetry(baseSleepTime, retries)
    )
    client.start()
    client.getZookeeperClient.blockUntilConnectedOrTimedOut()

    try {
      withFixture(test.toNoArgTest((client, InstallQueue(client))))
    } finally {
      try {
        client.delete().deletingChildrenIfNeeded().forPath(installQueuePath)
        ()
      } catch {
        case e: KeeperException.NoNodeException =>
      }
    }
  }

  private def checkInstallQueueContents
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate,
    expected: OperationStatus
  ): Unit = {
    val data =
      client
        .getData
        .forPath(
          statusPath(coordinate1)
        )
    val operationStatus = Envelope.decodeData[OperationStatus](data)
    assertResult(expected)(operationStatus)
  }

  private def insertPackageStatusIntoQueue
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate,
    contents: OperationStatus
  ): Unit = {
    client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        statusPath(packageCoordinate),
        Envelope.encodeData(contents)
      )
    ()
  }

  private def removePackageStatusFromQueue
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate
  ): Unit = {
    client
      .delete()
      .forPath(
        statusPath(packageCoordinate)
      )
    ()
  }

  private def createParentPath
  (
    client: CuratorFramework
  ): Unit = {
    client
      .create()
      .creatingParentsIfNeeded()
      .forPath(
        installQueuePath
      )
    ()
  }

  private def checkStatusDoesNotExist
  (
    client: CuratorFramework,
    packageCoordinate: PackageCoordinate
  ): Unit = {
    val stat =
      Option(
        client
          .checkExists()
          .forPath(
            statusPath(packageCoordinate)
          )
      )
    assertResult(None)(stat)
  }

  private def pollForViewStatus
  (
    installQueue: InstallQueue,
    attempts: Int,
    size: Int
  ): Option[Map[PackageCoordinate, OperationStatus]] = {
    Stream.tabulate(attempts) { _ =>
      val oneSecond = 1000L
      Thread.sleep(oneSecond)
      Await.result(installQueue.viewStatus())
    }.dropWhile(_.size < size).headOption
  }

}

object InstallQueueSpec {
  private val coordinate1 =
    PackageCoordinate("coordinate", PackageDefinition.Version("1"))
  private val coordinate2 =
    PackageCoordinate("coordinate", PackageDefinition.Version("2"))
  private val coordinate3 =
    PackageCoordinate("coordinate", PackageDefinition.Version("3"))
  private val coordinate4 =
    PackageCoordinate("coordinate", PackageDefinition.Version("4"))
  private val coordinate5 =
    PackageCoordinate("coordinate", PackageDefinition.Version("5"))
  private val errorResponse1 =
    ErrorResponse("foo", "bar")
  private val errorResponse2 =
    ErrorResponse("abc", "def")
  private val notInQueueFailureMessageCoordinate1 =
    s"Attempted to signal failure on an " +
      s"operation not in the install queue: $coordinate1"
  private val alreadyFailedFailureMessageCoordinate1 =
    s"Attempted to signal failure on an " +
      s"operation that has failed: $coordinate1"
  private val notInQueueSuccessMessageCoordinate1 =
    s"Attempted to signal success on an " +
      s"operation not in the install queue: $coordinate1"
  private val alreadyFailedSuccessMessageCoordinate1 =
    s"Attempted to signal success on an " +
      s"operation that has failed: $coordinate1"
  private val universeInstall =
    UniverseInstall(TestingPackages.MinimalV3ModelV3PackageDefinition)

}