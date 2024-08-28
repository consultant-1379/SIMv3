package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.Properties;
import com.ericsson.sim.engine.nodetype.model.DataType;

public abstract class BaseConverter<T> implements Converter<T> {
    @Override
    public final T convert(String value, Properties property) throws ConverterException {
        if (property.isRequired() && (value == null || value.isEmpty())) {
            throw new ConverterException("Value for required property '" + property.getName() + "' not provided");
        } else if (property.getType() == null) {
            throw new ConverterException("Field 'type' for a property is null. Cannot process it");
        }

        return convertImpl(property.getName(), value, property.getType());
    }

    protected abstract T convertImpl(String propertyName, String value, DataType dataType) throws ConverterException;
}
