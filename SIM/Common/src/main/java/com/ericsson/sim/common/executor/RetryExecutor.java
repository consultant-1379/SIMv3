package com.ericsson.sim.common.executor;

import com.ericsson.sim.common.worker.Worker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

public class RetryExecutor extends ThreadPoolExecutor {

    private static final Logger logger = LogManager.getLogger(RetryExecutor.class);

    public RetryExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
//        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new ArrayBlockingQueue<>(50000), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
//        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public RetryExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
//        latch = new CountDownLatch(totalTasks);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        //TODO: It will be FutureTask when submit is used. Use that for retry as well
        super.afterExecute(r, t);
        if (r instanceof Worker) {
            Worker worker = (Worker) r;
            switch (worker.getState()) {
                case RUNNING:
                    logger.debug("Worker {} is in running state", worker.getJobId());
                    break;
                case COMPLETED:
//                    latch.countDown();
                    break;
                case NO_RETRIES_LEFT:
                    logger.error("No more retries left for {}. Error: {}", worker.getJobId(), worker.getError() != null ? worker.getError().getMessage() : "null");
//                    latch.countDown();
                    break;
                case EXECUTION_FAILURE:
                    logger.warn("Execution failure in {}. Error: {}", worker.getJobId(), worker.getError() != null ? worker.getError().getMessage() : "null");
                    logger.info(worker.getJobId(), "Doing retry...");
                    this.execute(worker);
            }
        }
//        else if (r instanceof FutureTask) {
//            FutureTask future = (FutureTask) r;
//            try {
//                Object value = future.get();
//            } catch (InterruptedException e) {
//                //Todo: check issues
//                Thread.currentThread().interrupt();
//            } catch (ExecutionException e) {
//                logger.warn(, );
//            }
//        }
        else {
            logger.debug("Task is not of type Worker. Cannot do retries. Type is: " + r.getClass().getName());
//            latch.countDown();
        }
    }


    @Override
    public String toString() {
        return super.toString();
    }
}
