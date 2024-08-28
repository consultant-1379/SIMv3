package com.ericsson.sim.engine.nodetype.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * '
 * GSON POJO for reading types
 */
public class NodeType {
    private String name;
    private String baseType;
    private boolean internal;
    private List<Properties> properties;
    private boolean isMerged = false;

    private HashMap<String, Properties> propertiesMap = null;

    public String getName() {
        return name;
    }

    public String getBaseType() {
        return baseType;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isMerged() {
        return isMerged;
    }

    public void setMerged(boolean merged) {
        isMerged = merged;
    }

    public HashMap<String, Properties> getProperties() {
        synchronized (this) {
            if (propertiesMap == null && properties != null) {
                propertiesMap = (HashMap<String, Properties>) properties.stream().collect(Collectors.toMap(Properties::getName, s -> s));
                properties.clear();
            }
        }

        return propertiesMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeType nodeType = (NodeType) o;
        return name.equals(nodeType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "NodeType {\n" +
                "\tname='" + name + "'\n" +
                "\t, baseNode=" + baseType + '\n' +
                "\t, internal=" + internal + '\n' +
                "\t, properties=\n" + Arrays.toString(getProperties().entrySet().toArray()) + '\n' +
                '}';
    }

}

