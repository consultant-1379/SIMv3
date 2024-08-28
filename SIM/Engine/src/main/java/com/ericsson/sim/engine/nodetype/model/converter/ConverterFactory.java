package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.Properties;

public class ConverterFactory {

    private static final ConverterFactory instance = new ConverterFactory();

    private ConverterFactory() {
    }

    public static ConverterFactory getInstance() {
        return instance;
    }

    public Converter<?> getConverter(Properties property) throws ConverterException {
        Converter<?> converter = null;

        //TODO: Regex and Date are subtype of String. They should be own types with their own converter
        if ("string".equalsIgnoreCase(property.getType().getType())) {
            converter = StringConverter.instance;
        } else if ("integer".equalsIgnoreCase(property.getType().getType())) {
            converter = IntegerConverter.instance;
        } else if ("boolean".equalsIgnoreCase(property.getType().getType())) {
            converter = BooleanConverter.instance;
        } else {
            throw new ConverterException("Unknown type " + property.getType().getType() + " for property " + property.getName());
        }

        return converter;
    }
}
