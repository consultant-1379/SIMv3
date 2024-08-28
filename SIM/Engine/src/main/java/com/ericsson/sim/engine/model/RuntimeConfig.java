package com.ericsson.sim.engine.model;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.plugins.model.Config;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeConfig implements Config {
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private String nodeName = "";

    public String getNodeName() {
        return nodeName;
    }

    public void addProperty(String name, Object value) {
        if (Constants.CONFIG_NODE_NAME.equals(name) && value != null) {
            nodeName = value.toString();
        }
        this.properties.put(name, value);
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public String getPropertyAsString(String name) {
        return getPropertyAsString(name, null);
    }

    public String getPropertyAsString(String name, String defaultValue) {
        if (!properties.containsKey(name)) {
            return defaultValue;
        }
        Object value = properties.get(name);
        return value == null ? null : value.toString();
    }

    public int getPropertyAsInteger(String name) {
        return getPropertyAsInteger(name, 0);
    }

    public int getPropertyAsInteger(String name, int defaultValue) {
        if (!properties.containsKey(name)) {
            return defaultValue;
        }
        Object value = properties.get(name);
        return (int) value;
    }

    public boolean getPropertyAsBoolean(String name) {
        return getPropertyAsBoolean(name, false);
    }

    public boolean getPropertyAsBoolean(String name, boolean defaultValue) {
        if (!properties.containsKey(name)) {
            return defaultValue;
        }
        Object value = properties.get(name);
        return (boolean) value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuntimeConfig that = (RuntimeConfig) o;
        if (this.nodeName != null && !this.nodeName.equals(that.nodeName)) {
            return false;
        }
        for (Map.Entry<String, Object> entry : this.properties.entrySet()) {
            Object thisObj = entry.getValue();
            Object otherObj = that.properties.get(entry.getKey());
            if (thisObj != null && !thisObj.equals(otherObj)) {
                return false;
            } else if (thisObj == null && otherObj != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties.toString());
    }

    public void clear() {
        this.properties.clear();
    }

    @Override
    public String toString() {
        return "RuntimeConfig {\n" +
                "\tnodeName='" + nodeName + '\'' + '\n' +
                "\tproperties=" + Arrays.toString(properties.entrySet().toArray()) + '\n' +
                '}';
    }
}
