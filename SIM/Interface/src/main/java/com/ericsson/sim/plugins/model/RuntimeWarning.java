package com.ericsson.sim.plugins.model;

public class RuntimeWarning extends Exception {
    public RuntimeWarning(String message) {
        super(message);
    }

    public RuntimeWarning(String message, Throwable cause) {
        super(message, cause);
    }
}
