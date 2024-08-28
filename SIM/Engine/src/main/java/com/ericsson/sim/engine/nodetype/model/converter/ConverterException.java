package com.ericsson.sim.engine.nodetype.model.converter;

public class ConverterException extends Exception {
    public ConverterException(Exception e) {
        super(e);
    }

    public ConverterException(String s) {
        super(s);
    }

    public ConverterException(String s, Exception e) {
        super(s, e);
    }
}
