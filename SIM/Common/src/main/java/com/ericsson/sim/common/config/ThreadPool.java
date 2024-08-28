package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

import java.util.concurrent.TimeUnit;

public class ThreadPool {
    int minSize = Constants.THREADPOOL_MIN_SIZE;
    int maxSize = Constants.THREADPOOL_MAX_SIZE;
    long idleTimeout = Constants.THREADPOOL_IDLE_SEC;
    int awaitTermination = Constants.THREADPOOL_AWAIT_TERMINATION;

    //gson will ignore this. this is internal
    final transient TimeUnit timeoutUnit = TimeUnit.SECONDS;
    final transient TimeUnit terminationTimeUnit = TimeUnit.SECONDS;

    public int getMinSize() {
        return minSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public int getAwaitTermination() {
        return awaitTermination;
    }

    public TimeUnit getTimeoutUnit() {
        return timeoutUnit;
    }

    public TimeUnit getTerminationTimeUnit() {
        return terminationTimeUnit;
    }
}
