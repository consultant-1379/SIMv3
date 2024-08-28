package com.ericsson.sim.engine.server;

import com.ericsson.sim.engine.config.ConfigService;
import com.ericsson.sim.plugins.rmi.Server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SimServer extends Server {

    enum ServerErrors implements Remote {
        STARTED,
        APP_CONFIG_ERROR,
        NODE_TYPE_CONFIG_ERROR,
        NE_CONFIG_ERROR,
        GENERAL_ERROR,
        SCHEDULER_ERROR,
        UNKNOWN_ERROR
    }

    ServerErrors startServer(ConfigService configService) throws RuntimeException, RemoteException;

    void stopServer() throws RuntimeException, RemoteException;

    boolean safeStopServer() throws RuntimeException, RemoteException;
}
