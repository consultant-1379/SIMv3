package com.ericsson.sim.engine.policy.eniq;


import com.ericsson.sim.common.util.ExecUtil;
import com.ericsson.sim.plugins.policy.HealthCheck;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.AbstractMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EniqHealthCheck implements HealthCheck, Runnable {

    private static class DefaultStatus implements HealthCheck {

        private boolean shutdown = false;

        @Override
        public String getName() {
            return "DefaultState";
        }

        @Override
        public void startup() throws RuntimeException {
            shutdown = false;
        }

        @Override
        public void shutdown() throws RuntimeException {
            shutdown = true;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public void check() {

        }

        @Override
        public boolean isOk() {
            return true;
        }
    }

    public static DefaultStatus DEFAULT_CHECK = new DefaultStatus();

    private static final Logger logger = LogManager.getLogger(EniqHealthCheck.class);
    private ConfigModel model = new ConfigModel();
    private final File configFile;
    private long lastConfigUpdate = 0;

    private volatile boolean isok = false;
    private final int waitFor = 1000;
    private final int maxSuccessiveErrors = 10;

    private final Thread serviceThread;

    public EniqHealthCheck(String configPath) {
        this.configFile = new File(configPath);
        serviceThread = new Thread(this);
        serviceThread.setName("EniqHealthCheck");
    }


    @Override
    public void startup() throws RuntimeException {
        init();
        serviceThread.start();
    }

    @Override
    public void shutdown() throws RuntimeException {
        this.serviceThread.interrupt();
    }

    @Override
    public boolean isShutdown() {
        return !serviceThread.isAlive();
    }


    @Override
    public void run() {
        int errorCount = 0;
        long lastExecuted = 0;
        while (!Thread.currentThread().isInterrupted()) {

            //check if config needs reloading
            if (configFile.lastModified() != lastConfigUpdate) {
                logger.info("Config changed, reloading it");
                try {
                    init();
                } catch (Throwable t) {
                    logger.error("Failed to reload configuration. Old configuration will be used", t);
                }
            }

            final long current = System.currentTimeMillis();
            logger.trace("checkAfter:{}, checkAfterMill:{}, current:{}, lastExecuted:{}", model.checkAfter, model.getCheckAfterTimeunit.toMillis(model.checkAfter), current, lastExecuted);
            if (current - model.getCheckAfterTimeunit.toMillis(model.checkAfter) >= lastExecuted) {
                lastExecuted = current;
                try {
                    logger.debug("Doing EniqHealthCheck");
                    check();
                    errorCount = 0;
                } catch (Throwable t) {
                    logger.error("An unknown error occurred. Health check will return default result ({}) until next check", model.defaultStateOnError);
                    isok = model.defaultStateOnError;
                    errorCount++;

                    if (errorCount > maxSuccessiveErrors) {
                        logger.error("EniqHealthCheck returned error successively too many times. Service will stop. Restart service or health check will not be available");
                        isok = model.defaultStateOnError;
                        return;
                    }
                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(waitFor);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void init() throws RuntimeException {

        ConfigModel healthCheckModel = null;
        if (!configFile.exists()) {
//            logger.error("Policy config file {} does not exists", configFile.getAbsolutePath());
//            return;
            throw new RuntimeException("Policy file for health check " + configFile.getAbsolutePath() + " does not exit");
        }
        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(this.configFile))) {
                healthCheckModel = new Gson().fromJson(reader, ConfigModel.class);
            }
        } catch (Throwable e) {
            logger.warn("Failed to read  " + configFile.getAbsolutePath(), e);
//            return;
            throw new RuntimeException(e);
        }

        if (healthCheckModel.maxPercentageUsedSpace <= 0 || healthCheckModel.maxPercentageUsedSpace > 99) {
            logger.warn("Configuration option 'maxPercentageUsedSpace' cannot be less than or equal to 0, or greater than 99. We got {}. Setting to default {}", healthCheckModel.maxPercentageUsedSpace, ConfigModel.MAX_SPACE_USED);
            healthCheckModel.maxPercentageUsedSpace = ConfigModel.MAX_SPACE_USED;
        }

        if (healthCheckModel.checkEniqStatus == null) {
            logger.info("checkEniqStatus is not provided, using default {}", ConfigModel.CHECK_ENIQ_STATUS);
            healthCheckModel.checkEniqStatus = ConfigModel.CHECK_ENIQ_STATUS;
        }
        if (healthCheckModel.defaultStateOnError == null) {
            logger.info("defaultStateOnError us not provided, using default {}", ConfigModel.DEFAULT_STATE_ON_ERROR);
            healthCheckModel.defaultStateOnError = ConfigModel.DEFAULT_STATE_ON_ERROR;
        }
        if (healthCheckModel.checkAfter <= 0) {
            logger.info("checkAfter should be greater than 0, using default {} {}}", ConfigModel.CHECK_AFTER, ConfigModel.CHECK_AFTER_TIMEUNIT.name());
            healthCheckModel.checkAfter = ConfigModel.CHECK_AFTER;
        }
        if (healthCheckModel.dataLocation == null || healthCheckModel.dataLocation.isEmpty()) {
            logger.warn("Configuration option 'dataLocation' is not provided, or is empty. Setting to default {}", ConfigModel.DATA_LOCATION);
            healthCheckModel.dataLocation = ConfigModel.DATA_LOCATION;
        }
        File dataLocation = new File(healthCheckModel.dataLocation);
        if (!dataLocation.exists()) {
            logger.error("Location {} does not exit. Health checks will return default state {}. Fix configuration file", healthCheckModel.dataLocation, healthCheckModel.defaultStateOnError);
        }
        if (healthCheckModel.engineCheckCommand == null || "".equals(healthCheckModel.engineCheckCommand)) {
            logger.info("Engine check command is not provided. Default command '{}' will be used", ConfigModel.ENGINE_CHECK);
            healthCheckModel.engineCheckCommand = ConfigModel.ENGINE_CHECK;
        }
        model = healthCheckModel;
        lastConfigUpdate = configFile.lastModified();

//        try {
//            logger.info("Checking config.json file which is expected to be in same directory as {} plugin", EniqHealthCheck.class.getSimpleName());
//            File jarPath = new File(EniqHealthCheck.class.getProtectionDomain().getCodeSource().getLocation().toURI());
//            File config = Paths.get(jarPath.getAbsolutePath(), "config.json").toFile();
//            if (!config.exists()) {
//                logger.warn("config.json not found for {} plugin. Will use default values", EniqHealthCheck.class.getSimpleName());
//            } else if (!config.canRead()) {
//                logger.warn("config.json not exists but cannot be read for {} plugin. Will use default values", EniqHealthCheck.class.getSimpleName());
//            } else {
//                logger.debug("Reading config.json for {} plugin", EniqHealthCheck.class.getSimpleName());
//                model = new Gson().fromJson(config.getAbsolutePath(), ConfigModel.class);
//            }
//        } catch (URISyntaxException e) {
//            logger.warn("Failed to load config. Will use default config", e);
//        }
    }

    public synchronized void check() {
        isok = model.defaultStateOnError;
        if (!checkNAS()) {
            logger.debug("checkNAS returned false");
            isok = false;
        }
        if (!checkEniqEngine()) {
            logger.debug("checkEniqEngine returned false");
            isok = false;
        }
        if (!checkDiskSpace(model.dataLocation, model.maxPercentageUsedSpace)) {
            logger.debug("checkDiskSpace returned false");
            isok = false;
        }
        logger.debug("ENIQ health check completed. Status is {}", isOk());
    }

    @Override
    public synchronized boolean isOk() {
        return isok;
    }

    private boolean checkNAS() {
        return true;
    }

    private boolean checkEniqEngine() {
        if (!model.checkEniqStatus) {
            logger.debug("checkEngineStatus is set to false. Engine status will not be checked");
            return true;
        }
        String program = model.engineCheckCommand; //"engine status";
        AbstractMap.SimpleEntry<Integer, List<String>> output = ExecUtil.execute(program, model.getEngineCheckArg);
        if (output == null) {
            logger.warn("Didn't get any output for '{}'. It will be assumed that engine is offline", program);
            return false;
        }
        if (output.getKey() == 0) {
            logger.debug("Engine is online");
            return true;
        } else {
            logger.info("Engine is offline");
            if (logger.isDebugEnabled()) {
                logger.debug("Output form '{}': \n{}", program, output.getValue() != null ? String.join("\n", output.getValue()) : "null");
            }
            return false;
        }
    }

    private boolean checkDiskSpace(String path, final int maxPercentageUsedSpace) {
        File file = new File(path);
        if (!file.exists()) {
            logger.warn("Provided ENIQ store path '{}' does not exists. Check will fail", path);
            return false;
        }
//        try {
//            FileStore fileStore = Files.getFileStore(file.toPath());
        final long total = file.getTotalSpace();// fileStore.getTotalSpace();
        final long free = file.getFreeSpace();//fileStore.getUsableSpace();
        final long used = total - free;
        final long usable = file.getUsableSpace();
        final float percentUsed = (used / (float) total) * 100f;

        if (logger.isDebugEnabled()) {
            logger.debug("Total space: {} KB", total / 1024);
            logger.debug("Free space: {} KB", free / 1024);
            logger.debug("Used space: {} KB", used / 1024);
            logger.debug("Usable space: {} KB", usable / 1024);
        }

//            final long used = fileStore.getTotalSpace() - fileStore.getUsableSpace();

        logger.debug("For '{}': total available={} bytes, total used = {} bytes, percent used = {}%", path, total, used, String.format("%.1f", percentUsed));
        if (percentUsed > maxPercentageUsedSpace) {
            logger.warn("Total space used {}% is more than configured threshold {}%", String.format("%.1f", percentUsed), maxPercentageUsedSpace);
            return false;
        }
        return true;
//        } catch (IOException e) {
//            logger.warn("Failed to check available disk space. Check will fail", e);
//            return false;
//        }
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
