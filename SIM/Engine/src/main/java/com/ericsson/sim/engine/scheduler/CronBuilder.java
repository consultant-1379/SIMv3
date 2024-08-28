package com.ericsson.sim.engine.scheduler;

import com.cronutils.mapper.CronMapper;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.field.CronField;
import com.cronutils.model.field.CronFieldName;
import com.cronutils.model.field.expression.FieldExpression;
import com.cronutils.parser.CronParser;
import com.ericsson.sim.common.Constants;
import com.ericsson.sim.engine.model.RuntimeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.CronExpression;

import java.text.ParseException;
import java.util.Map;

public class CronBuilder {
    private static final Logger logger = LogManager.getLogger(CronBuilder.class);

    private final static String ropCronFormat = "0 %s * ? * * *";

    public static CronExpression createExpression(String nodeName, RuntimeConfig runtimeConfig) {

        if (runtimeConfig.hasProperty(Constants.CONFIG_CRON_BASED_ROP)) {
            logger.debug("{} is provided, this value will be used instead of {}", Constants.CONFIG_CRON_BASED_ROP, Constants.CONFIG_CRON_ROP);
            return createUnixCronBasedCronExpression(nodeName, runtimeConfig);
        }
        logger.debug("Creating cron expression from {}", Constants.CONFIG_CRON_ROP);
        return createRopBasedCronExpression(nodeName, runtimeConfig);
    }

    private static CronExpression createUnixCronBasedCronExpression(String nodeName, RuntimeConfig runtimeConfig) {
        CronExpression cronExpression = null;

        try {
            String expression = runtimeConfig.getPropertyAsString(Constants.CONFIG_CRON_BASED_ROP);
            CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
            CronParser parser = new CronParser(cronDefinition);
            Cron unixCron = parser.parse(expression);
            unixCron.validate();

            Map<CronFieldName, CronField> map = unixCron.retrieveFieldsAsMap();
            CronField field = map.get(CronFieldName.DAY_OF_MONTH);
            FieldExpression fieldExpression = field.getExpression();
            if (!"*".equals(fieldExpression.asString())) {
                logger.error("For {}, DAY_OF_MONTH is not supported by scheduler. Use * instead of {}", nodeName, fieldExpression.asString());
                return null;
            }

            CronMapper cronMapper = CronMapper.fromUnixToQuartz();
            Cron quartzCron = cronMapper.map(unixCron);
            logger.debug("For {} converted UNIX expression: {} to QUARTZ: {}", nodeName, unixCron.asString(), quartzCron.asString());

            cronExpression = new CronExpression(quartzCron.asString());
            logger.debug("For {} cron expression will be {}", nodeName, cronExpression.getCronExpression());
        } catch (Exception e) {
            logger.error("Failed to create cron expression for scheduler", e);
            return null;
        }

        return cronExpression;
    }

    private static CronExpression createRopBasedCronExpression(String nodeName, RuntimeConfig runtimeConfig) {
        CronExpression cronExpression = null;

        if (!runtimeConfig.hasProperty(Constants.CONFIG_CRON_ROP)) {
            logger.error("{} is missing configuration '{}' required to schedule jobs", nodeName, Constants.CONFIG_CRON_ROP);
            return null;
        }

        try {
            int rop = runtimeConfig.getPropertyAsInteger(Constants.CONFIG_CRON_ROP);
            int offset = 0;
            if (runtimeConfig.hasProperty(Constants.CONFIG_CRON_OFFSET)) {
                offset = runtimeConfig.getPropertyAsInteger(Constants.CONFIG_CRON_OFFSET);
            }

            logger.debug("For {} creating schedule with rop {} and offset {}", nodeName, rop, offset);

            int nextMinute = offset; //start from 0 minute + offset
            StringBuilder expressionBuilder = new StringBuilder();

            do {
                expressionBuilder.append(',').append(nextMinute % 60);
                nextMinute += rop;
            } while (nextMinute < 60);

            String expression = expressionBuilder.length() > 0 ? expressionBuilder.substring(1) : "0";

            cronExpression = new CronExpression(String.format(ropCronFormat, expression));
            logger.debug("For {} cron expression will be {}", nodeName, cronExpression.getCronExpression());

        } catch (ClassCastException | ParseException e) {
            logger.error("Failed to create cron expression for scheduler", e);
            return null;
        }


        return cronExpression;
    }
}
