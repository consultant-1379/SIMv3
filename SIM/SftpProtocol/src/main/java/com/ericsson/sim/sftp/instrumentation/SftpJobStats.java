package com.ericsson.sim.sftp.instrumentation;

import com.ericsson.sim.plugins.instrumentation.JobStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SftpJobStats implements JobStats {
    private static final Logger logger = LogManager.getLogger(SftpJobStats.class);

    private final AtomicLong totalJobExecutionCount = new AtomicLong();

    private final AtomicInteger lastTasks = new AtomicInteger();
    private final AtomicInteger lastSuccessful = new AtomicInteger();
    private final AtomicInteger lastFailed = new AtomicInteger();
    private double lastJobTime = 0.0;

    private final AtomicLong totalTasks = new AtomicLong();
    private final AtomicLong totalSuccessful = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();

    public SftpJobStats() {
//        logger.info("New instance of instrumentation created", new Exception("Stacktrace:"));
        logger.info("New instance of instrumentation created");
    }

    public void setupJobStart() {
        this.lastTasks.set(0);
        this.lastSuccessful.set(0);
        this.lastFailed.set(0);
        lastJobTime = 0.0;
    }

    public void incrementJobExecutionCount() {
        this.totalJobExecutionCount.incrementAndGet();
    }

    public long getTotalJobExecutionCount() {
        return totalJobExecutionCount.get();
    }

    public int getLastExecutedTasks() {
        return lastTasks.get();
    }

    public void incrementLastExecutedTasks(int count) {
        this.lastTasks.addAndGet(count);
        this.totalTasks.addAndGet(count);
    }

    public int getLastSuccessful() {
        return lastSuccessful.get();
    }

    public void incrementLastSuccessful(int count) {
        this.lastSuccessful.addAndGet(count);
        this.totalSuccessful.addAndGet(count);
    }

    public int getLastFailed() {
        return lastFailed.get();
    }

    public void incrementLastFailed(int count) {
        this.lastFailed.addAndGet(count);
        this.totalFailed.addAndGet(count);
    }

    public synchronized double getLastJobTime() {
        return lastJobTime;
    }

    public synchronized void setLastJobTime(double timeInSec) {
        this.lastJobTime = timeInSec;
    }

    public long getTotalTasks() {
        return totalTasks.get();
    }

    public long getTotalSuccessful() {
        return totalSuccessful.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
    }

    @Override
    public String toString() {
        return "\n\tTotal Job Executions    = " + getTotalJobExecutionCount() +
                "\n\tLast Tasks Executed     = " + getLastExecutedTasks() +
                "\n\tLast Successful Tasks   = " + getLastSuccessful() +
                "\n\tLast Failed Tasks       = " + getLastFailed() +
                "\n\tLast Job Execution Time = " + getLastJobTime() + " sec" +
                "\n\tTotal Tasks Executed    = " + getTotalTasks() +
                "\n\tTotal Successful Tasks  = " + getTotalSuccessful() +
                "\n\tTotal Failed Tasks      = " + getTotalFailed();
    }
}
