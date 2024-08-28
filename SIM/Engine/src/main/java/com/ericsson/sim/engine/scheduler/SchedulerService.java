package com.ericsson.sim.engine.scheduler;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.ericsson.sim.engine.config.ConfigService;
import com.ericsson.sim.engine.model.RuntimeConfig;
import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.executor.RetryExecutor;
import com.ericsson.sim.plugins.instrumentation.JobStats;
import com.ericsson.sim.plugins.policy.HealthCheck;
import com.ericsson.sim.plugins.rmi.JobStatus;
import com.ericsson.sim.sftp.instrumentation.SftpJobStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchedulerService {
    private static final Logger logger = LogManager.getLogger(SchedulerService.class);

    private Scheduler scheduler = null;
    private ThreadPoolExecutor executor = null;
//    private PriorityExecutor priorityExecutor = null;

    private int terminationWait = Constants.THREADPOOL_AWAIT_TERMINATION;
    private TimeUnit terminationTimeunit = TimeUnit.SECONDS;

    public void initialize(ConfigService configService) throws SchedulerException {

        logger.info("Initializing scheduler");
        executor = new RetryExecutor(configService.getAppConfig().getThreadPool().getMinSize(),
                configService.getAppConfig().getThreadPool().getMaxSize(),
                configService.getAppConfig().getThreadPool().getIdleTimeout(),
                configService.getAppConfig().getThreadPool().getTimeoutUnit());
//        priorityExecutor = new PriorityExecutor(configService.getAppConfig().getThreadPool().getMinSize(),
//                configService.getAppConfig().getThreadPool().getMaxSize(),
//                configService.getAppConfig().getThreadPool().getIdleTimeout(),
//                configService.getAppConfig().getThreadPool().getTimeUnit());

        terminationWait = configService.getAppConfig().getThreadPool().getAwaitTermination();
        terminationTimeunit = configService.getAppConfig().getThreadPool().getTerminationTimeUnit();

        java.util.Properties properties = new java.util.Properties();
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, Constants.APP_NAME);
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID, Constants.APP_NAME);
        properties.setProperty(StdSchedulerFactory.PROP_SCHED_THREAD_NAME, Constants.APP_NAME + "_TP");

