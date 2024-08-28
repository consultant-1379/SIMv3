package com.ericsson.sim.plugins.model;

public interface Config {
    String getNodeName();

    void addProperty(String name, Object value);

    boolean hasProperty(String name);

    String getPropertyAsString(String name);

    String getPropertyAsString(String name, String defaultValue);

    int getPropertyAsInteger(String name);

    int getPropertyAsInteger(String name, int defaultValue);

    boolean getPropertyAsBoolean(String name);

    boolean getPropertyAsBoolean(String name, boolean defaultValue);

    void clear();

}
