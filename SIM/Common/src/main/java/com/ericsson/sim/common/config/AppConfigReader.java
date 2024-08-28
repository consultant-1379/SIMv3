package com.ericsson.sim.common.config;


import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.util.HelpUtil;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class AppConfigReader {

    private static final Logger logger = LogManager.getLogger(AppConfigReader.class);

    private final File configFile;
    private AppConfig configModel;

    public AppConfigReader(String filePath) {
        this.configFile = new File(filePath);
    }

    public void read() throws IOException {
        Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new FileReader(this.configFile))) {
            this.configModel = gson.fromJson(reader, AppConfig.class);
        }
    }

    public boolean verify() {

        //verify paths
        if (HelpUtil.isNullOrEmpty(configModel.paths.privateKey)) {
            logger.error("'paths.privateKey' is not provided. SSH private key path must be provided for {} to work", Constants.APP_NAME);
            return false;
        }
        File file = new File(configModel.paths.privateKey);
        if (!file.exists()) {
            logger.warn("Path {} does not exists", configModel.paths.privateKey);
            logger.error("'paths.privateKey' does not exists. SSH private key path must be provided for {} to work", Constants.APP_NAME);
            return false;
        } else if (!file.isFile()) {
            logger.error("'paths.privateKey' is not a file. SSH private key path must be provided for {} to work", Constants.APP_NAME);
            return false;
        } else if (!file.canRead()) {
            logger.error("'paths.privateKey' is not readable. SSH private key path must be provided for {} to work", Constants.APP_NAME);
            return false;
        }

        if (HelpUtil.isNullOrEmpty(configModel.paths.nodeTypes)) {
            logger.info("'paths.nodeTypes' is either not provided or is empty. Using default {}", Constants.PATHS.NODETYPES);
            configModel.paths.nodeTypes = Constants.PATHS.NODETYPES;
        }
        file = new File(configModel.paths.nodeTypes);
        if (!file.exists()) {
            logger.warn("Path {} does not exists", configModel.paths.nodeTypes);
            logger.error("Node type path must exist with defined node types for {} to work.", Constants.APP_NAME);
            return false;
        }

        if (HelpUtil.isNullOrEmpty(configModel.paths.neConfig)) {
            logger.info("'paths.neConfig' is either not provided or is empty. Using default {}", Constants.PATHS.NE_CONFIG);
            configModel.paths.neConfig = Constants.PATHS.NE_CONFIG;
        }
        file = new File(configModel.paths.neConfig);
        if (!file.exists()) {
            logger.warn("Path {} does not exists", configModel.paths.neConfig);
            logger.error("NE configuration file must exist for {} to work", Constants.APP_NAME);
            return false;
        }
        if (HelpUtil.isNullOrEmpty(configModel.paths.historyDb)) {
            logger.info("'paths.historyDb' is either not provided or is empty. Using default {}", Constants.PATHS.HISTORY_DB);
            configModel.paths.historyDb = Constants.PATHS.HISTORY_DB;
        }
        file = new File(configModel.paths.historyDb);
        if (!file.exists()) {
            logger.info("Path {} does not exists. Creating", configModel.paths.historyDb);
            if (!file.mkdir()) {
                logger.error("Failed to create path {}. History files must be created for {} to work", configModel.paths.historyDb, Constants.APP_NAME);
                return false;
            }
        }

        //verify thread pool configuration
        if (configModel.threadPool.minSize < 1) {
            logger.warn("'threadPool.minSize' cannot be less then 1. Current value {}. Setting to default {}", configModel.threadPool.minSize, Constants.THREADPOOL_MIN_SIZE);
            configModel.threadPool.minSize = Constants.THREADPOOL_MIN_SIZE;
        }
        if (configModel.threadPool.maxSize < 1) {
            logger.warn("'threadPool.maxSize' cannot be less then 1. Current value {}. Setting to default {}", configModel.threadPool.maxSize, Constants.THREADPOOL_MAX_SIZE);
            configModel.threadPool.maxSize = Constants.THREADPOOL_MAX_SIZE;
        }
//        if (configModel.threadPool.maxSize > 10) {
//            logger.warn("Keeping many threads idle can waste system resources while waiting for next job to start. It is recommended to keep a small initial size");
//        }
        if (configModel.threadPool.maxSize < 10) {
            logger.warn("Using a small thread pool can lead to under utilization of resources. An ideal start could be to consider maximum size of 10.");
        }
        if (configModel.threadPool.maxSize > 2500) {
            logger.warn("Using a very large thread pool can negatively impact performance. An ideal start could be to consider maximum size of {}.", Constants.THREADPOOL_MAX_SIZE);
        }
        if (configModel.threadPool.idleTimeout < 1) {
            logger.warn("'threadPool.idleTimeout' cannot be less then 1. Current value {}. Setting to default {}", configModel.threadPool.idleTimeout, Constants.THREADPOOL_IDLE_SEC);
            configModel.threadPool.idleTimeout = Constants.THREADPOOL_IDLE_SEC;
        }
        if (configModel.threadPool.awaitTermination < 0) {
            logger.warn("'threadPool.awaitTermination' cannot be less than 0. Current value {}. Setting to default {}", configModel.threadPool.awaitTermination, Constants.THREADPOOL_AWAIT_TERMINATION);
            configModel.threadPool.awaitTermination = Constants.THREADPOOL_AWAIT_TERMINATION;
        }
        if (configModel.threadPool.awaitTermination > 30) {
            logger.warn("Too big 'threadPool.awaitTermination', current value {}, may result in {} taking too long to shutdown. Consider a smaller time to await", configModel.threadPool.awaitTermination, Constants.APP_NAME);
        }

        //verify rmi
        if (HelpUtil.isNullOrEmpty(configModel.rmi.serverName)) {
            logger.info("'rmi.serverName' is either not provided or is empty. Using default {}", Constants.RMI_SERVER_NAME);
            configModel.rmi.serverName = Constants.RMI_SERVER_NAME;
        }
        if (configModel.rmi.registryPort < 1 || configModel.rmi.registryPort > 65534) {
            logger.warn("'rmi.registryPort' cannot be less then 1 or greater than 65534. Current value {}. Setting to default {}", configModel.rmi.registryPort, Constants.RMI_REGISTRY_PORT);
            configModel.rmi.registryPort = Constants.RMI_REGISTRY_PORT;
        }

        if (configModel.scheduler.ignoreMissfire == null) {
            logger.info("'scheduler.ignoreMissfire is not set. Setting to {}'", true);
            configModel.scheduler.ignoreMissfire = true;
        }

        if (configModel.jobs == null) {
            configModel.jobs = new Jobs();
        }
        if (configModel.jobs.batchSize <= 0) {
            logger.warn("'jobs.batchSize' should be a positive value. Current value {}. Setting to default {}", configModel.jobs.batchSize, Constants.BATCH_SIZE);
            configModel.jobs.batchSize = Constants.BATCH_SIZE;
        } else if (configModel.jobs.batchSize < 100) {
            logger.warn("A small batch size, currently set to {}, can adversely affect performance. A larger number i.e. {} is recommended", configModel.jobs.batchSize, Constants.BATCH_SIZE);
        } else if (configModel.jobs.batchSize > 5000) {
            logger.warn("A large batch size, currently set to {}, can result in one node filling in the internal queue with request resulting in long time for collection for smaller nodes. " +
                    "A smaller number i.e. {} is recommended", configModel.jobs.batchSize, Constants.BATCH_SIZE);
        }
        if (configModel.jobs.windDownTime <= 0) {
            logger.warn("'jobs.windDownTime' should be a positive value. Current value {}. Setting to default {} {}",
                    configModel.jobs.windDownTime, Constants.MARGIN_TO_NEXT_EXECUTION, configModel.jobs.getWindDownTimeUnit().name());
            configModel.jobs.windDownTime = Constants.MARGIN_TO_NEXT_EXECUTION;
        }
        if (configModel.jobs.windDownTime <= 15) {
            logger.warn("A very small 'jobs.windDownTime' time can result in current running job overlapping with next execution of this job. A larger time i.e. {} {} is recommended",
                    Constants.MARGIN_TO_NEXT_EXECUTION, configModel.jobs.getWindDownTimeUnit().name());
        }

        //verify pattern
        if (HelpUtil.isNullOrEmpty(configModel.patterns.hash)) {
            logger.info("'patterns.hash' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_HASHCODE);
            configModel.patterns.hash = Constants.PATTERN_REPLACE_HASHCODE;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.nodeName)) {
            logger.info("'patterns.nodeName' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_NODENAME);
            configModel.patterns.nodeName = Constants.PATTERN_REPLACE_NODENAME;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.ipAddress)) {
            logger.info("'patterns.ipAddress' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_IPADDRESS);
            configModel.patterns.ipAddress = Constants.PATTERN_REPLACE_IPADDRESS;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.remoteFilename)) {
            logger.info("'patterns.remoteFilename' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_REMOTE_FILE_NAME);
            configModel.patterns.remoteFilename = Constants.PATTERN_REPLACE_REMOTE_FILE_NAME;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.remoteFilenameNoExt)) {
            logger.info("'patterns.remoteFilenameNoExt' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_REMOTE_FILE_NAME_NO_EXT);
            configModel.patterns.remoteFilenameNoExt = Constants.PATTERN_REPLACE_REMOTE_FILE_NAME_NO_EXT;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.uniqueId)) {
            logger.info("'patterns.uniqueId' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_UNIQUE_ID);
            configModel.patterns.uniqueId = Constants.PATTERN_REPLACE_UNIQUE_ID;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.ropStartDatetime)) {
            logger.info("'patterns.ropStartDatetime' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_ROP_START_DATETIME);
            configModel.patterns.ropStartDatetime = Constants.PATTERN_REPLACE_ROP_START_DATETIME;
        }
        if (HelpUtil.isNullOrEmpty(configModel.patterns.ropEndDatetime)) {
            logger.info("'patterns.ropEndDatetime' is either not provided or is empty. Setting to default {}", Constants.PATTERN_REPLACE_ROP_END_DATETIME);
            configModel.patterns.ropEndDatetime = Constants.PATTERN_REPLACE_ROP_END_DATETIME;
        }
        //check for illegal characters
        if (configModel.patterns.hash.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.hash' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.nodeName.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.nodeName' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.ipAddress.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.ipAddress' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.remoteFilename.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.remoteFilename' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.remoteFilenameNoExt.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.remoteFilenameNoExt' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.uniqueId.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.uniqueId' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.ropStartDatetime.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.ropStartDatetime' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }
        if (configModel.patterns.ropEndDatetime.indexOf('$') > -1) {
            //we cannot handle this
            logger.error("'patterns.ropEndDatetime' contains an illegal character: '$'. Pattern is not acceptable");
            return false;
        }

        //verify neCofig
        if (configModel.neConfig.commentStart == null) {
            logger.debug("'neConfig.commentStart is not provided. Setting to empty string as comments may not be possible in provided config'");
            configModel.neConfig.commentStart = "";
        }
        if (HelpUtil.isNullOrEmpty(configModel.neConfig.delimiter)) {
            logger.info("'neConfig.delimiter' is either not provided or is empty. Setting to default {}", Constants.CSV_DEFAULT_SEPARATOR);
            configModel.neConfig.delimiter = Constants.CSV_DEFAULT_SEPARATOR;
        }

        return true;
    }

    public AppConfig getConfigModel() {
        return configModel;
    }
}
