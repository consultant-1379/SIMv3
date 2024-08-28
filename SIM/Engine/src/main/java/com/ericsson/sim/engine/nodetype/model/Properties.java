package com.ericsson.sim.engine.nodetype.model;

import java.util.Objects;

public class Properties {
    private String name;
    private boolean primary;
    private boolean required;
    private DataType type;

    public String getName() {
        return name;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isRequired() {
        return required;
    }

    public DataType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Properties that = (Properties) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Properties {\n" +
                "\tname='" + name + '\'' + '\n' +
                "\t, primary='" + primary + '\'' + '\n' +
                "\t, required=" + required + '\n' +
                "\t, type=" + type + '\n' +
                '}';
    }
}
