package com.ericsson.sim.engine.nodetype.model.converter;

import com.ericsson.sim.engine.nodetype.model.DataType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class StringConverter extends BaseConverter<String> {
    private static final Logger logger = LogManager.getLogger(StringConverter.class);

    public static final StringConverter instance = new StringConverter();

    private StringConverter() {
    }

    @Override
    public String convertImpl(String propertyName, String value, DataType dataType) throws ConverterException {
        if (value == null || value.isEmpty()) {
            logger.debug("Value for property {} is null or empty, using defaultString {}", propertyName, dataType.getDefaultString());
            if (dataType.isRegex()) {
                verifyRegex(propertyName, dataType.getDefaultString());
            }
            return dataType.getDefaultString();
        }
        if (dataType.isRegex()) {
            verifyRegex(propertyName, value);
        }
        if (dataType.isDatetime()) {
            verifyDatetime(propertyName, value);
        }
        if (dataType.getValidStrings() != null) {
            logger.debug("Checking if value {} for property {} is part of valid strings: {}", value, propertyName, dataType.getValidStrings());
            if (!dataType.getValidStrings().contains(value)) {
                logger.warn("Value {} is not in list of valid strings {} for property {}", value, dataType.getValidStrings(), propertyName);
                throw new ConverterException(value + " is not in list of valid strings for property " + propertyName);
            }
        }

        return value;
    }

    private void verifyDatetime(String propertyName, String format) throws ConverterException {
        logger.debug("Verifying datetime {} for property {} is valid format", format, propertyName);

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            formatter.format(LocalDateTime.now());
        } catch (IllegalArgumentException | DateTimeException e) {
            logger.warn("Failed to verify datetime format for property " + propertyName, e);
            throw new ConverterException("Datetime format " + format + " for property " + propertyName + " is not a valid format. " + e.getMessage());
        }

    }

    private void verifyRegex(String propertyName, String regex) throws ConverterException {
        logger.debug("Verifying regex {} for property {} is valid regex", regex, propertyName);

        //check if regex is valid or not first
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            logger.warn("Failed to verify regex for property " + propertyName, e);
            throw new ConverterException("Regex " + regex + " for property " + propertyName + " is not a valid regex expression. " + e.getMessage());
        }
//        if (!regex.matches(regex)) {
//            logger.warn("Failed to match {} with regex {} for property {}", regex, regex, propertyName);
//            throw new ConverterException(regex + " does not match the provided regex for type " + regex + " for property " + propertyName);
//        }
    }
}
