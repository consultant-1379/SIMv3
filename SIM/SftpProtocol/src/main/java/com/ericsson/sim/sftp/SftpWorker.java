package com.ericsson.sim.sftp;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.history.AppendProgressFile;
import com.ericsson.sim.common.history.AppendProgressManager;
import com.ericsson.sim.common.history.HistoryFile;
import com.ericsson.sim.common.history.HistoryManager;
import com.ericsson.sim.common.history.model.ActiveFile;
import com.ericsson.sim.common.history.model.FileAttributes;
import com.ericsson.sim.common.pool.ConnectionPool;
import com.ericsson.sim.common.pool.ShrinkableKeyedObjectPool;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.common.worker.Worker;
import com.ericsson.sim.plugins.model.Config;
import com.ericsson.sim.plugins.model.ProtocolException;
import com.ericsson.sim.plugins.policy.HealthCheck;
import com.ericsson.sim.sftp.filter.FileFilter;
import com.ericsson.sim.sftp.filter.model.DefaultFilter;
import com.ericsson.sim.sftp.instrumentation.SftpJobStats;
import com.ericsson.sim.sftp.processor.interfaces.FilenameProcessor;
import com.ericsson.sim.sftp.stream.DelimiterOutputStream;
import com.ericsson.sim.sftp.stream.GenerateCsvOutputStream;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SftpWorker extends Worker {
    private static final Logger logger = LogManager.getLogger(SftpWorker.class);

    //    private final ConnectionPool<SftpServerDetails, SftpConnection> pool;
    private final SftpServerDetails serverDetail;
    private final ThreadPoolExecutor executor;
    private final HealthCheck healthCheck;
    //    private final PriorityExecutor priorityExecutor;
    private final Config configuration;
    private final SftpJobStats stats;
    private final File historyDb;
    private final String jobId;
    private final int retries;
    private final long nextExecutionTime;
    private final int batchSize;
    private final long windDownTime;

    private final String[] knownHousekeepingActions = {"", "IMMEDIATE", "STORAGE_DAYS", "MOVE"};
    private static final int HK_ACTION_NONE = 0;
    private static final int HK_ACTION_IMMEDIATE = 1;
    private static final int HK_ACTION_STORAGE_DAYS = 2;
    private static final int HK_ACTION_MOVE = 3;

    private final List<FilenameProcessor> filenameProcessors = new ArrayList<>(3);

    private FileFilter fileFilter = new DefaultFilter();

    public SftpWorker(ThreadPoolExecutor executor, HealthCheck healthCheck, SftpServerDetails serverDetail, Config configuration, SftpJobStats stats, String historyDb, String jobId, long nextExecutionTime, int batchSize, long windDownTime) {
//    public SftpWorker(PriorityExecutor executor, SftpServerDetails serverDetail, Config configuration, String historyDb, String jobId, long nextExecutionTime, int batchSize, int windDownTime) {
        this.executor = executor;
        this.healthCheck = healthCheck;
//        this.pool = pool;
        this.serverDetail = serverDetail;
        this.historyDb = new File(historyDb);
        this.jobId = jobId;
        this.configuration = configuration;
        this.stats = stats;
        this.retries = this.configuration.getPropertyAsInteger("sftpRetries", 0);
        this.nextExecutionTime = nextExecutionTime;
        this.batchSize = batchSize;
        this.windDownTime = windDownTime;
    }

    public void addLocalFilenameProcessor(FilenameProcessor processor) {
        if (processor != null) {
            filenameProcessors.add(processor);
        } else {
            logger.info("A null FilenameProcessor was provided. It will be ignored");
        }
    }

    public void addFileFilter(FileFilter fileFilter) {
        this.fileFilter = fileFilter;
    }

    @Override
    protected void runEx() {
        final String threadName = Thread.currentThread().getName();
        try {
            stats.setupJobStart();
            stats.incrementJobExecutionCount();

            //before we run, check the health
            if (!healthCheck.isOk()) {
                logger.error("Health check status failed. Will not execute task {}", jobId);
                return;
            }

            logger.info("Health check says {}. Class was {}", healthCheck.isOk(), healthCheck.getClass().getSimpleName());

            final long start = System.currentTimeMillis();
            Thread.currentThread().setPriority(7);

            Thread.currentThread().setName(jobId + "::main");

            logger.debug("Starting at {}. Next execution at {}", start, nextExecutionTime);

            logger.debug("Using config: {}", this.configuration.toString());

            final int maxSftpConn = this.configuration.getPropertyAsInteger(Constants.CONFIG_MAX_SFTP_CONNECTION);

            final String remotePath = this.configuration.getPropertyAsString("remoteDirectory", "");
            final String localPath = this.configuration.getPropertyAsString("destinationDirectory", "");
            final String parentDirPattern = this.configuration.getPropertyAsString("parentDirPattern");
            final String fileNamePattern = this.configuration.getPropertyAsString(Constants.CONFIG_REMOTE_FILE_REGEX);
            final String renamePattern = this.configuration.getPropertyAsString(Constants.CONFIG_FILE_RENAME_PATTERN);
            final int storageDays = this.configuration.getPropertyAsInteger("storageDays");
            final String moveTo = this.configuration.getPropertyAsString("moveTo");
            final String deleteAction = this.configuration.getPropertyAsString("deleteAction");
            final boolean localCleanup = this.configuration.getPropertyAsBoolean("localCleanup", false);
            final boolean ignoreHistory = this.configuration.getPropertyAsBoolean("ignoreHistory", false);
            final boolean currentHourLocalDir = this.configuration.getPropertyAsBoolean("currentHourLocalDir", false);
            final String copyTo = this.configuration.getPropertyAsString("copyTo", "");

            boolean moveAfterTransfer = knownHousekeepingActions[HK_ACTION_MOVE].equals(deleteAction);
            boolean deleteAfterTransfer = knownHousekeepingActions[HK_ACTION_IMMEDIATE].equals(deleteAction);
            boolean deleteAfterDays = knownHousekeepingActions[HK_ACTION_STORAGE_DAYS].equals(deleteAction);

            try {
                checkAndCreateDir(localPath);
            } catch (IOException e) {
                logger.error("Failed to create path. Job will not be executed until destination path is available", e);
                return;
            }

            final List<Path> copyToFiles = createCopyToFiles(copyTo);

            //check if remote housekeeping is required
            if (moveAfterTransfer && (moveTo == null || "".equals(moveTo))) {
                logger.warn("{} is selected as deleteAction but moveTo directory is not provided}", deleteAction);
                moveAfterTransfer = false;
            }
            if (deleteAfterDays && storageDays <= 0) {
                logger.info("{} is selected as deleteAction but storageDays of {} was provided. storageDays must be greater than 0 for this type of housekeeping. Remote housekeeping will be disabled",
                        knownHousekeepingActions[HK_ACTION_STORAGE_DAYS], storageDays);
                deleteAfterDays = false;
            }

            final ConnectionPool<SftpServerDetails, SftpConnection> pool = new SftpConnectionPool(new ShrinkableKeyedObjectPool<>(new SftpSessionFactory()), maxSftpConn, 1);

            try {
                //open history for this node
                long opStart = System.currentTimeMillis();
                HistoryFile historyFile = HistoryManager.instance.getHistory(jobId, historyDb);
                logger.info("Reading history took {} ms to complete", (System.currentTimeMillis() - opStart));

                opStart = System.currentTimeMillis();
                logger.info("Listing remote files on {}", serverDetail.getHost());
                Stream<Map.Entry<String, FileAttributes>> remoteFiles = getRemoteFiles(pool, remotePath, parentDirPattern, fileNamePattern);
                logger.info("Listing remote files took {} ms to complete", (System.currentTimeMillis() - opStart));

                opStart = System.currentTimeMillis();

                //TODO: check how two maps can be created from 1 stream
                //create one complete view
                final LinkedHashMap<String, FileAttributes> processedRemoteFiles = remoteFiles.sorted((o1, o2) -> o2.getValue().getRemoteMTime() - o1.getValue().getRemoteMTime())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1.getRemoteMTime() >= v2.getRemoteMTime() ? v1 : v2, LinkedHashMap::new));

                if (ignoreHistory) {
                    logger.debug("ignoreHistory is set to true. All files will be considered even if they are in history");
                }
                //create one view which only have files that need transfer
                final LinkedHashMap<String, FileAttributes> localMissingFiles = processedRemoteFiles.entrySet().stream()
                        .filter(e -> ignoreHistory || !historyFile.hasFile(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1.getRemoteMTime() >= v2.getRemoteMTime() ? v1 : v2, LinkedHashMap::new));

                logger.info("Creating list of files to transfer took {} ms to complete", (System.currentTimeMillis() - opStart));

                opStart = System.currentTimeMillis();
                final HashMap<String, FileAttributes> removedFiles = cleanupHistory(processedRemoteFiles, historyFile);
                logger.info("Cleaning history took {} ms to complete", (System.currentTimeMillis() - opStart));

                logger.info("Total {} files discovered after filtering. {} removed from history as they don't exists in remote server anymore. {} selected for transfer based on saved history",
                        processedRemoteFiles.size(), removedFiles.size(), localMissingFiles.size());

                if (localCleanup) {
                    localHousekeeping(removedFiles);
                }

                if (logger.isTraceEnabled()) {
                    logger.trace("Files selected for transfer: {}", localMissingFiles.entrySet().stream().map(k -> k.getKey() + "=" + k.getValue()).collect(Collectors.joining(", ", "{", "}")));
                }

                opStart = System.currentTimeMillis();
                //transfer files that are in remote but not in local history
                if (localMissingFiles.size() > 0) {
                    transferFiles(pool, localPath, historyFile, localMissingFiles, copyToFiles, deleteAfterTransfer, moveAfterTransfer, moveTo, currentHourLocalDir,
                            renamePattern);
                } else {
                    logger.info("No files selected for download");
                }
                logger.info("Transfer operation took {} ms to complete", (System.currentTimeMillis() - opStart));

                if (deleteAfterDays) {
                    opStart = System.currentTimeMillis();
                    remoteHousekeeping(pool, processedRemoteFiles, historyFile, storageDays);
                    logger.info("Remote files housekeeping {} ms to complete", (System.currentTimeMillis() - opStart));
                }

                opStart = System.currentTimeMillis();
                //FIXME: In rare cases, the application can stop while file is being saved corrupting the progress. Save in temporary and then rename as atomic operation.
                //save history
                historyFile.save();

                //close
                historyFile.close();
                logger.info("Saving history took {} ms to complete", (System.currentTimeMillis() - opStart));

            } catch (Throwable t) {
                logger.error("Failed to complete worker " + jobId, t);
            } finally {
                logger.debug("Closing pool for {}", jobId);

                printPoolStats(jobId, maxSftpConn, pool);
                pool.close();
//            Thread.currentThread().setName(name);

//            logger.info("Operations on {} took {} ms to complete", serverDetail.getHost(), TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
                double duration = ((double) System.currentTimeMillis() - (double) start) / 1000;
//            logger.info("Operations on {} took {} sec to complete", serverDetail.getHost(), duration);
                logger.info("Operations on {} took {} sec to complete", jobId, duration);

                stats.setLastJobTime(duration);
            }
        } catch (Throwable t) {
            logger.fatal("Unhandled error", t);
        } finally {
            Thread.currentThread().setName(threadName);
        }
    }

    private void localHousekeeping(HashMap<String, FileAttributes> removedFiles) {
        long opStart;
        opStart = System.currentTimeMillis();
        logger.info("Local cleanup is enabled. Removing {} files that are no longer on {}", removedFiles.size(), jobId);
        AtomicInteger removedFilesCount = new AtomicInteger();
        removedFiles.entrySet().parallelStream().forEach(e -> {
            File localFile = new File(e.getValue().getLocalPath());
            if (localFile.exists()) {
                if (localFile.delete()) {
                    logger.debug("File {} deleted as part of local cleanup", localFile.getAbsolutePath());
                    removedFilesCount.incrementAndGet();
                } else {
                    logger.warn("Failed to delete {}", localFile.getAbsolutePath());
                }
            } else {
                logger.info("File {} does not exists. Has it been moved? after transfer", localFile.getAbsolutePath());
            }
        });
        logger.info("Removed {} local files out of {} tried in {} ms", removedFilesCount.get(), removedFiles.size(), (System.currentTimeMillis() - opStart));
    }

    private List<Path> createCopyToFiles(String copyTo) {
        final List<Path> copyToFiles = new ArrayList<>();
        if (copyTo != null && !copyTo.isEmpty()) {
            logger.debug("copyTo: {}", copyTo);
            String[] split = copyTo.split(";");
            for (String s : split) {
                s = s.trim();
                logger.debug("Checking copyTo destination: '{}'", s);
                if (!s.isEmpty()) {
                    try {
                        Path path = Paths.get(s);
                        checkAndCreateDir(path.toFile());
                        copyToFiles.add(path);
                    } catch (IOException e) {
                        logger.error("Failed to create path for copyTo destination " + s + ". This path will not be used", e);
                    }
                }
            }
        }
        return copyToFiles;
    }

    private void checkAndCreateDir(String localPath) throws IOException {
        checkAndCreateDir(new File(localPath));
    }

    private void checkAndCreateDir(File localPathFile) throws IOException {
        if (!localPathFile.exists()) {
            logger.info("Destination path {} does not exists. Will try to create and any parent directories that does not exists", localPathFile.getAbsolutePath());
//            try {
            Files.createDirectories(localPathFile.toPath());
            logger.info("Path {} created", localPathFile.getAbsolutePath());
//            } catch (IOException e) {
//                logger.error("Failed to create path. Job will not be executed until destination path is available", e);

//            }
        }
    }

    private void remoteHousekeeping(ConnectionPool<SftpServerDetails, SftpConnection> pool, LinkedHashMap<String, FileAttributes> processedRemoteFiles,
                                    HistoryFile historyFile, long storageDays) {
        logger.debug("Housekeeping of files on {} is enabled. Building list of files to be cleaned", serverDetail.getHost());
        final long deleteTime = storageDays * 86400L;
        logger.debug("Files with mTime older than {} will be removed", deleteTime);

        final long deleteAfter = Instant.now().getEpochSecond() - deleteTime;
        logger.debug("Delete after is calculated using local time: {} sec", deleteAfter);

        //for now, we delete in loop. it can also be done in threads like transfer but the operation
        //should be quick enough

        //we are comparing local time with remote. if they are in different timezone (highly improbable) we may
        //end up removing some files a little before their time.

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger selected = new AtomicInteger();
        StringBuilder filesSelected = new StringBuilder();
        processedRemoteFiles.entrySet().stream().filter(k -> k.getValue().getRemoteMTime() < deleteAfter).parallel().forEach(k -> {
            SftpConnection connection = null;
            selected.incrementAndGet();
            try {
                connection = pool.getPool().borrowObject(serverDetail);

                if (connection == null) {
                    logger.error("Failed to get connection for {}. Would not be able to delete {}", serverDetail.getHost(), k.getValue().getRemoteFilename());
                    throw new RuntimeException("Failed to get connection for " + serverDetail.getHost());
                }

                if (logger.isTraceEnabled()) {
                    filesSelected.append("{").append(k.getKey()).append("=").append(k.getValue()).append("},");
                }

                connection.delete(k.getValue().getRemoteParentDir(), k.getValue().getRemoteFilename());
                success.getAndIncrement();

                // remove from history as well if it exists
                historyFile.removeFile(k.getKey());

            } catch (Exception e) {
                logger.error(e);
                failed.getAndIncrement();
            } finally {
                try {
                    pool.getPool().returnObject(serverDetail, connection);
                } catch (Exception ignored) {
                }
            }
        });
        if (logger.isTraceEnabled()) {
            logger.trace("Files selected to delete: {}", filesSelected.toString());
        }
        logger.info("Housekeeping of files on {} executed. Of {} files {} were successfully removed and {} failed", serverDetail.getHost(), selected.get(), success.get(), failed.get());
    }

    private void transferFiles(ConnectionPool<SftpServerDetails, SftpConnection> pool, String localPath, HistoryFile historyFile, LinkedHashMap<String, FileAttributes> localMissingFiles,
                               final List<Path> copyToFiles, final boolean deleteAfterTransfer, final boolean moveAfterTransfer, final String moveTo, final boolean currentHourLocalDir, final String renamePattern) {

        batchProcess(pool, localPath, historyFile, localMissingFiles, copyToFiles, deleteAfterTransfer, moveAfterTransfer, moveTo, currentHourLocalDir, renamePattern);
    }

    private void batchProcess(ConnectionPool<SftpServerDetails, SftpConnection> pool, String localParentDir, HistoryFile historyFile, Map<String, FileAttributes> localMissingFiles,
                              final List<Path> copyToFiles, final boolean deleteAfterTransfer, final boolean moveAfterTransfer, final String moveToDir, final boolean currentHourLocalDir, final String renamePattern) {

        int submitted = 0;
        int success = 0;
        int failed = 0;

        //TODO: Better flexible configuration option to let user create local dir based on different parameters
        if (currentHourLocalDir) {
            localParentDir = createIfNotPresentParentDir(localParentDir);
            if (localParentDir == null) return;
        }


        final boolean appendToFile = this.configuration.getPropertyAsBoolean("appendToFile", false);
        final int appendRetries = this.configuration.getPropertyAsInteger("appendRetries", Constants.APPEND_RETRIES);

        final boolean csvAppend = this.configuration.getPropertyAsBoolean("appendToCsv", false);

        final AppendProgressFile progressFile = getProgressFile(appendToFile, jobId, historyDb);
        if (appendToFile && progressFile == null) {
            return;
        }

        logger.info("Starting {} with batchSize {} and windDownTime {}", jobId, batchSize, windDownTime);

        ArrayDeque<Future<FileAttributes>> results = new ArrayDeque<>();
        final List<String> keys = new ArrayList<>(localMissingFiles.keySet());
        boolean refill = true;

        final int pages = (int) Math.ceil((double) keys.size() / batchSize);
        logger.debug("Total {} received. Tasks are divided in {} batches each of size {} tasks", keys.size(), pages, batchSize);

        if (logger.isTraceEnabled()) {
            logger.trace("Top 50 files: {}", String.join("\n", keys.subList(0, Math.min(keys.size(), 50))));
            logger.trace("Last 50 files: {}", String.join("\n", keys.subList(Math.max(keys.size() - 50, 0), keys.size() - 1)));
        }

        boolean healthOk;
        for (int i = 0; i < pages; i++) {
            healthOk = healthCheck.isOk();
            logger.debug("Health check says {}. Class was {}", healthOk, healthCheck.getClass().getSimpleName());
            if (!healthOk) {
                logger.warn("Health check failed. Tasks will not be queued, but the ones that were already queued will allowed to finish");
                refill = false;
            } else {
                List<String> subList = keys.subList(i * batchSize, (Math.min((i + 1) * batchSize, keys.size())));

//                if (subList.size() < batchSize) {
                if (pages == i + 1) {
                    logger.debug("This looks like last batch, no need to refill");
                    refill = false;
                }

                // add workers to executor for selected batch
                for (final String key : subList) {
                    final FileAttributes value = localMissingFiles.get(key);

                    final String finalLocalParentDir = localParentDir;
                    final String localFilename = processLocalFilename(renamePattern, value.getRemoteFilename(), value.getRemoteFileMatcher());

                    if (logger.isDebugEnabled()) {
                        logger.debug("Submitting task to pull {} from {} to destination {} as file named {}", value.getRemoteFilename(), value.getRemoteParentDir(), localParentDir, localFilename);
                    }

                    if (csvAppend) {
                        results.add(executor.submit(() -> executeCsvGet(pool, copyToFiles, deleteAfterTransfer, moveAfterTransfer, moveToDir, progressFile, value, localFilename, finalLocalParentDir)));
                    } else if (appendToFile) {
                        results.add(executor.submit(() -> executeFileGet(pool, copyToFiles, deleteAfterTransfer, moveAfterTransfer, moveToDir, progressFile, value, localFilename, finalLocalParentDir)));
                    } else {
                        results.add(executor.submit(() -> executeFileDownload(pool, copyToFiles, deleteAfterTransfer, moveAfterTransfer, moveToDir, value, localFilename, finalLocalParentDir)));
                    }
                    submitted++;
                }
            }
            //poll for workers and check if they are completed or not
            while (!results.isEmpty()) {
                Future<FileAttributes> future = results.pollFirst();
                if (future == null) {
                    logger.warn("Future is null. This should not happen as queue still had items");
                    continue;
                }
                try {
                    final FileAttributes fileAttributes = future.get();
                    final String fileKey = fileAttributes.getRemotePath();
                    success++;

                    logger.debug("Updating history for {}", fileKey);
                    FileAttributes missingFile = localMissingFiles.get(fileKey);
                    updateHistory(fileKey, historyFile, missingFile, progressFile, appendToFile, appendRetries);

                    //check if there is a need to refill
                    //a higher number to make sure we can keep the pressure and not end up in situation
                    //where threads are idling
                    if (refill && executor.getQueue().size() <= ((2 * executor.getMaximumPoolSize() * 75) / 100)) {
                        logger.debug("Breaking to refill");
                        break;
                    }

                } catch (Throwable e) {
                    logger.error("Failed to complete download", e);
                    failed++;
                }
            }

//            if (submitted % 1000 == 0) {
            if (submitted % batchSize == 0) {
                //don't show it often
                logger.info("{} operations have been submitted so far out of total {}.", submitted, keys.size());
                logger.info("Shared queue currently have {} operations waiting with {} completed.", executor.getQueue().size(), executor.getCompletedTaskCount());
            }

            //FIXME: What if the job was updated form config and now the next execution time has changed to be closer? Consider getting job context and see if it updates when job is updated?
            //FIXME: Job is annotated to not allow concurrent execution so we should be safe with double execution but it would mean we have missfires in new job settings while this long running job will still be working on backlog in worst case
            //start to wind down. this will allow current tasks to complete, but not add any new ones
            if (this.nextExecutionTime - System.currentTimeMillis() <= windDownTime) {
                logger.info("Next execution is approaching. Stopping current execution. Don't worry, the remaining tasks and new ones will be handled in next execution.");

                //process remaining operations
                while (!results.isEmpty()) {
                    Future<FileAttributes> future = results.pollFirst();
                    if (future == null) {
                        logger.warn("Future is null. This should not happen as queue still had items");
                        continue;
                    }
                    try {
                        final FileAttributes fileAttributes = future.get();
                        final String fileKey = fileAttributes.getRemotePath();
                        success++;

                        logger.debug("Updating history for {}", fileKey);
                        FileAttributes missingFile = localMissingFiles.get(fileKey);
                        updateHistory(fileKey, historyFile, missingFile, progressFile, appendToFile, appendRetries);

                    } catch (Throwable e) {
                        logger.error("Failed to complete download", e);
                        failed++;
                    }
                }

                logger.info("{} tasks were still remaining to be executed. {} were executed", (keys.size() - submitted), submitted);

                break;
            }

            //check health status
            if (!healthOk) {
                logger.error("Health check status failed. Existing execution for {}. {} executed of which {} were successful", jobId, submitted, success);
                break;
            }
        }

        if (progressFile != null) {
            try {
                progressFile.save();
            } catch (IOException e) {
                logger.error("Failed to save progress file {}", progressFile.getFile().getName(), e);
            }
            progressFile.close();
        }

        logger.info("Transfer from {} executed. Of {} tasks {} were successfully executed and {} failed", jobId, submitted, success, failed);
        stats.incrementLastExecutedTasks(submitted);
        stats.incrementLastSuccessful(success);
        stats.incrementLastFailed(failed);
    }

    private void updateHistory(String fileKey, HistoryFile historyFile, FileAttributes missingFile, AppendProgressFile progressFile, boolean appendToFile, int appendRetries) {
        if (appendToFile) {
            ActiveFile activeFile = progressFile.getFile(missingFile.getRemotePath());
            if (activeFile == null) {
                logger.warn("Append progress for remote file {} is not created. Unable to save progress. Next download may have additional data", missingFile.getRemoteFilename());
            } else {
                //add to history if we have read too many times without any result
                logger.debug("appendRetries={} and retires={} for {}", appendRetries, activeFile.getSuccessiveEmptyResponses(), missingFile.getRemoteFilename());
                if (isRemoteFileInactive(appendRetries, activeFile.getSuccessiveEmptyResponses())) {
                    logger.info("Saving {} in history. No more append operations will be executed", missingFile.getRemoteFilename());
                    addToHistory(historyFile, fileKey, missingFile);

                    //remove from progress
                    progressFile.removeFile(missingFile.getRemotePath());
                }
            }
        } else {
            addToHistory(historyFile, fileKey, missingFile);
        }
    }

    /**
     * Check if the file is inactive or not. An inactive file works in context of appended files where more data
     * is added to a remote file.
     *
     * @param appendRetries            Empty retries allowed
     * @param successiveEmptyResponses Actual successive empty retries done for remote file
     * @return True if the file should be considered inactive, false otherwise
     */
    private boolean isRemoteFileInactive(int appendRetries, int successiveEmptyResponses) {
        return successiveEmptyResponses >= appendRetries;
    }

    private void addToHistory(HistoryFile historyFile, String fileKey, FileAttributes missingFile) {
        if (missingFile != null) {
            logger.debug("Adding {} in history", fileKey);
            if (logger.isTraceEnabled()) {
                logger.trace("{} attributes: {}", fileKey, missingFile.toString());
            }
            historyFile.addFile(fileKey, missingFile);
        } else {
            logger.warn("{} is not in list of missing remote files. Will not be saved in history.", fileKey);
        }
    }

    private FileAttributes executeFileDownload(ConnectionPool<SftpServerDetails, SftpConnection> pool, List<Path> copyToFiles, boolean deleteAfterTransfer, boolean moveAfterTransfer, String moveToDir, FileAttributes value, String localFilename, String finalLocalParentDir) {
        try {
            Thread.currentThread().setName(jobId + "::worker");

            final String remoteParentDir = value.getRemoteParentDir();
            final String remoteFilename = value.getRemoteFilename();

            SftpConnection connection = null;
            try {
                connection = pool.getPool().borrowObject(serverDetail);
                if (connection == null) {
                    throw new RuntimeException("Failed to get connection for " + serverDetail.getHost());
                }

                File dstLocalFile = Paths.get(finalLocalParentDir, localFilename).toFile();

                connection.download(remoteParentDir, remoteFilename, finalLocalParentDir, localFilename, false);
                value.setLocalPath(dstLocalFile.getAbsolutePath());
                copyFilesToMultipleDestination(copyToFiles, localFilename, dstLocalFile);

                try {
                    if (deleteAfterTransfer) {
                        connection.delete(value.getRemoteParentDir(), value.getRemoteFilename());
                    } else if (moveAfterTransfer) {
                        connection.move(value.getRemoteParentDir() + "/" + value.getRemoteFilename(), moveToDir + "/" + value.getRemoteFilename());
                    }
                } catch (ProtocolException e) {
                    logger.error("File downloaded but housekeeping failed", e);
                }
                return value;
            } finally {
                pool.getPool().returnObject(serverDetail, connection);
            }

        } catch (RuntimeException e) {
            //throw without further processing
            throw e;
        } catch (Throwable t) {
            logger.warn("An error occurred while downloading/getting remote file task", t);
            throw new RuntimeException("Failed to complete download/get operation", t);
        }
    }

    private FileAttributes executeFileGet(ConnectionPool<SftpServerDetails, SftpConnection> pool, List<Path> copyToFiles, boolean deleteAfterTransfer, boolean moveAfterTransfer, String moveToDir, AppendProgressFile progressFile, FileAttributes value, String localFilename, String finalLocalParentDir) {
        try {
            final String appendDelimiter = this.configuration.getPropertyAsString("finishDelimiter");
            final int appendRetries = this.configuration.getPropertyAsInteger("appendRetries", Constants.APPEND_RETRIES);

            Thread.currentThread().setName(jobId + "::worker");

            final String remoteParentDir = value.getRemoteParentDir();
            final String remoteFilename = value.getRemoteFilename();

            SftpConnection connection = null;
            try {
                connection = pool.getPool().borrowObject(serverDetail);
                if (connection == null) {
                    throw new RuntimeException("Failed to get connection for " + serverDetail.getHost());
                }

                File dstLocalFile = Paths.get(finalLocalParentDir, localFilename).toFile();

                logger.debug("File appending is enabled");
                //open local file with delimiter stream
                if (dstLocalFile.exists()) {
                    logger.debug("File {} already exists. Will append to same file", localFilename);
                }

                ActiveFile activeFile = getOrAddActiveFile(progressFile, value.getRemotePath());

                try (FileOutputStream fileOutputStream = new FileOutputStream(dstLocalFile, true)) {
                    final DelimiterOutputStream delimiterOutputStream;

                    delimiterOutputStream = new DelimiterOutputStream(fileOutputStream, appendDelimiter);
                    logger.debug("Created special output stream to stream remaining bytes from {} to file {}", activeFile.getTotalBytesRead(), localFilename);

                    connection.get(remoteParentDir, remoteFilename, delimiterOutputStream, activeFile.getTotalBytesRead());
                    int read = delimiterOutputStream.bytesWritten();

                    logger.debug("Read {} bytes from {} starting from {}", read, remoteFilename, activeFile.getTotalBytesRead());
                    if (read == 0) {
                        activeFile.incrementSuccessiveEmptyResponses();
                    }
                    activeFile.addByteReads(read);

                    if (delimiterOutputStream.remaining() > 0) {
                        logger.info("There were {} bytes still left in stream which were not written in file {}",
                                delimiterOutputStream.remaining(), localFilename);
                        if (logger.isTraceEnabled()) {
                            logger.trace("Dumping remaining bytes as string: {}", delimiterOutputStream.dumpRemaining());
                        }
                    }

                    delimiterOutputStream.close();
                } catch (FileNotFoundException e) {
                    logger.error("Failed to create or open file " + dstLocalFile.getAbsolutePath(), e);
                    throw new RuntimeException("Remote file " + remoteFilename + " cannot be processed as we failed to create or open local file " + localFilename);
                } catch (Exception e) {
                    logger.warn("Append operation failed.", e);
                    throw new RuntimeException("Append operation failed with an error", e);
                }

                //so an empty file will be created in case there is nothing to read because we read an old file
                if (dstLocalFile.length() == 0) {
                    logger.debug("File {} is empty, removing it", localFilename);
                    try {
                        Files.delete(dstLocalFile.toPath());
                    } catch (Exception e) {
                        logger.debug("Failed to delete file " + localFilename, e);
                    }
                } else {
                    value.setLocalPath(dstLocalFile.getAbsolutePath());
                    copyFilesToMultipleDestination(copyToFiles, localFilename, dstLocalFile);

                    try {
                        if (deleteAfterTransfer) {
                            logger.debug("deleteAfterTransfer is set to true");
                            if (isRemoteFileInactive(appendRetries, activeFile.getSuccessiveEmptyResponses())) {
                                connection.delete(value.getRemoteParentDir(), value.getRemoteFilename());
                            } else {
                                logger.debug("File is being appended and is considered still active. appendRetires={} and successive empty responses so far={}. File will not be deleted yet",
                                        appendRetries, activeFile.getSuccessiveEmptyResponses());
                            }
                        } else if (moveAfterTransfer) {
                            logger.debug("moveAfterTransfer is set to true");
                            if (isRemoteFileInactive(appendRetries, activeFile.getSuccessiveEmptyResponses())) {
                                connection.move(value.getRemoteParentDir() + "/" + value.getRemoteFilename(), moveToDir + "/" + value.getRemoteFilename());
                            } else {
                                logger.debug("File is being appended and is considered still active. appendRetires={} and successive empty responses so far={}. File will not be moved yet",
                                        appendRetries, activeFile.getSuccessiveEmptyResponses());
                            }
                        }
                    } catch (ProtocolException e) {
                        logger.error("File downloaded but housekeeping failed", e);
                    }
                }
                return value;
            } finally {
                pool.getPool().returnObject(serverDetail, connection);
            }

        } catch (RuntimeException e) {
            //throw without further processing
            throw e;
        } catch (Throwable t) {
            logger.warn("An error occurred while downloading/getting remote file task", t);
            throw new RuntimeException("Failed to complete download/get operation", t);
        }
    }

    private FileAttributes executeCsvGet(ConnectionPool<SftpServerDetails, SftpConnection> pool, List<Path> copyToFiles, boolean deleteAfterTransfer, boolean moveAfterTransfer, String moveToDir, AppendProgressFile progressFile, FileAttributes value, final String localFilenamePattern, String finalLocalParentDir) {
        try {
            Thread.currentThread().setName(jobId + "::worker");

            final String appendDelimiter = this.configuration.getPropertyAsString("finishDelimiter");
            final String csvDelimiter = this.configuration.getPropertyAsString("csvDelimiter");

            final DateTimeFormatter csvDateHeaderFormat = DateTimeFormatter.ofPattern(this.configuration.getPropertyAsString("csvDateHeaderFormat"));
            final DateTimeFormatter csvRopStartFormatter = DateTimeFormatter.ofPattern(this.configuration.getPropertyAsString("csvRopStartPattern"));
            final DateTimeFormatter csvRopEndFormatter = DateTimeFormatter.ofPattern(this.configuration.getPropertyAsString("csvRopEndPattern"));

            final int csvDateHeaderIndex = this.configuration.getPropertyAsInteger("csvDateHeaderIndex");
            final boolean hasHeader = this.configuration.getPropertyAsBoolean("hasHeader");
            final int appendRetries = this.configuration.getPropertyAsInteger("appendRetries", Constants.APPEND_RETRIES);

            final int rop = this.configuration.getPropertyAsInteger(Constants.CONFIG_CRON_ROP);

            final int osSize = 16384;

            final String remoteParentDir = value.getRemoteParentDir();
            final String remoteFilename = value.getRemoteFilename();

            SftpConnection connection = null;
            try {
                connection = pool.getPool().borrowObject(serverDetail);
                if (connection == null) {
                    throw new RuntimeException("Failed to get connection for " + serverDetail.getHost());
                }

                //process local file name with current information atleast
                String initialFilename = evaluateCsvLocalFilename(localFilenamePattern, csvRopStartFormatter, csvRopEndFormatter, rop, LocalDateTime.now());

                Path dstLocalPath = Paths.get(finalLocalParentDir, initialFilename);
                File dstLocalFile = dstLocalPath.toFile();

                logger.debug("Csv file appending is enabled");
                //open local file with delimiter stream
                if (dstLocalFile.exists()) {
                    logger.debug("File {} already exists. Will append to same file", initialFilename);
                }

                ActiveFile activeFile = getOrAddActiveFile(progressFile, value.getRemotePath());
                HashMap<String, File> generatedFilenames = null;

                try (FileOutputStream fileOutputStream = new FileOutputStream(dstLocalFile, true)) {

                    //TODO:currently, It's not very optimized as we try to create a name for each line
                    // optimize to check if the evaluated header date is within a rop period to keep using old file name

                    try (final GenerateCsvOutputStream csvOutputStream = new GenerateCsvOutputStream(fileOutputStream, activeFile.getHeader(),
                            localDateTime -> evaluateCsvLocalFilename(localFilenamePattern, csvRopStartFormatter, csvRopEndFormatter, rop, localDateTime),
                            finalLocalParentDir, initialFilename, appendDelimiter, csvDelimiter, csvDateHeaderFormat, csvDateHeaderIndex, hasHeader)) {

                        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(csvOutputStream, osSize)) {

                            connection.get(remoteParentDir, remoteFilename, bufferedOutputStream, activeFile.getTotalBytesRead());
                            int read = csvOutputStream.bytesWritten();

                            logger.debug("Read {} bytes from {} starting from {}", read, remoteFilename, activeFile.getTotalBytesRead());
                            if (read == 0) {
                                activeFile.incrementSuccessiveEmptyResponses();
                            } else if ((activeFile.getHeader() == null || activeFile.getHeader().isEmpty()) && hasHeader) {
                                logger.debug("Going to save header in progress file");
                                String header = csvOutputStream.getHeader();
                                if (header == null) {
                                    logger.warn("Header read is null. There will be no header information available");
                                    activeFile.setHeader("");
                                } else {
                                    logger.debug("Saving header {}", header);
                                    activeFile.setHeader(header);
                                }
                            }
                            activeFile.addByteReads(read);

                            if (csvOutputStream.remaining() > 0) {
                                logger.info("There were {} bytes still left in stream which were not written in generated file", csvOutputStream.remaining());
                                if (logger.isTraceEnabled()) {
                                    logger.trace("Dumping remaining bytes as string: {}", csvOutputStream.dumpRemaining());
                                }
                            }

                            generatedFilenames = csvOutputStream.getGeneratedFiles();
                            //so an empty file will be created in case there is nothing to read because we read an old file
                            if (dstLocalFile.length() == 0) {
                                logger.debug("File {} is empty, removing it", initialFilename);
                                try {
                                    Files.delete(dstLocalFile.toPath());
                                } catch (Exception e) {
                                    logger.debug("Failed to delete file " + initialFilename, e);
                                }
                            } else {
                                //put the initial file as well
                                generatedFilenames.put(initialFilename, dstLocalFile);
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    logger.error("Failed to create or open file " + dstLocalFile.getAbsolutePath(), e);
                    throw new RuntimeException("Remote file " + remoteFilename + " cannot be processed as we failed to create or open local file " + initialFilename);
                } catch (Exception e) {
                    logger.warn("Append operation failed.", e);
                    throw new RuntimeException("Append operation failed with an error", e);
                }

                //TODO: there can be multiple files generated for one remote file. How will that be handled?
                value.setLocalPath(dstLocalFile.getAbsolutePath());
                copyFilesToMultipleDestination(copyToFiles, generatedFilenames);

                try {
                    if (deleteAfterTransfer) {
                        logger.debug("deleteAfterTransfer is set to true");
                        if (isRemoteFileInactive(appendRetries, activeFile.getSuccessiveEmptyResponses())) {
                            connection.delete(value.getRemoteParentDir(), value.getRemoteFilename());
                        } else {
                            logger.debug("File is being appended and is considered still active. appendRetires={} and successive empty responses so far={}. File will not be deleted yet",
                                    appendRetries, activeFile.getSuccessiveEmptyResponses());
                        }
                    } else if (moveAfterTransfer) {
                        logger.debug("moveAfterTransfer is set to true");
                        if (isRemoteFileInactive(appendRetries, activeFile.getSuccessiveEmptyResponses())) {
                            connection.move(value.getRemoteParentDir() + "/" + value.getRemoteFilename(), moveToDir + "/" + value.getRemoteFilename());
                        } else {
                            logger.debug("File is being appended and is considered still active. appendRetires={} and successive empty responses so far={}. File will not be moved yet",
                                    appendRetries, activeFile.getSuccessiveEmptyResponses());
                        }
                    }
                } catch (ProtocolException e) {
                    logger.error("File downloaded but housekeeping failed", e);
                }
                return value;
            } finally {
                pool.getPool().returnObject(serverDetail, connection);
            }

        } catch (RuntimeException e) {
            //throw without further processing
            throw e;
        } catch (Throwable t) {
            logger.warn("An error occurred while downloading/getting remote file task", t);
            throw new RuntimeException("Failed to complete download/get operation", t);
        }
    }

    private ActiveFile getOrAddActiveFile(AppendProgressFile progressFile, String remotePath) {
        ActiveFile activeFile = progressFile.getFile(remotePath);
        if (activeFile == null) {
            //its a new file, add it
            activeFile = new ActiveFile();
            progressFile.addFile(remotePath, activeFile);
            logger.debug("Added new active file for {}", remotePath);
        } else {
            logger.debug("Got active file for {}", remotePath);
        }

        return activeFile;
    }

    private String createIfNotPresentParentDir(String localParentDir) {
        logger.debug("Current date hour will be added under local parent dir");
        String dateHour = new SimpleDateFormat("yyyyMMddHH").format(Calendar.getInstance().getTime());
        if (localParentDir.endsWith("/")) {
            localParentDir = localParentDir + dateHour;
        } else {
            localParentDir = localParentDir + "/" + dateHour;
        }
        File localDirFile = new File(localParentDir);
        if (!localDirFile.exists()) {
            logger.info("Creating {} as it does not exists", localParentDir);
            if (!localDirFile.mkdir()) {
                logger.error("Failed to create {}. Collection will not be possible", localParentDir);
                return null;
            }
        }
        return localParentDir;
    }

    private String applyFileRenameRegex(final Matcher remoteFilePatternMatcher, final String remoteFilename,
                                        final String localFileReplacePattern) {

        if (localFileReplacePattern == null || "".equals(localFileReplacePattern)) {
            return remoteFilename;
        }

        logger.debug("Using local file replace pattern {}", localFileReplacePattern);

        String updatedFileName = remoteFilePatternMatcher.replaceFirst(localFileReplacePattern);
        logger.debug("Updated filename after group replace: {}", updatedFileName);

        updatedFileName = updatedFileName.replaceAll(Constants.PATTERN_REPLACE_IPADDRESS, configuration.getPropertyAsString(Constants.CONFIG_IPADDRESS, ""));
        logger.debug("Updated filename after {} replace: {}", Constants.PATTERN_REPLACE_IPADDRESS, updatedFileName);

        updatedFileName = updatedFileName.replaceAll(Constants.PATTERN_REPLACE_HASHCODE, HelpUtil.timeHashCode(configuration.getPropertyAsString(Constants.CONFIG_NODE_NAME)));
        logger.debug("Updated filename after {} replace: {}", Constants.PATTERN_REPLACE_HASHCODE, updatedFileName);

        return updatedFileName;
    }

    private void copyFilesToMultipleDestination(List<Path> copyToFiles, String localFilename, File localFile) {
        copyFilesToMultipleDestination(copyToFiles, Collections.singletonMap(localFilename, localFile));
    }

    /**
     * Copy download files to provided paths
     *
     * @param copyToFiles List of paths where downloaded files should be copied
     * @param localFiles  Map of files to be copied. Key is the local file name, value if the File object identifying the download file
     */
    private void copyFilesToMultipleDestination(List<Path> copyToFiles, Map<String, File> localFiles) {
        if (copyToFiles != null && copyToFiles.size() > 0) {

            if (localFiles == null || localFiles.size() == 0) {
                logger.info("No files downloaded ");
                return;
            }

            for (Map.Entry<String, File> entry : localFiles.entrySet()) {
                File dstFile = entry.getValue();

                if (!dstFile.exists()) {
                    logger.error("File {} should be downloaded but could not be found. Cannot copy it to other destinations", dstFile.getAbsolutePath());
                } else {
                    for (Path copyDestinationDir : copyToFiles) {
                        Path copyDestinationFilePath = Paths.get(copyDestinationDir.toString(), entry.getKey());
                        File copyDestinationFile = copyDestinationFilePath.toFile();

                        if (copyDestinationFile.exists()) {
                            logger.info("Target file {} already exists. Will not overwrite it with downloaded file", copyDestinationFile.getAbsolutePath());
                        } else {
                            try {
                                Files.copy(dstFile.toPath(), copyDestinationFilePath, StandardCopyOption.COPY_ATTRIBUTES);
                            } catch (IOException e) {
                                logger.error("Failed to copy file to " + copyDestinationDir, e);
                            }
                        }
                    }
                }
            }
        }
    }
//
//    private void copyFilesToMultipleDestination(List<Path> copyToFiles, String localFilename, Path dstPath, File dstFile) {
//        if (copyToFiles != null && copyToFiles.size() > 0) {
//
//            if (!dstFile.exists()) {
//                logger.error("File {} should be downloaded but could not be found. Cannot copy it to other destinations", dstFile.getAbsolutePath());
//            } else {
//                for (Path copyDestinationDir : copyToFiles) {
//                    Path copyDestinationFilePath = Paths.get(copyDestinationDir.toString(), localFilename);
//                    File copyDestinationFile = copyDestinationFilePath.toFile();
//                    if (copyDestinationFile.exists()) {
//                        logger.info("Target file {} already exists. Will not overwrite it with downloaded file", copyDestinationFile.getAbsolutePath());
//                    } else {
//                        try {
//                            Files.copy(dstPath, copyDestinationFilePath, StandardCopyOption.COPY_ATTRIBUTES);
//                        } catch (IOException e) {
//                            logger.error("Failed to copy file to " + copyDestinationDir, e);
//                        }
//                    }
//                }
//            }
//        }
//    }

    private HashMap<String, FileAttributes> cleanupHistory
            (LinkedHashMap<String, FileAttributes> remoteFiles, HistoryFile historyFile) {
        logger.debug("Cleaning up history based on total remote files discovered");
        HashMap<String, FileAttributes> cleanedUp = new HashMap<>();

//        int cleaned = 0;
        List<String> filesInHistory = historyFile.getFileNames();
        if (filesInHistory != null) {
            for (String remotePath : filesInHistory) {
                if (!remoteFiles.containsKey(remotePath)) {
                    //remote from history
                    logger.debug("Removing {} from history for {}", remotePath, jobId);
                    cleanedUp.put(remotePath, historyFile.getFile(remotePath));
                    historyFile.removeFile(remotePath);
//                    cleaned++;
                }
            }
        }
        if (cleanedUp.size() > 0) {
            logger.debug("{} files cleaned up from history as they no longer exist on {}", cleanedUp.size(), jobId);
        } else {
            logger.debug("Nothing to clean from history file for {}", jobId);
        }

        return cleanedUp;
    }

    private Stream<Map.Entry<String, FileAttributes>> getRemoteFiles
            (ConnectionPool<SftpServerDetails, SftpConnection> pool, final String remotePath,
             final String parentDirPattern, final String fileNamePattern) throws Exception {
        long opTime = 0;
        if (parentDirPattern == null) {
            logger.debug("No parent directory pattern is provided. Expecting files in {}", remotePath);
            opTime = System.currentTimeMillis();
            try {
                LinkedHashMap<String, FileAttributes> discoveredFiles = getRemoteFiles(pool, remotePath, fileNamePattern, SftpConnection.FilterType.FILE);
                return discoveredFiles.entrySet().stream();
            } finally {
                logger.debug("Getting FILE list from {} took {} ms to complete", remotePath, System.currentTimeMillis() - opTime);
            }
        } else {
            logger.debug("Listing directories inside {}", remotePath);
            opTime = System.currentTimeMillis();
            LinkedHashMap<String, FileAttributes> parentDirMap = getRemoteFiles(pool, remotePath, parentDirPattern, SftpConnection.FilterType.DIR);
            logger.debug("Getting DIR list from {} took {} ms to complete", remotePath, System.currentTimeMillis() - opTime);
            if (parentDirMap.size() == 0) {
                logger.info("No parent directories found inside {} matching pattern {}", remotePath, parentDirPattern);
                return Stream.empty();
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Found directories: {}", parentDirMap.values().stream().map(FileAttributes::toString).collect(Collectors.joining("\n")));
            }

            return parentDirMap.entrySet().stream().parallel()
                    .flatMap(f -> {
                        final FileAttributes value = f.getValue();
                        final String path = value.getRemoteParentDir().endsWith("/") ? value.getRemoteParentDir() + value.getRemoteFilename() : value.getRemoteParentDir() + "/" + value.getRemoteFilename();
                        try {
                            return getRemoteFiles(pool, path, fileNamePattern, SftpConnection.FilterType.FILE).entrySet().stream();
                        } catch (Exception e) {
                            logger.error("Failed to get remote files for path {} with pattern {}. Path will be ignored", path, fileNamePattern);
                            return Stream.empty();
                        }
                    });
        }
    }

    /**
     * Return a map of remote files from a path that satisfy the passed filters
     *
     * @param pool            Connection pool instance to take connections from
     * @param remotePath      Remote path from which files/dir will be listed
     * @param fileNamePattern Pattern to use to match files name with
     * @param filterOn        Filter either on file or directories
     * @return A stream instance referencing file names and file attributes of files matching the filter
     * @throws Exception
     */
    private LinkedHashMap<String, FileAttributes> getRemoteFiles
    (ConnectionPool<SftpServerDetails, SftpConnection> pool, final String remotePath,
     final String fileNamePattern, SftpConnection.FilterType filterOn) throws Exception {

        long opStart = System.currentTimeMillis();
        SftpConnection connection = null;
        try {
            connection = pool.getPool().borrowObject(serverDetail);
            logger.debug("Getting connection from pool took {} ms", System.currentTimeMillis() - opStart);
            opStart = System.currentTimeMillis();

            if (connection == null) {
                throw new RuntimeException("Failed to get connection for " + serverDetail.getHost());
            }
            //first get list of remote files
            logger.debug("Getting remote file list from {}", serverDetail.getHost());
            return connection.listRemoteFiles(remotePath, fileNamePattern, filterOn, this.fileFilter);

        } finally {
            pool.getPool().returnObject(serverDetail, connection);
            logger.debug("2. Getting {} list from {} took {} ms to complete", filterOn.name(), remotePath, System.currentTimeMillis() - opStart);
        }
    }

    private String processLocalFilename(final String renamePattern, final String remoteFilename, final Matcher remoteFilePatternMatcher) {
        String updatedFileName = renamePattern;

        for (FilenameProcessor processor : filenameProcessors) {
            logger.debug("Applying processor to filename {}", updatedFileName);
            updatedFileName = processor.process(updatedFileName, remoteFilename, remoteFilePatternMatcher);
        }

        return updatedFileName;
    }

    private AppendProgressFile getProgressFile(final boolean appendToFile, final String jobId, final File historyDb) {
        if (!appendToFile) {
            return null;
        }
        try {
            AppendProgressFile appendProgressFile = AppendProgressManager.instance.getProgress(jobId, historyDb);
            appendProgressFile.read(true);
            return appendProgressFile;
        } catch (IOException e) {
            logger.error("Failed to get progress information for " + jobId + ". File get operation will fail", e);
            return null;
        }
    }

    @Override
    public int totalRetries() {
        return retries;
    }

    public String getJobId() {
        return jobId;
    }

    /**
     * Calculate connection stats and print them
     *
     * @param workerId
     * @param maxSftpConn
     * @param pool
     */
    private void printPoolStats(String workerId, int maxSftpConn, ConnectionPool<
            SftpServerDetails, SftpConnection> pool) {
        List<SftpConnection> borrowed = new ArrayList<>(maxSftpConn);
        int totalObjects = 0;

        int totalDownload = 0;
        long totalTime = 0;
        long totalSize = 0;

        final DecimalFormat df = new DecimalFormat("#.#");
        df.setRoundingMode(RoundingMode.CEILING);

        byte exhaustAction = pool.getPool().getWhenExhaustedAction();
        try {
            pool.getPool().setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL);

            for (; ; totalObjects++) {
                try {

                    SftpConnection connection = pool.getPool().borrowObject(serverDetail);

                    totalDownload += connection.totalDownloadRequests();
                    totalTime += connection.totalDownloadTime();
                    totalSize += connection.totalDownloadSize();
                    borrowed.add(connection);

                } catch (Exception e) {
                    break;
                }
            }
        } finally {
            pool.getPool().setWhenExhaustedAction(exhaustAction);
        }

        //return objects borrowed
        for (SftpConnection connection : borrowed) {
            try {
                pool.getPool().returnObject(serverDetail, connection);
            } catch (Exception ignored) {
            }
        }

        float totalSec = (float) totalTime / 1000;
        float totalKb = (float) totalSize / 1024;
        if (totalSec == 0.0) {
            totalSec = 1.0f;
        }
        logger.info("For {}, total connections were: {}. total download requests: {}, total bytes downloaded: {}, total time spent on download: {} ms", workerId, totalObjects, totalDownload, totalSize, totalTime);
        logger.info("For {}, total throughput was {} downloads/sec and total speed was {} KB/sec", workerId, df.format(totalDownload / totalSec), df.format(totalKb / totalSec));

    }

    /**
     * Evaluate file name after replacing placeholders for ROP start and end times if provided in file
     *
     * @param localFilename        Local file name pattern
     * @param csvRopStartFormatter Start ROP time formatter
     * @param csvRopEndFormatter   End ROP time formatter
     * @param rop                  ROP period in minutes
     * @param localDateTime        Current time
     * @return Updated local file name
     */
    private String evaluateCsvLocalFilename(String localFilename, DateTimeFormatter csvRopStartFormatter, DateTimeFormatter csvRopEndFormatter, int rop, LocalDateTime localDateTime) {
        //given a local time we will have to create a new file name according to
        //rop
        String filenameFormat = localFilename;

        LocalDateTime startRop = getRopStartTime(localDateTime, rop);
        LocalDateTime endRop = startRop.plusMinutes(rop);

        filenameFormat = HelpUtil.replaceAll(filenameFormat, Constants.PATTERN_REPLACE_CSV_ROP_START_DATETIME, csvRopStartFormatter.format(startRop));
        filenameFormat = HelpUtil.replaceAll(filenameFormat, Constants.PATTERN_REPLACE_CSV_ROP_END_DATETIME, csvRopEndFormatter.format(endRop));

        return filenameFormat;
    }

    /**
     * Get start ROP time from provided.
     *
     * @param dateTime    Date time instance used to calculate start ROP time
     * @param ropInterval ROP interval
     * @return Local date time instance fixed to start ROP interval
     */
    private static LocalDateTime getRopStartTime(LocalDateTime dateTime, int ropInterval) {
        dateTime = dateTime.truncatedTo(ChronoUnit.HOURS).plusMinutes((long) ropInterval * (dateTime.getMinute() / ropInterval));
        return dateTime;
    }

}