//            properties.setProperty("org.quartz.threadPool.threadCount", String.valueOf(runtimeConfig.size() + 5));
        properties.setProperty("org.quartz.threadPool.class", CachedThreadPool.class.getName());

        SchedulerFactory schedulerFactory = new StdSchedulerFactory(properties);
        scheduler = schedulerFactory.getScheduler();
    }

    public void scheduleJob(ConfigService configService, Map<String, RuntimeConfig> oldRuntimeConfig, Map<String, RuntimeConfig> newRuntimeConfig, HealthCheck healthCheck) throws SchedulerException {

        for (Map.Entry<String, RuntimeConfig> entry : newRuntimeConfig.entrySet()) {
            scheduleJob(configService, healthCheck, entry.getKey(), entry.getValue(), oldRuntimeConfig == null ? null : oldRuntimeConfig.getOrDefault(entry.getKey(), null));
        }

        logger.debug("Starting scheduler");
        scheduler.start();
    }

    public void reschedule(ConfigService configService, Map<String, RuntimeConfig> oldRuntimeConfig, Map<String, RuntimeConfig> newRuntimeConfig, HealthCheck healthCheck) throws SchedulerException {
        deleteSchedules(newRuntimeConfig);

        logger.info("Rescheduling change jobs and scheduling new ones");
        scheduleJob(configService, oldRuntimeConfig, newRuntimeConfig, healthCheck);
    }

    private void deleteSchedules(Map<String, RuntimeConfig> newRuntimeConfig) throws SchedulerException {
        logger.info("Going to remove jobs that are no longer in new config");
        for (TriggerKey triggerKey : scheduler.getTriggerKeys(GroupMatcher.anyGroup())) {
            if (!newRuntimeConfig.containsKey(triggerKey.getName())) {
                logger.info("Job with id '{}' is no longer configured. Removing it from scheduler", triggerKey.getName());
                scheduler.unscheduleJob(triggerKey);
                Trigger trigger = scheduler.getTrigger(triggerKey);
                if (trigger != null) {
                    final JobKey jobKey = trigger.getJobKey();
                    scheduler.deleteJob(jobKey);
                }
            }
        }
    }

    private void scheduleJob(ConfigService configService, HealthCheck healthCheck, final String jobId, final RuntimeConfig newRuntimeConfig, final RuntimeConfig oldRuntimeConfig) throws SchedulerException {

        logger.debug("RuntimeConfig: \n{}", newRuntimeConfig);

        Trigger oldTrigger = scheduler.getTrigger(new TriggerKey(jobId));
        if (oldTrigger != null) {
//            boolean updateJob = false;
            //there is an old trigger, so there should be an old config
            if (oldRuntimeConfig != null) {
                if (newRuntimeConfig.equals(oldRuntimeConfig)) {
                    logger.info("Config for {} is still same. No need to update job", jobId);
                } else {
                    logger.info("Config for {} is updated. Job will be updated as well", jobId);
//                        updateJob = true;

                    if (logger.isDebugEnabled()) {
                        JobDataMap jobDataMap = scheduler.getJobDetail(oldTrigger.getJobKey()).getJobDataMap();
                        logger.debug("Job data map: {}", jobDataMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")));
                    }

                    //keep job stats
                    JobStats oldJobStats = (JobStats) scheduler.getJobDetail(oldTrigger.getJobKey()).getJobDataMap().get(Constants.QJOB_EXEC_INSTRUMENTATION);
                    if (oldJobStats == null) {
                        oldJobStats = new SftpJobStats();
                    }

                    scheduler.deleteJob(oldTrigger.getJobKey());
                    logger.info("Job {} removed", oldTrigger.getJobKey().getName());

//                    jobDetail.getJobDataMap().put(Constants.QJOB_EXEC_INSTRUMENTATION, oldJobStats);
                    JobDetail jobDetail = getJobDetail(configService, healthCheck, jobId, newRuntimeConfig, oldJobStats);
                    Trigger trigger = createTrigger(jobId, newRuntimeConfig, jobDetail, configService.getAppConfig().getScheduler().isIgnoreMissfire());

                    logger.info("Rescheduling {} job again", jobId);
                    //FIXME: rescheduleJob should work however for some reason it only updates the trigger, not the configuration i.e. job data map
                    //We can either rescheduleJob and then update map manually, or re-add the job after removing it which works as well
//                    scheduler.rescheduleJob(oldTrigger.getKey(), trigger);
                    scheduler.scheduleJob(jobDetail, trigger);
                }
            } else {
                //ok so there is a oldtrigger but no old config for it. Was it supposed to be removed but haven't been removed for some
                //reason? some error may have occurred while trying to remove it.
                //remove it from the schedule as there is no old config.
                //if there is a new config for this jobId, then we try to add it back again
                logger.info("A job was scheduled for {}, however old job config could not be found. Job will be removed and if there is new config for it, it will be added back agian", jobId);
                scheduler.deleteJob(oldTrigger.getJobKey());
                logger.info("Job {} removed", oldTrigger.getJobKey().getName());

                //add it back again with new update
                JobDetail jobDetail = getJobDetail(configService, healthCheck, jobId, newRuntimeConfig, new SftpJobStats());
                Trigger trigger = createTrigger(jobId, newRuntimeConfig, jobDetail, configService.getAppConfig().getScheduler().isIgnoreMissfire());

                logger.info("Scheduling {} job", jobId);
                scheduler.scheduleJob(jobDetail, trigger);
//                scheduler.rescheduleJob(oldTrigger.getKey(), trigger);
            }
//                oldBuilder = oldTrigger.getTriggerBuilder();
//            if (updateJob) {
////            logger.info("Rescheduling job again: {}", jobDetail);
//                logger.info("Rescheduling {} job again", jobId);
////            scheduler.scheduleJob(jobDetail, trigger, true);
////            scheduler.rescheduleJob(oldTrigger.getKey(), trigger);
////                scheduler.deleteJob(oldTrigger.getJobKey());
////                scheduler.scheduleJob(jobDetail, trigger);
//
//                //reschedule trigger
//                scheduler.rescheduleJob(oldTrigger.getKey(), trigger);
//            }
        } else {
//            logger.info("Scheduling new job: {}", jobDetail);
//            dataMap.put(Constants.QJOB_EXEC_INSTRUMENTATION, new SftpJobStats());
            JobDetail jobDetail = getJobDetail(configService, healthCheck, jobId, newRuntimeConfig, new SftpJobStats());
            Trigger trigger = createTrigger(jobId, newRuntimeConfig, jobDetail, configService.getAppConfig().getScheduler().isIgnoreMissfire());

            logger.info("Scheduling {} job", jobId);
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    private JobDetail getJobDetail(ConfigService configService, HealthCheck healthCheck, final String jobId, RuntimeConfig newRuntimeConfig, JobStats jobStats) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(Constants.QJOB_EXECUTOR, executor);
        dataMap.put(Constants.QJOB_HEALTH_CHECK, healthCheck);
//        dataMap.put(Constants.QJOB_EXECUTOR, priorityExecutor);
        dataMap.put(Constants.QJOB_RUNTIMECONFIG, newRuntimeConfig);
        dataMap.put(Constants.QJOB_HISTORYDIR, configService.getAppConfig().getPaths().getHistoryDb());
        dataMap.put(Constants.QJOB_PRIVATEKEY, configService.getAppConfig().getPaths().getPrivateKey());
        dataMap.put(Constants.QJOB_BATCH_SIZE, configService.getAppConfig().getJobs().getBatchSize());
        dataMap.put(Constants.QJOB_WINDDOWN_TIME, configService.getAppConfig().getJobs().getWindDownTimeUnit().toMillis(configService.getAppConfig().getJobs().getWindDownTime()));

        //TODO: plugins should load and own their configuration. Restructure
        dataMap.put(Constants.QJOB_RP_NODENAME, configService.getAppConfig().getPatterns().getNodeName());
        dataMap.put(Constants.QJOB_RP_IPADDRESS, configService.getAppConfig().getPatterns().getIpAddress());
        dataMap.put(Constants.QJOB_RP_HASHCODE, configService.getAppConfig().getPatterns().getHash());
        dataMap.put(Constants.QJOB_RP_REMOTE_FILE_NAME, configService.getAppConfig().getPatterns().getRemoteFilename());
        dataMap.put(Constants.QJOB_RP_REMOTE_FILE_NAME_NO_EXT, configService.getAppConfig().getPatterns().getRemoteFilenameNoExt());
        dataMap.put(Constants.QJOB_RP_UNIQUE_ID, configService.getAppConfig().getPatterns().getUniqueId());
        dataMap.put(Constants.QJOB_RP_ROP_START, configService.getAppConfig().getPatterns().getRopStartDatetime());
        dataMap.put(Constants.QJOB_RP_ROP_END, configService.getAppConfig().getPatterns().getRopEndDatetime());

        dataMap.put(Constants.QJOB_EXEC_INSTRUMENTATION, jobStats);

        return JobBuilder.newJob(SftpJob.class).withIdentity(jobId)
                .usingJobData(dataMap)
                .build();
    }

    private Trigger createTrigger(final String jobId, RuntimeConfig newRuntimeConfig, JobDetail jobDetail, final boolean ignoreMissfire) {
        CronExpression cronExpression = CronBuilder.createExpression(newRuntimeConfig.getNodeName(), newRuntimeConfig);
        Trigger trigger;
        if (cronExpression == null) {
            //create one time trigger
            logger.debug("Creating one-time job {} for node {} to be executed now", jobId, newRuntimeConfig.getNodeName());
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobId)
                    .forJob(jobId)
                    .startNow()
                    .forJob(jobDetail)
                    .build();
        } else {
            //create cron trigger
            logger.debug("Creating cron job {} for node {} to execute with schedule {}", jobId, newRuntimeConfig.getNodeName(), cronExpression.getCronExpression());
            CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
//            if (configService.getAppConfig().getScheduler().isIgnoreMissfire()) {
            if (ignoreMissfire) {
                cronScheduleBuilder = cronScheduleBuilder.withMisfireHandlingInstructionDoNothing();
            }
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobId)
                    .withSchedule(cronScheduleBuilder)
                    .forJob(jobDetail)
                    .build();
        }
        return trigger;
    }

    public void shutdown() {
        boolean isShutdown = false;
        RuntimeException toThrow = null;
        try {
            isShutdown = scheduler == null || scheduler.isShutdown();
        } catch (SchedulerException ignored) {
        }
        if (!isShutdown) {
            try {
//                if (!scheduler.isShutdown()) {
                List<JobExecutionContext> currentExecution = scheduler.getCurrentlyExecutingJobs();
                logger.info("Currently executing {} jobs. They will be cleared and shutdown", currentExecution != null ? currentExecution.size() : 0);
                logger.debug("Clearing scheduler");
                scheduler.clear();
                logger.debug("Shutting down scheduler");
                scheduler.shutdown();
//                } else {
//                    logger.info("Scheduler is already shutdown");
//                }
            } catch (Throwable e) {
                logger.error("Error while stopping scheduler", e);
                toThrow = new RuntimeException("Error while stopping scheduler", e);
            }
        } else {
            logger.info("Scheduler is either not initialized or already shutdown");
        }

        //Stop pool
        if (executor != null && !executor.isShutdown()) {
            try {
                logger.info("Shutting down executor");
                logger.debug("Currently executing {} tasks actively", executor.getActiveCount());
                List<Runnable> runnable = executor.shutdownNow();
                logger.debug("{} tasks were awaiting execution when executor was shutdown", runnable.size());
//                try {
//                    if (!executor.isShutdown()) {
//                        if (!executor.awaitTermination(terminationWait, terminationTimeunit)) {
//                            logger.info("Executor still had tasks running after awaiting termination for {} {}. Service will shutdown now.", terminationWait, terminationTimeunit.toString());
//                        }
//                    }
//                } catch (InterruptedException ignored) {
//                }
            } catch (Throwable e) {
                logger.error("Error while stopping executor", e);
                toThrow = new RuntimeException("Error while stopping scheduler", e);
            }
        } else {
            logger.info("Executor is either not initialized or already shutdown");
        }

//        if (priorityExecutor != null && !priorityExecutor.isShutdown()) {
//            try {
//
//                logger.info("Shutting down executor");
////                logger.debug("Currently executing around {} tasks actively", executor.getActiveCount());
//                priorityExecutor.shutdown();
////                logger.debug("{} tasks were awaiting execution when executor was shutdown", runnable.size());
//            } catch (Throwable e) {
//                logger.error("Error while stopping executor", e);
//                toThrow = new RuntimeException("Error while stopping scheduler", e);
//            }
//        } else {
//            logger.info("Executor is either not initialized or already shutdown");
//        }

        if (toThrow != null) {
            throw toThrow;
        }
    }

    public boolean canShutdown() throws SchedulerException {
        if (scheduler != null && scheduler.isStarted()) {
            List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();
            if (executingJobs == null) {
                logger.warn("Returned executing job list is null. This is unknown behaviour. Will not stop server");
                return false;
            } else if (executingJobs.size() == 0) {
                logger.debug("There are currently no jobs executing, its safe to stop server!");
                return true;
            } else {
                logger.debug("There are {} jobs executing. Will not stop server", executingJobs.size());
                return false;
            }
        }
        return true;
    }

    public List<JobStatus> listJobStatus() {
        List<JobStatus> jobs = new ArrayList<>();
        //FIXME: check multiple usage safety
        final CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        final CronDescriptor cronDescriptor = CronDescriptor.instance(Locale.getDefault());

        if (scheduler != null) {
            try {
//                DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
                for (TriggerKey triggerKey : scheduler.getTriggerKeys(GroupMatcher.anyGroup())) {
                    Trigger trigger = scheduler.getTrigger(triggerKey);
                    if (trigger != null) {
                        final JobKey jobKey = trigger.getJobKey();
                        final JobDetail jobDetail = scheduler.getJobDetail(jobKey);

                        if (trigger instanceof CronTrigger) {
                            CronTrigger cronTrigger = (CronTrigger) trigger;
                            CronParser cronParser = new CronParser(cronDefinition);

                            JobStats jobStats = (JobStats) jobDetail.getJobDataMap().get(Constants.QJOB_EXEC_INSTRUMENTATION);
                            if (jobStats == null) {
                                logger.debug("JobStats is null. This should not happen for cron triggers");
                                jobStats = new SftpJobStats();
                            }

                            jobs.add(new JobStatus(jobKey.getName(),
                                    cronTrigger.getCronExpression(),
                                    cronDescriptor.describe(cronParser.parse(cronTrigger.getCronExpression())),
                                    trigger.getNextFireTime(),
                                    jobStats));

                        } else if (trigger instanceof SimpleTrigger) {
                            SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;

                            SftpJobStats stats = new SftpJobStats();
                            stats.incrementLastExecutedTasks(simpleTrigger.getTimesTriggered());

                            jobs.add(new JobStatus(jobKey.getName(),
                                    "Every " + (int) (simpleTrigger.getRepeatInterval() / 1000L) + " seconds",
                                    "Repeated " + simpleTrigger.getTimesTriggered() + " out of total " + simpleTrigger.getRepeatCount() + " times",
                                    trigger.getNextFireTime(),
                                    stats));
                        } else {
                            JobStats jobStats = (JobStats) jobDetail.getJobDataMap().get(Constants.QJOB_EXEC_INSTRUMENTATION);
                            if (jobStats == null) {
                                jobStats = new SftpJobStats();
                            }

                            jobs.add(new JobStatus(jobKey.getName(),
                                    "Unknown",
                                    "Unknown",
                                    trigger.getNextFireTime(),
                                    jobStats));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to get schedules from scheduler", e);
                throw new RuntimeException(e);
            }
        } else {
            logger.warn("Scheduler is null. Will return empty list");
        }

        return jobs;
    }
}
