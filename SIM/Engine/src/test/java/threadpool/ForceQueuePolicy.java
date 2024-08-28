package threadpool;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class ForceQueuePolicy implements RejectedExecutionHandler {
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        try {
            executor.getQueue().put(runnable);
        } catch (InterruptedException e) {
            //should never happen since we never wait
            throw new RejectedExecutionException(e);
        }
    }
}
