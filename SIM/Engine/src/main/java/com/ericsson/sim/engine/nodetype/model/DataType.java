package com.ericsson.sim.engine.nodetype.model;

import java.util.List;

public class DataType {

    private String type;
    private String defaultString;
    private boolean regex = false;
    private boolean datetime = false;
    private List<String> validStrings;

    private int defaultValue = 0;
    private int minValue = 0;
    private int maxValue = Integer.MAX_VALUE;
    private List<Integer> validValues;

    public String getType() {
        return type;
    }

    public String getDefaultString() {
        return defaultString;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isDatetime() {
        return datetime;
    }

    public List<String> getValidStrings() {
        return validStrings;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public List<Integer> getValidValues() {
        return validValues;
    }


    @Override
    public String toString() {
        return "DataType{" +
                "type='" + type + '\'' +
                ", defaultString='" + defaultString + '\'' +
                ", regex=" + regex +
                ", datetime=" + datetime +
                ", validStrings=" + validStrings +
                ", defaultValue=" + defaultValue +
                ", minValue=" + minValue +
                ", maxValue=" + maxValue +
                ", validValues=" + validValues +
                '}';
    }
}
