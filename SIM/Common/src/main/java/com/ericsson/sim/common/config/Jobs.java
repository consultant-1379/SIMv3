package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

import java.util.concurrent.TimeUnit;

public class Jobs {
    int batchSize = Constants.BATCH_SIZE;
    int windDownTime = Constants.MARGIN_TO_NEXT_EXECUTION;
    transient TimeUnit windDownTimeUnit = TimeUnit.SECONDS;

    public int getBatchSize() {
        return batchSize;
    }

    public int getWindDownTime() {
        return windDownTime;
    }

    public TimeUnit getWindDownTimeUnit() {
        return windDownTimeUnit;
    }
}
