package com.ericsson.sim.sftp;

import com.ericsson.sim.common.history.HistoryFile;
import com.ericsson.sim.common.history.model.FileAttributes;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.plugins.model.ProtocolException;
import com.ericsson.sim.plugins.model.ServerDetails;
import com.ericsson.sim.plugins.protocols.Connection;
import com.ericsson.sim.sftp.filter.FileFilter;
import com.jcraft.jsch.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//TODO: make the class internal once decoupled from engine
public class SftpConnection implements Connection {
    private static final Logger logger = LogManager.getLogger(SftpConnection.class);

    private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
    private static final int KEEPALIVE = 30000; //30 seconds heartbeat

    //    private static final AtomicInteger step = new AtomicInteger(0);
    public String id = "";

    private Session session;
    private ChannelSftp channel;
    private final JSch jsch;

    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger totalSuccess = new AtomicInteger(0);
    private final AtomicLong totalTime = new AtomicLong();

//    public static final BinaryOperator<FileAttributes> MERGE_FUNCTION = (v1, v2) -> v1.getMtime() >= v2.getMtime() ? v1 : v2;
//    public static final Comparator<Map.Entry<String, FileAttributes>> FILE_COMPARATOR = (o1, o2) -> o2.getValue().getMtime() - o1.getValue().getMtime();
//    public static final Comparator<ChannelSftp.LsEntry> LS_ENTRY_COMPARATOR = (o1, o2) -> o2.getAttrs().getMTime() - o1.getAttrs().getMTime();

    //Take care of calculating amount of bytes transferred through this connection
    private static class TotalProgressMonitor implements SftpProgressMonitor {
        private final AtomicLong bytesTransferred = new AtomicLong();

        @Override
        public void init(int op, String src, String dest, long max) {
        }

        @Override
        public boolean count(long count) {
            bytesTransferred.addAndGet(count);
            return true;
        }

        @Override
        public void end() {

        }

        public final void resetTotalBytesTransferred() {
            this.bytesTransferred.set(0);
        }

        public long getBytesTransferred() {
            return bytesTransferred.get();
        }
    }

    private static class AppendProgressMonitor implements SftpProgressMonitor {

        private final SftpProgressMonitor totalProgressMonitor;

        private final AtomicLong bytesTransferred = new AtomicLong();

        public AppendProgressMonitor(SftpProgressMonitor totalProgressMonitor) {
            this.totalProgressMonitor = totalProgressMonitor;
        }

        @Override
        public void init(int op, String src, String dest, long max) {
            totalProgressMonitor.init(op, src, dest, max);
            bytesTransferred.set(0);
        }

        @Override
        public boolean count(long count) {
            totalProgressMonitor.count(count);
            bytesTransferred.addAndGet(count);
            return true;
        }

        @Override
        public void end() {
            totalProgressMonitor.end();
        }

        public long getBytesTransferred() {
            return bytesTransferred.get();
        }
    }

    private final TotalProgressMonitor progressMonitor = new TotalProgressMonitor();

//    private final SftpProgressMonitor progressMonitor = new SftpProgressMonitor() {
//        @Override
//        public void init(int op, String src, String dest, long max) {
//        }
//
//        @Override
//        public boolean count(long count) {
//            totalBytesTransferred.addAndGet(count);
//            return true;
//        }
//
//        @Override
//        public void end() {
//        }
//    };

    static {

        JSch.setLogger(new JSCHLogger());
//        JSch.setLogger(new com.jcraft.jsch.Logger() {
//            Path path = Paths.get("/var/ericsson/SIM/logs/jsch.log");
//
//            @Override
//            public boolean isEnabled(int level) {
//                return true;
//            }
//
//            public void log(int level, String message) {
//                try {
//                    StandardOpenOption option =
//                            !Files.exists(path) ? StandardOpenOption.CREATE : StandardOpenOption.APPEND;
//                    Files.write(path, java.util.Arrays.asList(message), option);
//                } catch (IOException e) {
//                    System.err.println(message);
//                }
//            }
//        });
    }

    public SftpConnection() {
//        id = "connection-" + step.getAndIncrement();
        this.jsch = new JSch();
    }

