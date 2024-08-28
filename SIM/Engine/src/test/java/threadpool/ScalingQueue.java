package threadpool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class ScalingQueue<E> extends LinkedBlockingQueue<E> {
    /**
     * The executor this Queue belongs to
     */
    private ThreadPoolExecutor executor;

    /**
     * Creates a TaskQueue with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public ScalingQueue() {
        super();
    }

    /**
     * Creates a TaskQueue with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue.
     */
    public ScalingQueue(int capacity) {
        super(capacity);
    }

    /**
     * Sets the executor this queue belongs to.
     */
    public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    /**
     * Inserts the specified element at the tail of this queue if there is at
     * least one available thread to run the current task. If all pool threads
     * are actively busy, it rejects the offer.
     *
     * @param object the element to add.
     * @return true if it was possible to add the element to this
     * queue, else false
     * @see ThreadPoolExecutor#execute(Runnable)
     */
    @Override
    public boolean offer(E object) {
        int allWorkingThreads = executor.getActiveCount() + super.size();
        return allWorkingThreads < executor.getPoolSize() && super.offer(object);
    }
}
