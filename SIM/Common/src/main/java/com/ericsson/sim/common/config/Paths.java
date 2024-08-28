package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

public class Paths {
    String nodeTypes = Constants.PATHS.NODETYPES;
    String neConfig = Constants.PATHS.NE_CONFIG;
    String historyDb = Constants.PATHS.HISTORY_DB;
    String policyConfig = Constants.PATHS.POLICY_CONFIG;
    String privateKey;

    public String getNodeTypes() {
        return nodeTypes;
    }

    public String getNeConfig() {
        return neConfig;
    }

    public String getHistoryDb() {
        return historyDb;
    }

    public String getPolicyConfig() {
        return policyConfig;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
