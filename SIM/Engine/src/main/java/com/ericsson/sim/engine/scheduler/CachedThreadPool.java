package com.ericsson.sim.engine.scheduler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedThreadPool implements ThreadPool {

    private static final Logger logger = LogManager.getLogger(CachedThreadPool.class);

    private ThreadPoolExecutor executorService;
    private final int initialSize;
    private final int maxSize = Integer.MAX_VALUE;

    private String instanceId = "";
    private String instanceName = "";

    private final int priority;
    private final boolean makeThreadsDaemons = false;

    public CachedThreadPool() {
        this(1, Thread.NORM_PRIORITY);
    }

    public CachedThreadPool(int initialSize, int threadPriority) {
        this.initialSize = initialSize;
        this.priority = threadPriority;
        logger.info("Cached thread pool will be used with initial size '{}' and priority '{}' (1 is lowest and 10 is highest)", initialSize, priority);
    }

    @Override
    public boolean runInThread(Runnable runnable) {
        if (runnable == null) {
            logger.warn("Runnable is null. Cannot run the task");
            return false;
        } else if (executorService.isShutdown()) {
            logger.warn("Executor has been shutdown, cannot submit the task");
            return false;
        }
        try {
            logger.debug("Submitting task for execution");
            executorService.submit(new CachedThreadPoolTask(runnable));
        } catch (RejectedExecutionException e) {
            logger.error("Task submission was rejected.", e);
            return false;
        } catch (Throwable t) {
            logger.error("An unhandled error occurred while submitting task", t);
            return false;
        }
        return true;
    }

    /**
     * Determines the number of threads that are currently available in the pool. Useful for determining the number of times runInThread(Runnable) can be called before returning false.
     * The implementation of this method should block until there is at least one available thread.
     * <p>
     * There will always be 1 thread available when using cached thread pool
     *
     * @return the number of currently available threads
     */
    @Override
    public int blockForAvailableThreads() {
        return 1;
    }

    @Override
    public void initialize() throws SchedulerConfigException {
        if (executorService == null ||
                (executorService.isTerminated() || executorService.isShutdown())) {
            executorService = new ThreadPoolExecutor(initialSize, maxSize,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    new CachedThreadPoolFactory(this),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    @Override
    public void shutdown(boolean waitForJobsToComplete) {
        if (waitForJobsToComplete) {
            Collection<Runnable> collection = new LinkedList<>();
//            executorService.getQueue().drainTo(collection);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60L, TimeUnit.SECONDS)) {
                    logger.error("Executor hasn't shutdown within {} sec timeout", 60);
                }
            } catch (InterruptedException ignored) {
                logger.error("Executor shutdown call was interrupted");
                Thread.currentThread().interrupt();
            }
        } else {
            executorService.shutdownNow();
        }
    }

    @Override
    public int getPoolSize() {
        return maxSize;
    }

    @Override
    public void setInstanceId(String schedInstId) {
        logger.debug("Setting instance Id: {}", schedInstId);
        this.instanceId = schedInstId;
    }

    @Override
    public void setInstanceName(String schedName) {
        logger.debug("Setting instance name: {}", schedName);
        this.instanceName = schedName;
    }

    private static class CachedThreadPoolFactory implements ThreadFactory {
        private final CachedThreadPool pool;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public CachedThreadPoolFactory(CachedThreadPool pool) {
            this.pool = pool;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, pool.instanceName + "-T-" + threadNumber.getAndIncrement());
            t.setDaemon(pool.makeThreadsDaemons);
            t.setPriority(pool.priority);
            return t;
        }
    }

    private static class CachedThreadPoolTask implements Runnable {
        private final Runnable innerTask;

        public CachedThreadPoolTask(Runnable innerTask) {
            this.innerTask = innerTask;
        }

        @Override
        public void run() {

            if (innerTask != null) {
                logger.debug("Calling inner runnable task");
                try {
                    innerTask.run();
                } catch (Throwable t) {
                    logger.error("Unhandled error while handling a task.", t);
                }
            } else {
                logger.warn("Inner runnable task is null, nothing to do");
            }
        }
    }
}
