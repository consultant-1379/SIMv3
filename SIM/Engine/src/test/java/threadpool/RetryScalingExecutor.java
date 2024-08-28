package threadpool;

import com.ericsson.sim.common.worker.Worker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class RetryScalingExecutor<T extends Worker> extends ScalingThreadPoolExecutor {
    public RetryScalingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<T> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }


    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        System.out.println("Type: " + (r == null ? "null" : r.getClass().getSimpleName()));

        if (r instanceof Worker) {
            Worker worker = (Worker) r;
            switch (worker.getState()) {
                case INTERRUPTED:
                    System.out.println("Interrupted!");
                    break;
                case COMPLETED:
                    System.out.println("Completed");
                    break;
                case NO_RETRIES_LEFT:
                    System.out.println("No retires left");
                    break;
                case RUNNING:
                    System.out.println("Task is in running state. This cannot happen");
                    break;
                case EXECUTION_FAILURE:
                    //schedule it again
                    this.execute(worker);
            }
        }
    }
}
