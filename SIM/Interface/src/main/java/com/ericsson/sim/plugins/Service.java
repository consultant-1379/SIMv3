package com.ericsson.sim.plugins;

public interface Service {
    void startup() throws RuntimeException;

    void shutdown() throws RuntimeException;

    boolean isShutdown();
}
