package com.ericsson.sim.plugins.policy;

import com.ericsson.sim.plugins.Plugin;

public interface HealthCheck extends Plugin {
    void check();
    boolean isOk();
}