    @Override
    public void connect(ServerDetails serverDetail) throws ProtocolException {

        final long start = System.currentTimeMillis();

        this.totalRequests.set(0);
        this.totalTime.set(0);
        this.totalSuccess.set(0);

        try {
            logger.debug("Opening session towards {}:{} as user {}", serverDetail.getHost(), serverDetail.getPort(), serverDetail.getUser());
            logger.debug("Using auth type: {}", serverDetail.getAuthType().name());

            if (serverDetail.getAuthType() == ServerDetails.AuthType.NONE) {
                logger.debug("Neither password nor key file is provided. Will try to establish connection without any authentication method");
                logger.debug("Setting known host: {}", serverDetail.getPrivateKey().getAbsolutePath());

                jsch.addIdentity(serverDetail.getPrivateKey().getAbsolutePath());

            } else if (serverDetail.getAuthType() == ServerDetails.AuthType.KEYFILE) {
                logger.debug("Using private key based authentication");
                File keyFile = serverDetail.getKeyFile();
                if (keyFile == null) {
                    logger.warn("No private key file provided");
                    throw new ProtocolException("No private key file provided for SSH authentication");
                } else if (!keyFile.exists()) {
                    logger.warn("Key file {} does not exists", keyFile.getAbsolutePath());
                    throw new ProtocolException("Key file provided for SSH authentication does not exists");
                } else if (!keyFile.canRead()) {
                    logger.warn("Key file {} exists but cannot be read", keyFile.getAbsolutePath());
                    throw new ProtocolException("Key file provided for SSH authentication cannot be read");
                }
                jsch.addIdentity(keyFile.getAbsolutePath());
            }

            this.session = jsch.getSession(serverDetail.getUser(), serverDetail.getHost(), serverDetail.getPort());

            logger.debug("Setting keep-alive to {} millisecond", KEEPALIVE);
            session.setServerAliveInterval(KEEPALIVE);

            session.setConfig("PreferredAuthentications", "publickey,password");
            session.setConfig("StrictHostKeyChecking", "no");

            if (serverDetail.getAuthType() == ServerDetails.AuthType.PASSWORD) {
                logger.debug("Using password based authentication");
                this.session.setPassword(serverDetail.getPassword());
            }
        } catch (JSchException e) {
            logger.error("Failed to open session towards {}: {}", serverDetail.getHost(), e.getMessage());
            throw new ProtocolException(e);
        }

        //connect timeout in milliseconds
        try {
            logger.debug("Connecting session with connect timeout: {} millisec", CONNECT_TIMEOUT);
            this.session.connect(CONNECT_TIMEOUT);
        } catch (JSchException e) {
            logger.error("Failed to connect session towards {}: {}", serverDetail.getHost(), e.getMessage());
            throw new ProtocolException(e);
        }
        logger.debug("Session connected with {}. Opening SFTP channel now.", serverDetail.getHost());

        //open channel
        try {
            logger.debug("Creating SFTP channel");
            channel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            logger.error("Failed to open SFTP channel towards {}: {}", serverDetail.getHost(), e.getMessage());
            throw new ProtocolException(e);
        }
        try {
            logger.debug("Connecting with SFTP channel");
            channel.connect();
        } catch (JSchException e) {
            logger.error("Failed to connect SFTP channel towards {}: {}", serverDetail.getHost(), e.getMessage());
            throw new ProtocolException(e);
        }
        logger.debug("SFTP channel created successfully");
        logger.debug("Call took {} ms", System.currentTimeMillis() - start);
    }

    @Override
    public void disconnect() {

        if (this.channel != null) {
            this.channel.disconnect();
            logger.debug("SFTP channel closed");
        }
        if (this.session != null) {
            this.session.disconnect();
            logger.debug("Session closed");
            logger.debug("Connection closed with {}", session.getHost());
            logger.debug("Throughput for this connection  with {} was {} millisecond/download",
                    session.getHost(),
                    String.format(Locale.getDefault(), "%.1f", this.downloadThroughput()));
        }

        this.totalRequests.set(0);
        this.totalTime.set(0);
        this.totalSuccess.set(0);
        this.progressMonitor.resetTotalBytesTransferred();

//        StringBuilder builder = new StringBuilder();
//        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
//        for (int i = 1; i < elements.length; i++) {
//            StackTraceElement s = elements[i];
//            builder.append("\tat ")
//                    .append(s.getClassName())
//                    .append(".")
//                    .append(s.getMethodName())
//                    .append("(")
//                    .append(s.getFileName())
//                    .append(":")
//                    .append(s.getLineNumber()).append(")\n");
//        }
//        logger.info("Disconnect was called: \n{}", builder.toString());
    }

