package com.ericsson.sim.engine.policy.eniq;

import java.util.concurrent.TimeUnit;

class ConfigModel {

    public static final String DATA_LOCATION = "/eniq/data/pmdata/";
    public static final String ENGINE_CHECK = "/eniq/sw/bin/engine";
    public static final String[] ENGINE_CHECK_ARG = {"status"};
    public static final boolean CHECK_ENIQ_STATUS = true;
    public static final int MAX_SPACE_USED = 80;
    public static final boolean DEFAULT_STATE_ON_ERROR = true;
    public static final int CHECK_AFTER = 20;
    public static final TimeUnit CHECK_AFTER_TIMEUNIT = TimeUnit.SECONDS;

    String dataLocation = DATA_LOCATION;
    int maxPercentageUsedSpace = MAX_SPACE_USED;
    Boolean checkEniqStatus = CHECK_ENIQ_STATUS;
    Boolean defaultStateOnError = DEFAULT_STATE_ON_ERROR;
    int checkAfter = CHECK_AFTER;
    transient TimeUnit getCheckAfterTimeunit = CHECK_AFTER_TIMEUNIT;
    String engineCheckCommand = ENGINE_CHECK;
    String[] getEngineCheckArg = ENGINE_CHECK_ARG;
}
