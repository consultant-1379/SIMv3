package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.DataType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IntegerConverter extends BaseConverter<Integer> {
    private static final Logger logger = LogManager.getLogger(IntegerConverter.class);

    public static final IntegerConverter instance = new IntegerConverter();

    private IntegerConverter() {
    }

    @Override
    public Integer convertImpl(String propertyName, String value, DataType dataType) throws ConverterException {
        if (value == null || value.isEmpty()) {
            logger.debug("Value for {} is null or empty, returning defaultValue {}", propertyName, dataType.getDefaultValue());
            return dataType.getDefaultValue();
        }
        int intValue = 0;
        try {
            intValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConverterException("Failed to convert " + value + " to integer for property " + propertyName);
        }
        if (dataType.getValidValues() != null) {
            logger.debug("Checking if value {} is part of valid values: {}", value, dataType.getValidValues());
            if (!dataType.getValidValues().contains(intValue)) {
                logger.warn("Value {} is not in list of valid values {} for property {}", value, dataType.getValidValues(), propertyName);
                throw new ConverterException(value + " is not in list of valid values for property " + propertyName);
            }
        }
        if (intValue < dataType.getMinValue()) {
            logger.warn("{} is smaller than required minimum value {} for property {}", intValue, dataType.getMinValue(), propertyName);
            throw new ConverterException(value + " is smaller than required minimum value " + dataType.getMinValue() + " for property " + propertyName);
        } else if (intValue > dataType.getMaxValue()) {
            logger.warn("{} is greater than maximum possible value {} for property {}", intValue, dataType.getMinValue(), propertyName);
            throw new ConverterException(value + " is greater than maximum possible value " + dataType.getMaxValue() + " for property " + propertyName);
        }

        return intValue;
    }
}
