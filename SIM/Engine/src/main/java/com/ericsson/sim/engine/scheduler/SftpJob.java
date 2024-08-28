package com.ericsson.sim.engine.scheduler;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.engine.model.RuntimeConfig;
import com.ericsson.sim.plugins.policy.HealthCheck;
import com.ericsson.sim.sftp.SftpServerDetails;
import com.ericsson.sim.sftp.SftpWorker;
import com.ericsson.sim.sftp.filter.FileFilter;
import com.ericsson.sim.sftp.filter.ModifiedTimeFilter;
import com.ericsson.sim.sftp.filter.NamePatternFilter;
import com.ericsson.sim.sftp.filter.model.DefaultFilter;
import com.ericsson.sim.sftp.filter.model.TimeFilterConfig;
import com.ericsson.sim.sftp.instrumentation.SftpJobStats;
import com.ericsson.sim.sftp.processor.AppendPrependProcessor;
import com.ericsson.sim.sftp.processor.RegexProcessor;
import com.ericsson.sim.sftp.processor.ReplacePatternProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadPoolExecutor;

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class SftpJob implements Job {

    private static final Logger logger = LogManager.getLogger(SftpJob.class);
    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public SftpJob() {
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        try {
            final LocalDateTime currentTime = LocalDateTime.now();

            //TODO: Find way to use the RetryExecutor as scheduler for Quartz (if there is any such thing)

            logger.debug("Running " + jobExecutionContext.getJobDetail().getKey() + " at: " + LocalDateTime.now().format(dateTimeFormatter));

            final JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
            if (dataMap == null) {
                logger.error("JobDataMap is null or empty. Required data must be passed for job to be executed");
                throw new JobExecutionException("Job failed. JobDataMap is either null or empty");
            }

            final ThreadPoolExecutor executor;
            final HealthCheck healthCheck;
//        final PriorityExecutor priorityExecutor;
            final RuntimeConfig runtimeConfig;
            final String historyDb;
            final String privateKey;
            final int batchSize;
            final long winddownTime;
            final SftpJobStats stats;
//        int count = 0;

            try {
                executor = (ThreadPoolExecutor) dataMap.get(Constants.QJOB_EXECUTOR);
                healthCheck = (HealthCheck) dataMap.get(Constants.QJOB_HEALTH_CHECK);
//            priorityExecutor = (PriorityExecutor) dataMap.get(Constants.QJOB_EXECUTOR);
                runtimeConfig = (RuntimeConfig) dataMap.get(Constants.QJOB_RUNTIMECONFIG);
                historyDb = (String) dataMap.get(Constants.QJOB_HISTORYDIR);
                privateKey = (String) dataMap.get(Constants.QJOB_PRIVATEKEY);
                batchSize = (Integer) dataMap.get(Constants.QJOB_BATCH_SIZE);
                winddownTime = (Long) dataMap.get(Constants.QJOB_WINDDOWN_TIME);
                stats = (SftpJobStats) dataMap.get(Constants.QJOB_EXEC_INSTRUMENTATION);
            } catch (ClassCastException e) {
                logger.error("Failed to cast JobDataMap entry to required type. ", e);
                throw new JobExecutionException(e);
            }

            //TODO: Use the plugin to create the required information. Decouple.
            final String username = runtimeConfig.getPropertyAsString(Constants.CONFIG_USER);
            final String password = runtimeConfig.getPropertyAsString(Constants.CONFIG_PASS);
            final String ipAddress = runtimeConfig.getPropertyAsString(Constants.CONFIG_IPADDRESS);
            final int port = runtimeConfig.getPropertyAsInteger(Constants.CONFIG_PORT);
            final String keyFile = runtimeConfig.getPropertyAsString(Constants.CONFIG_KEYFILE);
            final int retries = runtimeConfig.getPropertyAsInteger(Constants.CONFIG_RETRIES);

            final String jobId = jobExecutionContext.getJobDetail().getKey().getName();

            logger.debug("Setting server detail: userName={}, ipAddress={}, port={}, retries={}", username, ipAddress, port, retries);
            SftpServerDetails serverDetails = null;


            //Create ServerDetail
            if (password != null && !password.isEmpty()) {
                serverDetails = new SftpServerDetails(ipAddress, port, username, password, retries);
            } else if (keyFile != null && !keyFile.isEmpty()) {
                serverDetails = new SftpServerDetails(ipAddress, port, username, new File(keyFile), retries);
            } else {
                serverDetails = new SftpServerDetails(new File(privateKey), ipAddress, port, username, retries);
            }

            long nextExecutionTime = jobExecutionContext.getNextFireTime() == null ? 0 : jobExecutionContext.getNextFireTime().getTime();
//            SftpWorker worker = new SftpWorker(priorityExecutor, serverDetails, runtimeConfig, historyDb, jobId, nextExecutionTime, batchSize, winddownTime);
            SftpWorker worker = new SftpWorker(executor, healthCheck, serverDetails, runtimeConfig, stats, historyDb, jobId, nextExecutionTime, batchSize, winddownTime);

            setupFilter(worker, runtimeConfig, currentTime);
            setupProcessors(jobId, worker, dataMap, runtimeConfig, currentTime);

            logger.debug("Sending worker {} to executor.", jobId);
//            logger.debug("Executor: getCorePoolSize={}, getActiveCount={}, getQueue().size()={}", priorityExecutor.getCorePoolSize(), priorityExecutor.getActiveCount(), priorityExecutor.getQueueSize());
            logger.debug("Executor: getCorePoolSize={}, getActiveCount={}, getQueue().size()={}", executor.getCorePoolSize(), executor.getActiveCount(), executor.getQueue().size());

            executor.execute(worker);
//            priorityExecutor.register(jobId, worker, 1);
            logger.debug("Worker {} sent to executor for execution", jobId);

            //update execution count
            dataMap.put(Constants.QJOB_EXEC_INSTRUMENTATION, stats);

        } catch (Throwable e) {
            logger.error("Failed to execute SftpWorker", e);
            throw new JobExecutionException(e);
        }
    }

    private void setupFilter(SftpWorker worker, RuntimeConfig runtimeConfig, LocalDateTime currentTime) {
        final int lookbackTime = runtimeConfig.getPropertyAsInteger("lookbackTime", 1440);

        //TODO: Date and regex should be their own types. Converting to dateTime everytime is expensive. For now we use a cache

        //TODO: Timezone information is missing form rop times take from config. See how to take timezone information

//        final int startRopDateGroupId = runtimeConfig.getPropertyAsInteger("startRopDateGroupId", -1);
//        final String startRopDatePattern = runtimeConfig.getPropertyAsString("startRopDatePattern", null);

        final int stopRopDateGroupId = runtimeConfig.getPropertyAsInteger("stopRopDateGroupId", -1);
        final String stopRopDatePattern = runtimeConfig.getPropertyAsString("stopRopDatePattern", null);

//        final int startRopTimeGroupId = runtimeConfig.getPropertyAsInteger("startRopTimeGroupId", -1);
//        final String startRopTimePattern = runtimeConfig.getPropertyAsString("startRopTimePattern", null);

        final int stopRopTimeGroupId = runtimeConfig.getPropertyAsInteger("stopRopTimeGroupId", -1);
        final String stopRopTimePattern = runtimeConfig.getPropertyAsString("stopRopTimePattern", null);

        //check what is what and choose the filter

        FileFilter fileFilter = null;

        if (lookbackTime <= 0) {
            logger.info("lookbackTime is 0 or less. All files matching will be considered");
            fileFilter = new DefaultFilter();
        } else {

            boolean filenameFilter = true;

            //if any setting is not complete or provided, we cannot proceed.
            //We only take into account the end date and time pattern part for filtering
//            if (startRopDatePattern == null) {
//                logger.debug("startRopDatePattern is null. File pattern based filtering cannot be used");
//                filenameFilter = false;
//            } else if (startRopDateGroupId == -1) {
//                logger.debug("startRopDateGroupId is -1. File pattern based filtering cannot be used");
//                filenameFilter = false;
//            } else
            if (stopRopDatePattern == null) {
                logger.debug("stopRopDatePattern is null. File pattern based filtering cannot be used");
                filenameFilter = false;
            } else if (stopRopDateGroupId == -1) {
                logger.debug("stopRopDateGroupId is -1. File pattern based filtering cannot be used");
                filenameFilter = false;
            }
//                else if (startRopTimePattern == null) {
//                logger.debug("startRopTimePattern is null. File pattern based filtering cannot be used");
//                filenameFilter = false;
//            } else if (startRopTimeGroupId == -1) {
//                logger.debug("startRopTimeGroupId is -1. File pattern based filtering cannot be used");
//                filenameFilter = false;
//            }
            else if (stopRopTimePattern == null) {
                logger.debug("stopRopTimePattern is null. File pattern based filtering cannot be used");
                filenameFilter = false;
            } else if (stopRopTimeGroupId == -1) {
                logger.debug("stopRopTimeGroupId is -1. File pattern based filtering cannot be used");
                filenameFilter = false;
            }

            if (filenameFilter) {
                logger.info("Got enough information to create filename based time filtering");
                TimeFilterConfig timeFilterConfig = new TimeFilterConfig(stopRopDateGroupId, stopRopTimeGroupId, stopRopDatePattern, stopRopTimePattern);

                fileFilter = new NamePatternFilter(currentTime, lookbackTime, timeFilterConfig);
            } else {
                logger.info("Not enough information to create filename base time filtering. Filtering to be done based on last modified time");
                fileFilter = new ModifiedTimeFilter(currentTime, lookbackTime);
            }
        }

        worker.addFileFilter(fileFilter);
    }

    private static void setupProcessors(String jobId, SftpWorker worker, JobDataMap dataMap, RuntimeConfig runtimeConfig, LocalDateTime currentTime) {
        //Add file name processors according to configuration
        final boolean prependId = runtimeConfig.getPropertyAsBoolean("prependId", false);
        final boolean appendId = runtimeConfig.getPropertyAsBoolean("appendId", false);
        final String fileRenamePattern = runtimeConfig.getPropertyAsString(Constants.CONFIG_FILE_RENAME_PATTERN, null);
        if (prependId || appendId) {
            logger.debug("Adding AppendPrependProcessor to local filename processors");
            worker.addLocalFilenameProcessor(new AppendPrependProcessor(jobId, prependId, appendId));
        }

        if (!HelpUtil.isNullOrEmpty(fileRenamePattern)) {
            logger.debug("Adding RegexProcessor to local filename processors");
            worker.addLocalFilenameProcessor(new RegexProcessor());

            final int ropMinutes = runtimeConfig.getPropertyAsInteger(Constants.CONFIG_CRON_ROP);
            String localRopStartPattern = runtimeConfig.getPropertyAsString("replaceRopStartPattern");
            String localRopEndPattern = runtimeConfig.getPropertyAsString("replaceRopEndPattern");

            if (HelpUtil.isNullOrEmpty(localRopStartPattern)) {
                logger.warn("localRopStartPattern is either null or empty. Will use default pattern {} for correct replacement of date time",
                        Constants.PATTERN_REPLACE_ROP_START_DATETIME_FORMAT);
                localRopStartPattern = Constants.PATTERN_REPLACE_ROP_START_DATETIME_FORMAT;
            }
            if (HelpUtil.isNullOrEmpty(localRopEndPattern)) {
                logger.warn("localRopEndPattern is either null or empty. Will use default pattern {} for correct replacement of date time",
                        Constants.PATTERN_REPLACE_ROP_END_DATETIME_FORMAT);
                localRopEndPattern = Constants.PATTERN_REPLACE_ROP_END_DATETIME_FORMAT;
            }

            String startDatetime = DateTimeFormatter.ofPattern(localRopStartPattern).format(currentTime.minusMinutes(ropMinutes));
            String endDateTime = DateTimeFormatter.ofPattern(localRopEndPattern).format(currentTime);

            logger.debug("Adding ReplacePatternProcessor to local filename processors");
            worker.addLocalFilenameProcessor(new ReplacePatternProcessor(runtimeConfig, jobId,
                    (String) dataMap.get(Constants.QJOB_RP_NODENAME), (String) dataMap.get(Constants.QJOB_RP_IPADDRESS),
                    (String) dataMap.get(Constants.QJOB_RP_HASHCODE), (String) dataMap.get(Constants.QJOB_RP_UNIQUE_ID),
                    (String) dataMap.get(Constants.QJOB_RP_REMOTE_FILE_NAME), (String) dataMap.get(Constants.QJOB_RP_REMOTE_FILE_NAME_NO_EXT),
                    (String) dataMap.get(Constants.QJOB_RP_ROP_START), (String) dataMap.get(Constants.QJOB_RP_ROP_END),
                    startDatetime, endDateTime));
        }
    }
}