    @Override
    public boolean isConnected() {
        if (this.channel != null) {
            return this.channel.isConnected();
        }

        logger.debug("SFTP channel is not created yet");
        return false;
    }

    public static enum FilterType {
        DIR,
        FILE
    }

    /**
     * Get list of remote files/dir from remote parent directory following name pattern.
     *
     * @param remoteDir       Remote parent directory absolute path
     * @param fileNamePattern Remote file/dir name pattern to match
     * @param filterOn        Filter either for directory or files
     * @return A map of files/dir being found on remote server following the filters. The map is no sorted
     * @throws ProtocolException
     */
    public LinkedHashMap<String, FileAttributes> listRemoteFiles(final String remoteDir, final String fileNamePattern, final FilterType filterOn, final FileFilter fileFilter) throws ProtocolException {
        if (this.session == null) {
            logger.warn("SFTP session not initialized yet. Unable to list remote dir {}", remoteDir);
            throw new ProtocolException("Session not initialized yet. Unable to list remote dir");
        }

        if (this.channel == null) {
            logger.warn("SFTP channel not initialized yet. Unable to list remote dir {}", remoteDir);
            throw new ProtocolException("Channel not initialized yet. Unable to list remote dir");
        }

        logger.debug("Listing files/dir in {} from {} on connection {}", remoteDir, this.session.getHost(), id);
        logger.debug("Filter based on {}", filterOn.name());

        try {
//            if (remoteDir != null && !"".equals(remoteDir)) {
//                //set the remote and local working directory
//                logger.debug("Setting current remote dir to: {}", remoteDir);
//                this.channel.cd(remoteDir);
//            } else {
////                logger.debug("Remote path is not set, files are expected to be in root directory for user {}", session.getUserName());
//                throw new ProtocolException("Remote path is not set. Update node type configuration related to remote path setting before trying again");
//            }

            if (remoteDir == null || "".equals(remoteDir)) {
                throw new ProtocolException("Remote path is not set. Update node type configuration related to remote path setting before trying again");
            }

            //breakdown path to check if there are any glob characters. Jsch only supports * and ? in last part of path.
            //Anywhere else would be illegal
            final File remoteDirPath = new File(remoteDir);
            final String remotePathChildName = remoteDirPath.getName();
            final String parentRemoteDir = HelpUtil.replaceLast(remoteDir, remotePathChildName, "");// remoteDirPath.getParent();

//            String[] pathSplit = Arrays.stream(remoteDir.split(Pattern.quote(File.separator))).toArray(String[]::new);
//            final boolean globSearch = pathSplit[pathSplit.length - 1].contains("*") || pathSplit[pathSplit.length - 1].contains("?");
            if (parentRemoteDir.contains("*") || parentRemoteDir.contains("?")) {
                logger.error("remoteDir contains glob character at invalid location. They are only allowed in last part of path");
                logger.info("remoteDir: {}", remoteDir);
                throw new ProtocolException("Invalid remote directory path. Glob character (*,?) are only allowed in last part of path");
            }
            final boolean globSearch = remotePathChildName.contains("*") || remotePathChildName.contains("?");
            logger.debug("globSearch: {}", globSearch);

            LinkedList<String> baseDirMatchingGlob = new LinkedList<>();
            LinkedHashMap<String, FileAttributes> filteredFiles = new LinkedHashMap<>();
            AtomicInteger totalFilesFromLs = new AtomicInteger();
            final Pattern pattern = Pattern.compile(fileNamePattern);

            if (globSearch) {
                //incase we have glob characters in path, we will have to handle it a little different

                //get parent by replacement
//                final String originalParentRemoteDir = remoteDir.replace(remotePathChildName, "");

                //first we have to list current glob matching paths
                logger.info("globSearch is enabled");
                this.channel.ls(remoteDir, entry -> {
                    final String remotePath = HistoryFile.getRemotePath(parentRemoteDir, entry.getFilename());
                    logger.trace("Checking base file/dir {}", remotePath);
//                    if ((filterOn == FilterType.DIR) == entry.getAttrs().isDir()) {
                    //Glob search can return whatever matches the pattern. It can be a file or a dir
                    //in case we have a file, we can match it with the file pattern and add it to matched files
                    //in case we have a dir, we can add it to base directory list and search for files in it later
                    if (entry.getAttrs().isDir()) {
                        logger.debug("{} is a directory, adding to base directory list", entry.getFilename());
                        //add all to map
                        baseDirMatchingGlob.add(remotePath);
                    } else if (entry.getAttrs().isFifo()) {
                        logger.debug("{} is a file, matching it with filename pattern", entry.getFilename());
                        //parentRemoteDir will be the parent dir here.
                        FileAttributes fileAttributes = fileMatch(parentRemoteDir, entry, pattern, fileFilter);
                        if (fileAttributes != null) {
                            filteredFiles.put(fileAttributes.getRemotePath(), fileAttributes);
                        }
                    } else {
                        logger.debug("Ignoring {} as it neither DIR nor FILE", remotePath);
                    }
                    return ChannelSftp.LsEntrySelector.CONTINUE;
                });

                logger.info("Total base directories discovered: {}", baseDirMatchingGlob.size());
                if (logger.isDebugEnabled()) {
                    logger.debug("baseDirMatchingGlob: {}", String.join(", ", baseDirMatchingGlob));
                }
            } else {
                //there is only one base dir
                logger.debug("Single parent directory: {}", remoteDir);
                baseDirMatchingGlob.add(remoteDir);
            }

            baseDirMatchingGlob.forEach(baseDir ->
            {
                try {
                    this.channel.ls(baseDir, entry -> {
                        if ((filterOn == FilterType.DIR) == entry.getAttrs().isDir()) {
                            //if not DIR, check if its in blacklist
                            FileAttributes fileAttributes = fileMatch(baseDir, entry, pattern, fileFilter);
                            if (fileAttributes != null) {
                                filteredFiles.put(fileAttributes.getRemotePath(), fileAttributes);
                            }
//                            final String remotePath = HistoryFile.getRemotePath(baseDir, entry.getFilename());
//                            //if it passes initial filtering, check the name
//                            Matcher remoteFileMatcher = pattern.matcher(entry.getFilename());
//                            if (remoteFileMatcher.matches()) {
//                                //if it matches the name
////                        filteredFiles.put(remotePath, new FileAttributes(remoteDir, entry.getFilename(), entry.getAttrs().getMTime(), entry.getAttrs().getSize()));
//                                filteredFiles.put(remotePath, new FileAttributes(remotePath, "", entry.getAttrs().getMTime(), remoteFileMatcher));
//                            }
                            totalFilesFromLs.getAndIncrement();
                        }

                        return ChannelSftp.LsEntrySelector.CONTINUE;
                    });
                } catch (SftpException e) {
                    throw new RuntimeException(e);
                }
            });

//            this.channel.ls(remoteDir, entry -> {
//                if ((filterOn == FilterType.DIR) == entry.getAttrs().isDir()) {
//                    //if not DIR, check if its in blacklist
//                    final String remotePath = HistoryFile.getRemotePath(remoteDir, entry.getFilename());
//                    //if it passes initial filtering, check the name
//                    if (pattern.matcher(entry.getFilename()).matches()) {
//                        //if it matches the name
////                        filteredFiles.put(remotePath, new FileAttributes(remoteDir, entry.getFilename(), entry.getAttrs().getMTime(), entry.getAttrs().getSize()));
//                        filteredFiles.put(remotePath, new FileAttributes(remotePath, "", entry.getAttrs().getMTime()));
//                    }
//                    totalFilesFromLs.getAndIncrement();
//                }
//
//                return ChannelSftp.LsEntrySelector.CONTINUE;
//            });


            logger.debug("Total {} files were found. They were filtered based on type='{}' and name pattern='{}'. After filtering we got {} files",
                    totalFilesFromLs.get(), filterOn.name(), fileNamePattern, filteredFiles.size());
//            logger.debug("Out of {} discovered files, {} files are selected after filtering", totalFilesFromLs.get(), filteredFiles.size());

            return filteredFiles;

        } catch (SftpException | RuntimeException e) {
            throw new ProtocolException(e);
        }
    }

