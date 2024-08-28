package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

public class Rmi {
    String serverName = Constants.RMI_SERVER_NAME;
    int registryPort = Constants.RMI_REGISTRY_PORT;

    public String getServerName() {
        return serverName;
    }

    public int getRegistryPort() {
        return registryPort;
    }
}
