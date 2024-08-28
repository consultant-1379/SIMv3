package com.ericsson.sim.plugins.rmi;

import com.ericsson.sim.plugins.instrumentation.JobStats;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Date;

public class JobStatus implements Serializable {
    public final String jobName;
    public final String executionExpression;
    public final String executionSummary;
    public final Date nextExecution;
    public final JobStats stats;

    public JobStatus(String jobName, String cronExpression, String cronSummary, Date nextExecution, JobStats stats) throws RemoteException {
        super();
        this.jobName = jobName;
        this.executionExpression = cronExpression;
        this.executionSummary = cronSummary;
        this.nextExecution = nextExecution;
        this.stats = stats;
    }
}
