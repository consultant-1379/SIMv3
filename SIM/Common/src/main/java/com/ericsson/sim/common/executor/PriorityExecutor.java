package com.ericsson.sim.common.executor;

import com.ericsson.sim.plugins.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * TODO: Following is left todo:
 * 1- Let a thread join the progress for a task, and return with list of completed tasks
 * 2- Someway to check which tasks have completed and get a result for its execution independent of this executor
 * 3- Make sure that shutdown let current tasks for complete and return the status for calling threads to do something on it
 * 4- Await termination to make sure tasks complete
 */

public class PriorityExecutor implements Service, Runnable {
    private static final Logger logger = LogManager.getLogger(PriorityExecutor.class);

    private final LinkedBlockingQueue<InnerTask<Runnable>> blockingQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<TaskResult> completionQueue = new LinkedBlockingQueue<>();
    private final Thread serviceThread = new Thread(this);

    private ThreadPoolExecutor executor = null;
    private final String SHUTDOWN_ID = UUID.randomUUID().toString();
    private final InnerTask<Runnable> SHUTDOWN_TASK = new InnerTask<>(SHUTDOWN_ID, null, 1, 0);


    private static final int reportOn = 1000;

    public PriorityExecutor(int minSize, int maxSize, long idleTimeout, TimeUnit timeUnit) {
        logger.info("Initializing scheduler");
//        executor = new RetryExecutor(configService.getAppConfig().getThreadPool().getMinSize(),
//                configService.getAppConfig().getThreadPool().getMaxSize(),
//                configService.getAppConfig().getThreadPool().getIdleTimeout(),
//                configService.getAppConfig().getThreadPool().getTimeUnit());

        executor = new RetryExecutor(minSize, maxSize, idleTimeout, timeUnit);

    }

    public int getCorePoolSize() {
        return executor != null ? executor.getCorePoolSize() : 0;
    }

    public int getActiveCount() {
        return executor != null ? executor.getActiveCount() : 0;
    }

    public int getQueueSize() {
        return executor != null ? executor.getQueue().size() : 0;
    }

    public long getCompletedTaskCount() {
        return executor != null ? executor.getCompletedTaskCount() : 0;
    }

    private static class InnerTask<T extends Runnable> {
        private final String id;
        private final ArrayDeque<T> list;
        private final List<T> completed = new LinkedList<>();
        private final int priority;
        private final long stopAt;
        private int count = 0;

        private InnerTask(String id, ArrayDeque<T> list, int priority, long stopAt) {
            this.id = id;
            this.list = list;
            this.priority = priority;
            this.stopAt = stopAt;
        }
    }

    public interface TaskResult<T> {
        T getResult();

        boolean success();
    }

    /**
     * Registers list of callable with queue.
     */
    public synchronized void register(String id, Runnable runnable, int priority, long stopAt) {
        List<Runnable> list = new ArrayList<>();
        list.add(runnable);
        register(id, list, priority, stopAt);
    }

    public synchronized void register(String id, Collection<Runnable> list, int priority, long stopAt) {
        Objects.requireNonNull(list);
        logger.info("Adding queue of for {} of size {}", id, list.size());
        InnerTask<Runnable> innerTask = new InnerTask<Runnable>(id, new ArrayDeque<>(list), priority, stopAt);
        blockingQueue.add(innerTask);
    }

    @Override
    public synchronized void startup() throws RuntimeException {
        logger.info("Starting up PriorityExecutor");
        serviceThread.setPriority(8);
        serviceThread.setName("PriorityExecutor");
        serviceThread.start();
    }

    @Override
    public synchronized void shutdown() {
        List<InnerTask<Runnable>> remainingItems = new ArrayList<>(blockingQueue.size());
        blockingQueue.drainTo(remainingItems);
        blockingQueue.add(SHUTDOWN_TASK);
        List<Runnable> runnable = executor.shutdownNow();
        logger.debug("{} tasks were awaiting execution when executor was shutdown", runnable.size());
        for (InnerTask<Runnable> innerTask : remainingItems) {
            if (innerTask.list != null) {
                logger.info("{} had {} tasks when executor thread is shutdown. Any remaining tasks will be ignored", innerTask.id, innerTask.list.size());
            }
        }
    }

    public synchronized boolean isShutdown() {
        return !serviceThread.isAlive() && executor.isShutdown();
    }

    public List<TaskResult> join() {
        return null;
    }

    @Override
    public void run() {
        while (true) {
            try {
                //Take item from the queue or block if its empty

                InnerTask<Runnable> innerTask = blockingQueue.take();

                //A special shutdown task is added, we are shutting down now
                if (innerTask.list == null || SHUTDOWN_ID.equals(innerTask.id)) {
                    logger.info("Call to shutdown the executor thread. No more tasks will be handled");
                    return;
                }
                //If the executor was shutdown, there isn't anything much we can do
                if (executor.isShutdown()) {
                    logger.error("Executor is shutdown, closing executor thread as well");
                    return;
                }

                //Check if there are tasks in selected queue
                int priority = Math.max(innerTask.priority, 1);
                for (int i = 0; i < priority; i++) {
                    Runnable item = innerTask.list.poll();
                    if (item != null) {
                        //SynchronousQueue on the other side will make sure that when execute returns, there was
                        //a thread that have taken the task.
                        executor.execute(item);
                    } else if (innerTask.list.size() <= 0) {
                        break;
                    }
                }
                if (innerTask.list.size() <= 0) {
                    logger.info("Task list for {} is empty now. Removing it from queue", innerTask.id);
                } else {
                    if (innerTask.count % reportOn == 0) {
                        logger.info("For {}, {} tasks are completed out of {}", innerTask.id, innerTask.count, innerTask.list.size());
                    }
                    //Queue again only if there are still items in it
                    innerTask.count++;
                    if (innerTask.stopAt <= System.currentTimeMillis()) {
                        logger.info("Task {} will stop now as it has reached its maximum set time", innerTask.id);
                    } else {
                        blockingQueue.add(innerTask);
                    }
                }
            } catch (InterruptedException ignored) {
            }
        }
    }
}
