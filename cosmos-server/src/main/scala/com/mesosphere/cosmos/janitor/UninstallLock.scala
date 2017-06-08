package com.mesosphere.cosmos.janitor

import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mesosphere.cosmos.janitor.CuratorUninstallLock._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import org.slf4j.Logger

/**
  * Allows a cosmos instance to acquire a lock on a particular uninstall.
  */
trait UninstallLock {
  /**
    * Checks if this uninstall is already locked by this JVM
    * @param appId
    * @return True if this app is locked by this JVM
    */
  def isLockedByThisProcess(appId: AppId): Boolean
  def lock(appId: AppId): Boolean
  def unlock(appId: AppId): Unit
}

/**
  * Implements UninstallLock using Curator (ZK)
  */
final class CuratorUninstallLock(curator: CuratorFramework) extends UninstallLock {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private var locksCache = Map[AppId, InterProcessMutex]()

  /**
    * InterProcessMutex locks threads rather than whole processes (jvms). As such, a single thread
    * executor is used here to extend the locking to the whole process.
    */
  val lockExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("uninstall-lock-thread")
    .build())

  private def lockOnThread(mutex: InterProcessMutex): Boolean = {
    val lockCallable = new Callable[Boolean] {
      override def call(): Boolean = {
        mutex.acquire(CuratorUninstallLock.AcquireTimeout, TimeUnit.MILLISECONDS)
      }
    }
   lockExecutor.submit(lockCallable).get()
  }

  private def unlockOnThread(mutex: InterProcessMutex): Unit = {
    val unlockCallable = new Callable[Unit] {
      override def call(): Unit = {
        mutex.release()
      }
    }
    lockExecutor.submit(unlockCallable).get()
  }

  override def isLockedByThisProcess(appId: AppId): Boolean = {
    synchronized {
      locksCache.get(appId) match {
        case Some(lock) => lock.isAcquiredInThisProcess
        case _ => false
      }
    }
  }

  override def lock(appId: AppId): Boolean = {
    logger.info("Attempting to acquire lock for {}", getLockPath(appId))
    synchronized {
      val lock = locksCache.get(appId) match {
        case Some(existed) => existed
        case None => new InterProcessMutex(curator, getLockPath(appId))
      }

      if (lockOnThread(lock)) {
        logger.info("Acquired lock for {}", getLockPath(appId))
        // Add the lock to the cache. In the case that it already existed, we're
        // simply rewriting it with itself.
        locksCache += (appId -> lock)
        true
      } else {
        logger.info("Lock for {} not acquired. Already owned", getLockPath(appId))
        // Do not add the lock to the cache. It isn't acquired.
        false
      }
    }
  }

  override def unlock(appId: AppId): Unit = {
    logger.info("Attempting to release lock for {}", getLockPath(appId))
    synchronized {
      locksCache.get(appId) match {
        case Some(existed) =>
          unlockOnThread(existed)
          // Remove the lock from the cache.
          locksCache -= appId
          logger.info("Released lock for {}", getLockPath(appId))
        case _ =>
          logger.info("Lock does not exist for {}. Nothing to unlock.", getLockPath(appId))
      }
    }
  }
}

object CuratorUninstallLock {
  val AcquireTimeout = 0L

  private def getLockPath(appId: AppId): String = {
    Paths.get(SdkJanitor.UninstallFolder, appId.toString, "lock").toString
  }
}