    @Override
    public void download(String remotePath, String remoteFileName, String localPath, String localFileName, boolean setModifiedTime) throws ProtocolException {

        totalRequests.incrementAndGet();
        final long start = System.currentTimeMillis();
        try {

            if (this.session == null) {
                logger.warn("SFTP session not initialized yet. Unable to transfer file {}", remoteFileName);
                throw new ProtocolException("Session not initialized yet. Unable to transfer file");
            }

            if (this.channel == null) {
                logger.warn("SFTP channel not initialized yet. Unable to transfer file {}", remoteFileName);
                throw new ProtocolException("Channel not initialized yet. Unable to transfer file");
            }
            if (!this.channel.isConnected()) {
                logger.warn("SFTP channel is not connected. Unable to transfer file {}", remoteFileName);
                throw new ProtocolException("Channel is not connected. Unable to transfer file");
            }

            logger.debug("Pulling {} from {} on server {} on connection {}", remoteFileName, remotePath, this.session.getHost(), id);

            final File localFileObj = Paths.get(localPath, localFileName).toFile();
            final File localPathFile = localFileObj.getParentFile();

            if (!localPathFile.exists()) {
//            logger.warn("Local path {} does not exists", localPathFile.getAbsolutePath());
                throw new ProtocolException("Local path " + localPathFile.getAbsolutePath() + " does not exists");
            }

            if (remotePath == null || "".equals(remotePath)) {
                throw new ProtocolException("Empty remote path is not allowed. Provide a path on remote server to get files from");
            }

            //set the remote and local working directory
            logger.debug("Setting current remote dir to: {}", remotePath);
            try {
                this.channel.cd(remotePath);
            } catch (SftpException e) {
                throw new ProtocolException(e);
            }

            logger.debug("Setting current local dir to: {}", localPath);
            try {
                this.channel.lcd(localPath);
            } catch (SftpException e) {
                throw new ProtocolException(e);
            }

//        logger.debug("Pulling {} from {} to local path {}", remoteFileName, this.session.getHost(), localPath);
            try {
                this.channel.get(remoteFileName, localFileName, progressMonitor);
            } catch (SftpException e) {
                logger.warn("Failed to get file {} from path {} on remote server: {}", remoteFileName, remotePath, e.getMessage());
                throw new ProtocolException(e);
            }
//        logger.debug("{} transferred successfully from {}", remoteFileName, this.session.getHost());

            //TODO: Make this optional
            if (setModifiedTime) {
                //stat the file to get file stats
                logger.debug("Getting stats for file {}", remoteFileName);
                SftpATTRS attributes;
                try {
                    attributes = this.channel.lstat(remoteFileName);
                } catch (SftpException e) {
                    throw new ProtocolException(e);
                }

                logger.debug("Remote modify time for {} is {}", remoteFileName, attributes.getMtimeString());
                ZonedDateTime mTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(attributes.getMTime() * 1000L), ZoneId.systemDefault());
                //lets update the file mTime locally
                logger.debug("Updating mTime of local file {} to {}", localFileName, mTime.toString());
                logger.debug("Number of seconds since EPOCH are: {}", mTime.toInstant().toEpochMilli());
                if (!localFileObj.setLastModified(mTime.toInstant().toEpochMilli())) {
                    logger.warn("Failed to change last modified time for local file {}", localFileObj.getAbsolutePath());
                }
            }

            totalSuccess.incrementAndGet();

            logger.debug("{} :: Success -> {} from {} >> {}", id, remoteFileName, remotePath, localPath);
        } finally {
            totalTime.addAndGet(System.currentTimeMillis() - start);
        }
    }

    @Override
    public void move(String fromPath, String toPath) throws ProtocolException {
        logger.debug("Going to move {} to {}", fromPath, toPath);
        try {
            try {
                if (!fileExists(fromPath)) {
                    logger.warn("Remote file {} does not exists. Unable to move it", fromPath);
                    return;
                }
            } catch (SftpException e) {
                logger.warn("Failed to check if file exists. Will still try to move it", e);
            }
            this.channel.rename(fromPath, toPath);
        } catch (SftpException e) {
            logger.warn("Failed to move file form {} to {}", fromPath, toPath);
            throw new ProtocolException("Failed to move file", e);
        }
    }

    @Override
    public void delete(String remotePath, String remoteFileName) throws ProtocolException {
        logger.debug("Going to delete {} from {}", remoteFileName, remotePath);
        try {
            logger.debug("Change directory to {}", remotePath);
            this.channel.cd(remotePath);

            try {
                if (!fileExists(remoteFileName)) {
                    logger.warn("Remote file {} at path {} does not exists. Unable to delete it", remoteFileName, remotePath);
                    return;
                }
            } catch (SftpException e) {
                logger.warn("Failed to check if file exists. Will still try to remove it", e);
            }

            logger.debug("Trying to remove file {}", remoteFileName);
            this.channel.rm(remoteFileName);

            logger.debug("File removed");
        } catch (SftpException e) {
            logger.warn("Failed to execute delete operation on {}", remoteFileName);
            throw new ProtocolException("Failed to delete file", e);
        }
    }

    /**
     * Get remote file size
     *
     * @param remotePath     Remote file parent directory path
     * @param remoteFileName Remote file name
     * @return File size
     * @throws ProtocolException
     */
    public long size(String remotePath, String remoteFileName) throws ProtocolException {
        logger.debug("Checking length of remote file: {} at {}", remoteFileName, remotePath);

        try {
            logger.debug("Change directory to {}", remotePath);
            this.channel.cd(remotePath);

            logger.debug("Getting file attributes for {}", remoteFileName);
            SftpATTRS stat = this.channel.stat(remoteFileName);
            return stat.getSize();
        } catch (SftpException e) {
            logger.warn("Failed to stat file {}", remoteFileName);
            throw new ProtocolException("Failed to stat file", e);
        }
    }

    /**
     * Get remote file starting from {@code from} position in remote file
     *
     * @param remotePath     Remote file parent dir absolute path
     * @param remoteFileName Remote file name
     * @param dst            Destination file stream
     * @param from           Number of bytes to skip in remote file
     * @return Total bytes that were downloaded
     * @throws ProtocolException If an error occurred while getting file
     */
