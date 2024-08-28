package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.Properties;

public interface Converter<T> {
    T convert(String value, Properties property) throws ConverterException;
}
