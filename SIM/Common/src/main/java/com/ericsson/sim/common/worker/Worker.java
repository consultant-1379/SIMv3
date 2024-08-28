package com.ericsson.sim.common.worker;


import java.util.Objects;

public abstract class Worker implements Runnable {

    public enum ExecutionState {
        COMPLETED,
        RUNNING,
        NO_RETRIES_LEFT,
        EXECUTION_FAILURE,
        INTERRUPTED
    }

    protected ExecutionState state;
    protected Throwable error = null;
    private int attempt = 0;

    @Override
    public final void run() {
        this.state = ExecutionState.RUNNING;
        if (!canRetry()) {
            setFailure(ExecutionState.NO_RETRIES_LEFT, new RuntimeException("No more retries left. Attempted " + attempt + " of total " + totalRetries() + " retries"));
            return;
        }
        attempt++;
        try {
            runEx();
            setSuccess();
        } catch (InterruptedException e) {
            setFailure(ExecutionState.INTERRUPTED, e);
        } catch (Throwable e) {
            setFailure(ExecutionState.EXECUTION_FAILURE, e);
        }
    }

    protected abstract void runEx() throws Exception;

    public final ExecutionState getState() {
        return state;
    }

    public final Throwable getError() {
        return error;
    }

    public boolean canRetry() {
        return attempt <= totalRetries();
    }

    public final int remainingAttempts() {
        return attempt;
    }

    public abstract int totalRetries();

    public abstract String getJobId();

    private void setSuccess() {
        this.state = ExecutionState.COMPLETED;
        this.error = null;
    }

    private void setFailure(ExecutionState errorCode, Throwable error) {
        this.state = errorCode;
        this.error = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Worker worker = (Worker) o;
        return getJobId() != null && getJobId().equals(worker.getJobId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getJobId());
    }

    @Override
    public String toString() {
        return "Worker{" +
                "state=" + state.name() +
                ", error=" + (error != null ? error.getMessage() : "null") +
                ", attempt=" + attempt +
                '}';
    }
}