//    public long get(String remotePath, String remoteFileName, OutputStream dst, long from) throws ProtocolException {
//
//        logger.debug("Streaming file {} skipping first {} bytes", remoteFileName, from);
//        try {
//            logger.debug("Change directory to {}", remotePath);
//            this.channel.cd(remotePath);
//
//            logger.debug("Streaming source to destination from {} bytes", from);
//
//            AppendProgressMonitor appendProgressMonitor = new AppendProgressMonitor(progressMonitor);
//            this.channel.get(remoteFileName, dst, appendProgressMonitor, ChannelSftp.RESUME, from);
//
//            //The function is called getBytesTransferred but it really is
//            //from + actual bytes transferred. The get operation first calls the
//            //SftpProgressMonitor.count(from) and sets current pointer.
//            return appendProgressMonitor.getBytesTransferred() - from;
//        } catch (SftpException e) {
//            logger.warn("Failed to stream file {}", remoteFileName);
//            throw new ProtocolException("Failed to stream file", e);
//        }
//    }

    /**
     * Get remote file starting from {@code from} position in remote file
     *
     * @param remotePath     Remote file parent dir absolute path
     * @param remoteFileName Remote file name
     * @param dst            Delimiter aware output stream
     * @param from           Number of bytes to skip in remote file
     * @throws ProtocolException If an error occurred while getting file
     */
    public void get(String remotePath, String remoteFileName, OutputStream dst, long from) throws ProtocolException {
        logger.debug("Streaming file {} skipping first {} bytes", remoteFileName, from);
        try {
            logger.debug("Change directory to {}", remotePath);
            this.channel.cd(remotePath);

            logger.debug("Streaming source to destination from {} bytes", from);
            logger.debug("Using delimiter aware stream to write until required delimiter");

            this.channel.get(remoteFileName, dst, progressMonitor, ChannelSftp.RESUME, from);

//            return dst.bytesWritten();
        } catch (SftpException e) {
            logger.warn("Failed to stream file {}", remoteFileName);
            throw new ProtocolException("Failed to stream file", e);
        }
    }

    @Override
    public InputStream read() throws ProtocolException {
        throw new ProtocolException("Not supported");
    }

    @Override
    public void write(OutputStream outputStream) throws ProtocolException {
        throw new ProtocolException("Not supported");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    private float downloadThroughput() {
        return this.totalRequests.get() == 0 ? 0f : (float) this.totalTime.get() / (float) this.totalRequests.get();
    }

    @Override
    public int totalDownloadRequests() {
        return this.totalRequests.get();
    }

    @Override
    public long totalDownloadTime() {
        return this.totalTime.get();
    }

    @Override
    public long totalDownloadSize() {
        return this.progressMonitor.getBytesTransferred();
    }

    /**
     * Check if the file matches the pattern. If it does, a {@link FileAttributes} is created containing
     * downloadable path and related attributes
     *
     * @param baseDir Base remote directory path
     * @param entry   {@link com.jcraft.jsch.ChannelSftp.LsEntry} entry instance
     * @param pattern File name pattern to match
     * @return An instance of {@link FileAttributes} if name matches, or null otherwise
     */
    private FileAttributes fileMatch(String baseDir, ChannelSftp.LsEntry entry, Pattern pattern, FileFilter fileFilter) {
        final String remotePath = HistoryFile.getRemotePath(baseDir, entry.getFilename());
        //if it passes initial filtering, check the name
        logger.trace("checking {} file with pattern {}", remotePath, pattern.pattern());
        Matcher remoteFileMatcher = pattern.matcher(entry.getFilename());
        if (remoteFileMatcher.matches()) {
            //if it matches the name
//                        filteredFiles.put(remotePath, new FileAttributes(remoteDir, entry.getFilename(), entry.getAttrs().getMTime(), entry.getAttrs().getSize()));
            logger.trace("{} matches file pattern, checking if it is accepted by filter", remotePath);
            boolean accept = false;

            try {
                accept = fileFilter.accept(remoteFileMatcher, entry.getAttrs());
            } catch (Throwable t) {
                logger.error("File filter threw an error. Cannot filter file correctly", t);
            }

            if (accept) {
            	logger.info("File remote time:: {}", entry.getAttrs().getMTime());
                return new FileAttributes(remotePath, "", entry.getAttrs().getMTime(), remoteFileMatcher);
            } else {
                logger.debug("File {} is not accepted by filter. Discarding it", entry.getFilename());
            }
        } else {
            logger.debug("{} does not file pattern, ignoring it", remotePath);
        }

        return null;
    }

    /**
     * Check if file represented by name exists.
     *
     * @param filename file name in current directory
     * @return True if file exits, false otherwise
     */
    private boolean fileExists(String filename) throws SftpException {
        try {
            this.channel.lstat(filename);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            } else {
                //something else is wrong
                throw e;
            }
        }
    }

    private static class JSCHLogger implements com.jcraft.jsch.Logger {
        private Map<Integer, Level> levels = new HashMap<Integer, Level>();

        private final Logger LOGGER;


        public JSCHLogger() {
            // Mapping between JSch levels and our own levels
            levels.put(DEBUG, Level.DEBUG);
            levels.put(INFO, Level.INFO);
            levels.put(WARN, Level.WARN);
            levels.put(ERROR, Level.ERROR);
            levels.put(FATAL, Level.FATAL);

            LOGGER = LogManager.getLogger(JSCHLogger.class);
        }

        @Override
        public boolean isEnabled(int pLevel) {
//            return LOGGER.isEnabled(levels.getOrDefault(pLevel, Level.DEBUG));
//            return true;
            if (levels.containsKey(pLevel) && levels.get(pLevel) == Level.ERROR) {
                return true;
            } else if (levels.containsKey(pLevel) && levels.get(pLevel) == Level.FATAL) {
                return true;
            }
            return false;
        }

        @Override
        public void log(int pLevel, String pMessage) {
            Level level = levels.get(pLevel);
            if (level == null) {
//                level = LOGGER.getLevel();
                level = Level.ERROR;
            }
            LOGGER.log(level, pMessage); // logging-framework dependent...
        }
    }
}
