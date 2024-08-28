package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.DataType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BooleanConverter extends BaseConverter<Boolean> {
    private static final Logger logger = LogManager.getLogger(BooleanConverter.class);

    public static final BooleanConverter instance = new BooleanConverter();

    private BooleanConverter() {
    }

    @Override
    public Boolean convertImpl(String propertyName, String value, DataType dataType) throws ConverterException {
        if (value == null || value.isEmpty()) {
            logger.debug("Value for {} is null or empty, returning defaultString {}", propertyName, dataType.getDefaultString());
            value = dataType.getDefaultString();
        }
//        try {
        if ("TRUE".equalsIgnoreCase(value)) {
            return true;
        } else if ("FALSE".equalsIgnoreCase(value)) {
            return false;
        } else {
            throw new ConverterException("Value must either be true or false (case insensitive). Wrong value: " + value + " for property " + propertyName);
        }
//        } catch (NumberFormatException e) {
//            throw new ConverterException("Failed to convert " + value + " to boolean for property " + propertyName);
//        }
    }
}
