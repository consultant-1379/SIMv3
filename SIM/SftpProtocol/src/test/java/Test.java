import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.history.HistoryFile;
import com.ericsson.sim.common.history.model.FileAttributes;
import com.ericsson.sim.common.pool.ConnectionPool;
import com.ericsson.sim.plugins.model.Config;
import com.ericsson.sim.plugins.model.ProtocolException;
import com.ericsson.sim.plugins.model.ServerDetails;
import com.ericsson.sim.sftp.SftpConnection;
import com.ericsson.sim.sftp.SftpServerDetails;
import com.ericsson.sim.sftp.filter.model.DefaultFilter;
import com.ericsson.sim.sftp.processor.AppendPrependProcessor;
import com.ericsson.sim.sftp.processor.ReplacePatternProcessor;
import com.ericsson.sim.sftp.processor.interfaces.FilenameProcessor;
import com.ericsson.sim.sftp.stream.SingleLineOutputStream;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Test {

    public static class Finder
            extends SimpleFileVisitor<Path> {

        private final PathMatcher matcher;
        private int numMatches = 0;

        Finder(String pattern) {
            matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern);
        }

        // Compares the glob pattern against
        // the file or directory name.
        void find(Path file) {
            Path name = file.getFileName();
            if (name != null && matcher.matches(name)) {
                numMatches++;
                System.out.println(file);
            }
        }

        // Prints the total number of
        // matches to standard out.
        void done() {
            System.out.println("Matched: "
                    + numMatches);
        }

        // Invoke the pattern matching
        // method on each file.
        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attrs) {
            find(file);
            return FileVisitResult.CONTINUE;
        }

        // Invoke the pattern matching
        // method on each directory.
        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                                                 BasicFileAttributes attrs) {
            find(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                                               IOException exc) {
            System.err.println(exc);
            return FileVisitResult.CONTINUE;
        }
    }

    private static boolean isRegex(String str) {
        try {
            Pattern.compile(str);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static void listRemoteFiles(ConnectionPool<SftpServerDetails, SftpConnection> pool, String remotePath, String filePattern) {
        Path remotePathFile = Paths.get(remotePath);
        String[] splitPath = StreamSupport.stream(remotePathFile.spliterator(), false).map(Path::toString).toArray(String[]::new);

        for (String path : splitPath) {
            if (isRegex(path)) {

            }
        }
    }

    final static Object mutex = new Object();


    /**
     * public static class DelimiterOutputStream extends OutputStream {
     * <p>
     * private final OutputStream out;
     * private final byte[] finishDelimiter;
     * private final AtomicInteger count = new AtomicInteger(0);
     * <p>
     * //64kb buffer. if we don't get delimiter until this, we will have to write it
     * //to not eat up memory
     * private ByteBuffer byteBuffer = initBuffer();
     * <p>
     * public DelimiterOutputStream(OutputStream out, String finishDelimiter) {
     * this.out = out;
     * this.finishDelimiter = finishDelimiter.getBytes();
     * }
     *
     * @Override public void write(int b) throws IOException {
     * <p>
     * this.write(new byte[b], 0, 1);
     * }
     * @Override public void write(byte[] b) throws IOException {
     * <p>
     * this.write(b, 0, b.length);
     * }
     * @Override public void write(byte[] b, int off, int len) throws IOException {
     * if (b == null) {
     * throw new NullPointerException();
     * } else if ((off < 0) || (off > b.length) || (len < 0) ||
     * ((off + len) > b.length) || ((off + len) < 0)) {
     * throw new IndexOutOfBoundsException();
     * } else if (len == 0) {
     * return;
     * }
     * <p>
     * //write only when delimiter is found.
     * //this will not work if delimiter itself is split in two writes.
     * <p>
     * //if there is no delimiter, we just write everything as it is
     * if (finishDelimiter.length == 0) {
     * this.out.write(b, off, len);
     * count.addAndGet(len);
     * return;
     * }
     * <p>
     * //otherwise, we have to check where we get delimiter in the array
     * //byteBuffer is filled in the check as well
     * int index = lastIndexOf(b, off, len, finishDelimiter);
     * if (index < 0) {
     * //                count.addAndGet(len);
     * return;
     * }
     * <p>
     * final int pointer = index + finishDelimiter.length;
     * this.out.write(byteBuffer.array(), 0, byteBuffer.position() - (len - pointer));
     * count.addAndGet(byteBuffer.position() - (len - pointer));
     * <p>
     * //adjust buffer
     * ByteBuffer newBuffer = initBuffer();
     * newBuffer.put(b, pointer, len - pointer);
     * byteBuffer = newBuffer;
     * }
     * @Override public void close() throws IOException {
     * try {
     * this.out.close();
     * } catch (Exception ignored) {
     * }
     * super.close();
     * <p>
     * count.set(0);
     * <p>
     * //important cast
     * ((Buffer) byteBuffer).clear();
     * }
     * <p>
     * private int getCount() {
     * return count.get();
     * }
     * <p>
     * <p>
     * //      * Returns the start position of the first occurrence of the specified {@code target} within
     * //      * {@code array}, or {@code -1} if there is no such occurrence.
     * //      *
     * //      * <p>More formally, returns the lowest index {@code i} such that {@code Arrays.copyOfRange(array,
     * //      * i, i + target.length)} contains exactly the same elements as {@code target}.
     * //      *
     * //      * @param array  the array to search for the sequence {@code target}
     * //     * @param target the array to search for as a sub-sequence of {@code array}
     * <p>
     * private int lastIndexOf(byte[] array, int off, int len, byte[] target) {
     * //send back the len - pos as current index to make sure we can copy
     * //all buffer to out stream
     * <p>
     * if (target.length == 0) {
     * byteBuffer.put(array, off, len);
     * return len - off;
     * }
     * <p>
     * //while we traverse, we also add data to byteBuffer
     * int lastIndex = -1;
     * <p>
     * int i = off;
     * outer:
     * for (; i < (off + len) - target.length + 1; i++) {
     * for (int j = 0; j < target.length; j++) {
     * byteBuffer.put(array[i + j]);
     * if (array[i + j] != target[j]) {
     * continue outer;
     * }
     * }
     * lastIndex = i;
     * }
     * <p>
     * //copy the remaining byte in case the target has 2 or more size
     * if (lastIndex < 0 && (len - off) > i) {
     * byteBuffer.put(array, i, len - off - i);
     * }
     * <p>
     * return lastIndex;
     * }
     * <p>
     * private static ByteBuffer initBuffer() {
     * return ByteBuffer.allocate(64 * 1024);
     * }
     * }
     */

    static class DelimiterOutputStream extends OutputStream {

        private final OutputStream out;
        private final byte[] finishDelimiter;
        private final AtomicInteger count = new AtomicInteger(0);

        //64kb buffer. if we don't get delimiter until this, we will have to write it
        //to not eat up memory
        private ByteBuffer byteBuffer = initBuffer();

        public DelimiterOutputStream(OutputStream out, String finishDelimiter) {
            this.out = out;
            this.finishDelimiter = finishDelimiter.getBytes();
        }

        @Override
        public void write(int b) throws IOException {

            this.write(new byte[b], 0, 1);
        }

        @Override
        public void write(byte[] b) throws IOException {

            this.write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if ((off < 0) || (off > b.length) || (len < 0) ||
                    ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }

            //write only when delimiter is found.
            //this will not work if delimiter itself is split in two writes.

            //if there is no delimiter, we just write everything as it is
            if (finishDelimiter.length == 0) {
                //TODO: log debug
                this._write(b, off, len);
                return;
            }
            //if the buffer is too small, even smaller than finish delimiter
            else if (finishDelimiter.length >= byteBuffer.capacity()) {
                //TODO: log warning
                this._write(b, off, len);
                return;
            }

            //before we write anything to buffer, check if it will overflow
            //buffer. If so, flush buffer except for last finishDelimiter.length
            //we will write remaining part to buffer again and check for index from there

            //we have something coming in that is larger than the buffer capacity. Flush enough to make room
            if (byteBuffer.capacity() < len) {
                //flush buffer first
                _flush();

                //write enough to make capacity
                this._write(b, off, (len - byteBuffer.capacity()));

                //update offset and len
                off += (len - byteBuffer.capacity());
                len = byteBuffer.capacity();
            }
            //now for cases where capacity is enough but remaining is not
            else if (byteBuffer.remaining() < len) {
                //write excess bytes from buffer
                final int excess = len - byteBuffer.remaining();
                _write(byteBuffer.array(), 0, excess);

                //adjust buffer
                ByteBuffer newBuffer = initBuffer();
                newBuffer.put(byteBuffer.array(), excess, byteBuffer.position() - excess);
                byteBuffer = newBuffer;
            }

            //write everything to byte buffer
            byteBuffer.put(b, off, len);

            //otherwise, we have to check where we get delimiter in the array
            //byteBuffer is filled in the check as well
            int index = lastIndexOf(byteBuffer.array(), 0, byteBuffer.position(), finishDelimiter);
            //we could not find index, move to next part
            if (index < 0) {
                return;
            }

            final int pointer = index + finishDelimiter.length;
            this._write(byteBuffer.array(), 0, pointer);

            //adjust buffer
            ByteBuffer newBuffer = initBuffer();
            newBuffer.put(byteBuffer.array(), pointer, byteBuffer.position() - pointer);
            byteBuffer = newBuffer;
        }

        private void _write(byte[] b, int off, int len) throws IOException {
            this.out.write(b, off, len);
            this.count.addAndGet(len);
        }

        //not to be called by external/outer streams
        private void _flush() throws IOException {
            this._write(byteBuffer.array(), 0, byteBuffer.position());
            byteBuffer.clear();
        }

        @Override
        public void close() throws IOException {
            try {
                this.out.close();
            } catch (Exception ignored) {
            }
            super.close();

            count.set(0);

            //important cast
            ((Buffer) byteBuffer).clear();
        }

        public int getCount() {
            return count.get();
        }

        private int lastIndexOf(byte[] array, int off, int len, byte[] target) {

            //while we traverse, we also add data to byteBuffer
            int lastIndex = -1;

            outer:
            for (int i = off; i < (off + len) - target.length + 1; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (array[i + j] != target[j]) {
                        continue outer;
                    }
                }
                lastIndex = i;
            }

            return lastIndex;
        }

        private static ByteBuffer initBuffer() {
            return ByteBuffer.allocate(100);
        }

        public String dumpRemaining() {
            return new String(byteBuffer.array(), 0, byteBuffer.position());
        }
    }

    static class AppendProgress implements SftpProgressMonitor {

        private final AtomicLong progress = new AtomicLong();

        @Override
        public void init(int op, String src, String dest, long max) {
            System.out.println("AppendProgress.init() op=" + op + ", src=" + src + ", dest=" + dest + ", max=" + max);
            progress.set(0);
        }

        @Override
        public boolean count(long count) {
            System.out.println("AppendProgress.read(): " + count);
            progress.addAndGet(count);
            return true;
        }

        @Override
        public void end() {
            System.out.println("AppendProgress.end()");
        }

        public long read() {
            return progress.get();
        }
    }

    private static int writeString(OutputStream stream, String str) throws IOException {
        byte[] bytes = str.getBytes();
        stream.write(bytes, 0, bytes.length);
        return bytes.length;
    }

    private static int writeString(OutputStream stream, String str, int off) throws IOException {
        byte[] bytes = str.getBytes();
        stream.write(bytes, off, bytes.length - off);
        return bytes.length - off;
    }

    public static void main(String[] args) throws Exception {

        Config configuration = new Config() {
            HashMap<String, Object> config = new HashMap<>();

            @Override
            public String getNodeName() {
                return "test";
            }

            @Override
            public void addProperty(String name, Object value) {
                config.put(name, value);
            }

            @Override
            public boolean hasProperty(String name) {
                return config.containsKey(name);
            }

            @Override
            public String getPropertyAsString(String name) {
                return (String) config.get(name);
            }

            @Override
            public String getPropertyAsString(String name, String defaultValue) {
                return (String) config.getOrDefault(name, defaultValue);
            }

            @Override
            public int getPropertyAsInteger(String name) {
                return (int) config.get(name);
            }

            @Override
            public int getPropertyAsInteger(String name, int defaultValue) {
                return (int) config.getOrDefault(name, 0);
            }

            @Override
            public boolean getPropertyAsBoolean(String name) {
                return (boolean) config.get(name);
            }

            @Override
            public boolean getPropertyAsBoolean(String name, boolean defaultValue) {
                return (boolean) config.getOrDefault(name, defaultValue);
            }

            @Override
            public void clear() {
                config.clear();
            }
        };

        configuration.addProperty(Constants.CONFIG_NODE_NAME, "test");
        configuration.addProperty(Constants.CONFIG_IPADDRESS, "192.168.0.1");


        List<FilenameProcessor> filenameProcessors = new ArrayList<>(3);

//        filenameProcessors.add(new RegexProcessor());
//        filenameProcessors.add(new AppendPrependProcessor("test", false, false));
        filenameProcessors.add(new ReplacePatternProcessor(configuration, "test",
                Constants.PATTERN_REPLACE_NODENAME, Constants.PATTERN_REPLACE_IPADDRESS, Constants.PATTERN_REPLACE_HASHCODE,
                Constants.PATTERN_REPLACE_UNIQUE_ID, Constants.PATTERN_REPLACE_REMOTE_FILE_NAME, Constants.PATTERN_REPLACE_REMOTE_FILE_NAME_NO_EXT,
                Constants.PATTERN_REPLACE_ROP_START_DATETIME, Constants.PATTERN_REPLACE_ROP_END_DATETIME,
                Constants.PATTERN_REPLACE_CSV_ROP_START_DATETIME, Constants.PATTERN_REPLACE_CSV_ROP_END_DATETIME));

        String renamePattern = "%IP_ADDRESS%-%HASH_CODE%_service-%R_FILE_NAME%";
        String filename = "B202201050715+0100-202201050730+0100.as1";

        for (FilenameProcessor filenameProcessor : filenameProcessors) {
            renamePattern = filenameProcessor.process(renamePattern, filename, null);
        }

        System.out.println(renamePattern);

        System.exit(0);
//
//        System.out.println(ReplacePatternProcessor.removeExt("meters_202218.log"));
//        System.out.println(ReplacePatternProcessor.removeExt(""));
//        System.out.println(ReplacePatternProcessor.removeExt("meters_202218"));
//        System.out.println(ReplacePatternProcessor.removeExt(".meters_202218"));
//        System.out.println(ReplacePatternProcessor.removeExt(".m.eters_202218"));
//        System.out.println(ReplacePatternProcessor.removeExt(".m.e.t.e.r.s._.2..0.2.2.1.8.log"));
//        System.out.println(ReplacePatternProcessor.removeExt(".m.e.t.e.r.s._.2..0.2.2.1.8.log."));
//
//        System.exit(0);
//
//        String org = "in this file%HASHCODE% we have";
//        String rst = HelpUtil.replaceAll(org, "%HASHCODE", "" + HelpUtil.timeHashCode());
//        System.out.println(rst);
//
//        assert org.equals(rst);

//        byte[] test = {1, 2, 3, 4, 5, 6, 7, 8, 9};
//
//
//        int len = 5;
//        int off = 1;
//
//        byte[] copiedTest = Arrays.copyOfRange(test, off, off + len);
//
//
//        System.exit(0);
//
//        Pattern remotePatthen = Pattern.compile("(.*)-(.*)\\.(.*)");
//        final String remoteFilename = "A2021-2022.txt";
//        final String localRepalcePattern = "%IP_ADDRESS%$2_local_$1.$3";
//
//        Matcher matcher = remotePatthen.matcher(remoteFilename);
//        if (matcher.matches()) {
//            System.out.println("replace: " + matcher.replaceFirst(localRepalcePattern));
//        }
//

        SingleLineOutputStream csvOutputStream = new SingleLineOutputStream(System.out, "\n");
        csvOutputStream.write("1,2,3\n4,5,6\n7,8,9\n10,11,12\n1001,1002,100006\n".getBytes());
        csvOutputStream.write(("Left in stream: '" + csvOutputStream.dumpRemaining() + "'\n").getBytes());
        System.out.println("Now left: " + csvOutputStream.dumpRemaining());
//        csvOutputStream.close();

        int count = 0;

//        ByteOutputStream bous = new ByteOutputStream();
        DelimiterOutputStream delimiterOutputStream = new DelimiterOutputStream(System.out, "\n");
        StringBuilder actualString = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            count += writeString(delimiterOutputStream, "[");
            actualString.append("[");
            StringBuilder str = new StringBuilder();
            for (int j = 0; j < i + 1; j++) {
                str.append(",").append(j);
            }
            str.append("]\n");
            count += writeString(delimiterOutputStream, str.toString(), 1);
            actualString.append(str.substring(1));
        }

//        String saved = new String(bous.getBytes());
//        assert saved.equals(actualString.toString());

        System.out.println("\nactual count:" + count);
        System.out.println("stream count:" + delimiterOutputStream.getCount());
        System.out.println("left in stream: \"" + delimiterOutputStream.dumpRemaining() + "\"");
//        System.out.println("stream string: " + saved);
        System.out.println("actual string: " + actualString.toString());
//
//        bytesbytes = "2022-06-22-0001".getBytes();
//        delimiterOutputStream.write(bytesbytes, 0, bytesbytes.length);
//
//        bytesbytes = "2022-06-22-0001".getBytes();
//        delimiterOutputStream.write(bytesbytes, 0, bytesbytes.length);
//
//        bytesbytes = "2022-06-22-0001".getBytes();
//        delimiterOutputStream.write(bytesbytes, 0, bytesbytes.length);

//        delimiterOutputStream.write(bytesbytes, 3, 5);
//        delimiterOutputStream.write("\n".getBytes());
//        delimiterOutputStream.write("\n".getBytes());
//        bytesbytes = "MORE INFO\nNEXT LINE\nMORE".getBytes();
//        delimiterOutputStream.write(bytesbytes);
//        bytesbytes = "\n".getBytes();
//        delimiterOutputStream.write(bytesbytes);
//        delimiterOutputStream.write((delimiterOutputStream.count + "\n").getBytes());

        System.exit(0);

        SftpServerDetails serverDetails = new SftpServerDetails("132.196.219.200", 22, "eatamal", "eatamal", 1);
        SftpConnection connection = new SftpConnection();
        connection.connect(serverDetails);
        System.out.println("Connected");

        String remotePath = "/etc/ericsson/enm/tmp";
        String remoteFileName = "file.stat";


        long size = connection.size(remotePath, remoteFileName);
//        AppendProgress progress = new AppendProgress();
        long progress = 0;
//        DelimiterOutputStream countOutputStream = new DelimiterOutputStream(System.out, "\n");
//
//        while (size > progress) {
////            System.out.println("main(), size=" + size + ", CountOutputStream.read=" + countOutputStream.getCount() + ", AppendProgress.read=" + progress.read());
////            System.out.println("main(), size=" + size + ", AppendProgress.read=" + progress.read());
//            connection.get(remotePath, remoteFileName, countOutputStream, progress);
//
////            System.out.println("main() CountOutputStream.read=" + countOutputStream.getCount() + ", AppendProgress.read=" + progress.read());
////            System.out.println("main() AppendProgress.read=" + progress.read());
//
//            synchronized (mutex) {
//                mutex.wait(60000);
//            }
//
//            size = connection.size(remotePath, remoteFileName);
//        }

        connection.disconnect();
        System.out.println("Disconnected");
        System.exit(0);

//        Stream<Map.Entry<String, FileAttributes>> streamfiles = getRemoteFiles("/etc/ericsson/enm/Test/simtest/teststructure/pmneexport/neexport_*", ".*", ".*.xml.gz");
        Stream<Map.Entry<String, FileAttributes>> streamfiles = getRemoteFiles("/etc/ericsson/enm/Test/simtest/teststructure/pmneexport/neexport_20220220/", "[A-Za-z0-9]+", ".*.xml.gz");
        streamfiles.forEach(e -> System.out.println(e.getKey() + " = " + e.getValue()));
        System.exit(0);

        String path = "/var/performance/dir{1-50}";

        System.exit(0);

        AppendPrependProcessor appendPrependProcessor = new AppendPrependProcessor("TMA_1", false, true);
        System.out.println(appendPrependProcessor.process("B202201050715+0100-202201050730+0100.as1", "", null));

        System.exit(0);

        long time = 3686440;

        System.out.println(new SimpleDateFormat("mm:ss.SSS").format(new Date(time)));
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
        Duration duration = Duration.ofMillis(time);


        System.exit(0);

        System.out.println("dir? " + result(true, FilterType.DIR));
        System.out.println("dir? " + result(true, FilterType.FILE));
        System.out.println("dir? " + result(true, FilterType.DIR_OR_FILE));
    }

    private HashMap<String, FileAttributes> cleanupHistory(LinkedHashMap<String, FileAttributes> remoteFiles, HistoryFile historyFile) {

        HashMap<String, FileAttributes> cleanedUp = new HashMap<>();

//        int cleaned = 0;
        List<String> filesInHistory = historyFile.getFileNames();
        if (filesInHistory != null) {
            for (String remotePath : filesInHistory) {
                if (!remoteFiles.containsKey(remotePath)) {
                    //remote from history

                    cleanedUp.put(remotePath, historyFile.getFile(remotePath));
                    historyFile.removeFile(remotePath);
//                    cleaned++;
                }
            }
        }

        return cleanedUp;
    }

    private static Stream<Map.Entry<String, FileAttributes>> getRemoteFiles(final String remotePath, final String parentDirPattern, final String fileNamePattern) throws Exception {
        long opTime = 0;
        if (parentDirPattern == null) {

            opTime = System.currentTimeMillis();
            try {
                LinkedHashMap<String, FileAttributes> discoveredFiles = getRemoteFiles(remotePath, fileNamePattern, SftpConnection.FilterType.FILE);
                return discoveredFiles.entrySet().stream();
            } finally {

            }
        } else {

            opTime = System.currentTimeMillis();
            LinkedHashMap<String, FileAttributes> parentDirMap = getRemoteFiles(remotePath, parentDirPattern, SftpConnection.FilterType.DIR);

            if (parentDirMap.size() == 0) {

                return Stream.empty();
            }


            return parentDirMap.entrySet().stream().parallel()
                    .flatMap(f -> {
                        final FileAttributes value = f.getValue();
                        final String path = value.getRemoteParentDir().endsWith("/") ? value.getRemoteParentDir() + value.getRemoteFilename() : value.getRemoteParentDir() + "/" + value.getRemoteFilename();
                        try {
                            return getRemoteFiles(path, fileNamePattern, SftpConnection.FilterType.FILE).entrySet().stream();
                        } catch (Exception e) {

                            return Stream.empty();
                        }
                    });
        }
    }

    private static LinkedHashMap<String, FileAttributes> getRemoteFiles(final String remotePath, final String fileNamePattern, SftpConnection.FilterType filterOn) throws Exception {


        return listRemoteFiles(remotePath, fileNamePattern, filterOn);


    }

    public static LinkedHashMap<String, FileAttributes> listRemoteFiles(final String remoteDir, final String fileNamePattern, final SftpConnection.FilterType filterOn) throws ProtocolException {
        ServerDetails serverDetails = new SftpServerDetails("132.196.219.200", 22, "eatamal", "eatamal", 1);
        SftpConnection connection = new SftpConnection();
        connection.connect(serverDetails);
        return connection.listRemoteFiles(remoteDir, fileNamePattern, filterOn, new DefaultFilter());
    }

    public static enum FilterType {
        DIR,
        FILE,
        DIR_OR_FILE
    }

    private static boolean result(boolean isDir, FilterType filterOn) {
        return (filterOn == FilterType.DIR) == isDir;
    }

//    static final int threads = 100;
//    static ConcurrentHashMap<String, List<String>> organizer = new ConcurrentHashMap<>(threads);
//
//    static class RetryExecutor extends ThreadPoolExecutor {
//
//        private final CountDownLatch latch;
//
//        public RetryExecutor(int corePoolSize, int maximumPoolSize, int totalTasks) {
//            this(corePoolSize, maximumPoolSize, totalTasks, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
//        }
//
//        public RetryExecutor(int corePoolSize, int maximumPoolSize, int totalTasks, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
//            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
//            latch = new CountDownLatch(totalTasks);
//        }
//
//        @Override
//        protected void afterExecute(Runnable r, Throwable t) {
//            super.afterExecute(r, t);
//            if (r instanceof Worker) {
//                Worker worker = (Worker) r;
//                switch (worker.getState()) {
//                    case RUNNING:
////                        print(worker.getWorkerId(), "Invalid state. Ignoring");
//                        break;
//                    case COMPLETED:
//                        latch.countDown();
//                        break;
//                    case NO_RETRIES_LEFT:
//                        print(worker.getWorkerId(), worker.getError() != null ? worker.getError().getMessage() : "null");
//                        latch.countDown();
//                        break;
//                    case EXECUTION_FAILURE:
//                        print(worker.getWorkerId(), worker.getError() != null ? worker.getError().getMessage() : "null");
//                        print(worker.getWorkerId(), "Doing retry...");
//                        this.execute(worker);
//                }
//            } else {
//                //TODO: Does not support retries, so we just return
//                print("Task is not of type Worker. Cannot do retries. Type is: " + r.getClass().getName());
//                latch.countDown();
//            }
//        }
//
//        public void await() throws InterruptedException {
//            latch.await();
//        }
//
//        @Override
//        public String toString() {
//            return super.toString();
//        }
//    }
//
//    static abstract class Worker implements Runnable {
//
//        public enum ExecutionState {
//            COMPLETED,
//            RUNNING,
//            NO_RETRIES_LEFT,
//            EXECUTION_FAILURE
//        }
//
//        protected ExecutionState state;
//        protected Throwable error = null;
//        private int attempt = 0;
//
//        @Override
//        public final void run() {
//            this.state = ExecutionState.RUNNING;
//            if (!canRetry()) {
//                setFailure(ExecutionState.NO_RETRIES_LEFT, new RuntimeException("No more retries left. Attempted " + attempt + " of total " + totalRetries() + " retries"));
//                return;
//            }
//            attempt++;
//            try {
//                runEx();
//                setSuccess();
//            } catch (Throwable e) {
//                setFailure(ExecutionState.EXECUTION_FAILURE, e);
//            }
//        }
//
//        protected abstract void runEx() throws Exception;
//
//        public final ExecutionState getState() {
//            return state;
//        }
//
//        public final Throwable getError() {
//            return error;
//        }
//
//        public boolean canRetry() {
//            return attempt <= totalRetries();
//        }
//
//        public final int remainingAttempts() {
//            return attempt;
//        }
//
//        public abstract int totalRetries();
//
//        public abstract String getWorkerId();
//
//        private void setSuccess() {
//            this.state = ExecutionState.COMPLETED;
//            this.error = null;
//        }
//
//        private void setFailure(ExecutionState errorCode, Throwable error) {
//            this.state = errorCode;
//            this.error = error;
//        }
//
//        @Override
//        public boolean equals(Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//            Worker worker = (Worker) o;
//            return getWorkerId() != null && getWorkerId().equals(worker.getWorkerId());
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(getWorkerId());
//        }
//
//        @Override
//        public String toString() {
//            return "Worker{" +
//                    "state=" + state.name() +
//                    ", error=" + (error != null ? error.getMessage() : "null") +
//                    ", attempt=" + attempt +
//                    '}';
//        }
//    }
//
//    static class SftpWorker extends Worker {
//
//
//        private final SftpConnectionPool pool;
//        private final ServerDetail serverDetail;
//        private final String workerId;
//        private final int retries;
//
//        private SftpWorker(SftpConnectionPool pool, ServerDetail serverDetail, int retries, String workerId) {
//            this.pool = pool;
//            this.serverDetail = serverDetail;
//            this.workerId = workerId;
//            this.retries = retries;
//        }
////
////        public static synchronized SftpWorker getNewWorker(SftpConnectionPool pool, ServerDetail serverDetail, int retries, String workerId) {
////            print("Creating new worker with id: " + workerId);
////            return new SftpWorker(pool, serverDetail, retries, workerId);
////        }
//
//        @Override
//        protected void runEx() throws Exception {
//            SftpConnection connection = null;
//
//            try {
//                connection = pool.getPool().borrowObject(serverDetail);
//
//                if (connection == null) {
//                    throw new RuntimeException("Failed to get connection");
//                } else {
//                    connection.work();
//                }
//            } finally {
//                pool.getPool().returnObject(serverDetail, connection);
//            }
//        }
//
//        @Override
//        public int totalRetries() {
//            return retries;
//        }
//
//        public String getWorkerId() {
//            return workerId;
//        }
//    }
//
//    public static void main(String[] args) throws Exception {
//
//        final ServerDetail serverDetail = new ServerDetail("host", 22, "user", "password", 1);
//        final RetryExecutor service = new RetryExecutor(1, 100, threads);
//        final SftpConnectionPool pool = new SftpConnectionPool(5, 1);
//
//        for (int i = 0; i < threads; i++) {
//            SftpWorker worker = new SftpWorker(pool, serverDetail, 0, "worker-" + i);
//            service.execute(worker);
//        }
//
//
////        boolean result = !service.awaitTermination(5, TimeUnit.SECONDS);
////        if (!result) {
////            print("Some thread may not have exited cleanly");
////        }
//        service.await();
//        service.shutdown();
//        print("Open connections: " + pool.getPool().getNumActive() + pool.getPool().getNumIdle());
//        pool.close();
//        dump();
//        print("Ending!");
////        System.exit(0);
//    }
//
//    static void print(String message) {
////        System.out.println(Thread.currentThread().getName() + " :: " + message);
//        print(Thread.currentThread().getName(), message);
////        print("", message);
//    }
//
//    static synchronized void print(String id, String message) {
////        synchronized (Thread.currentThread()) {
//        id = Thread.currentThread().getName() + "->" + id;
////            List<String> messages = organizer.getOrDefault(id, new ArrayList<>());
////            messages.add(message);
////            organizer.put(id, messages);
//        System.out.println(id + " :: " + message);
////        }
//    }
//
//    static void dump() {
//        if (organizer.size() == 0) {
//            System.out.println("Nothing to print");
//            return;
//        }
//        for (Map.Entry<String, List<String>> messages : organizer.entrySet()) {
//            for (String message : messages.getValue()) {
//                System.out.println(messages.getKey() + " :: " + message);
//            }
//        }
//    }
//
//    public static class SftpSessionFactory extends BaseKeyedPoolableObjectFactory<ServerDetail, SftpConnection> {
//
//
//        @Override
//        public SftpConnection makeObject(ServerDetail serverDetail) throws Exception {
//            SftpConnection connection = new SftpConnection();
//            connection.connect();
//            if (!connection.isConnected()) {
//                throw new Exception("Failed to create connection");
//            }
//
//            return connection;
//        }
//
//        @Override
//        public void destroyObject(ServerDetail key, SftpConnection connection) throws Exception {
//            if (connection != null) {
////                print(connection.id, "Disconnecting...");
//                connection.disconnect();
//            }
//        }
//
//        @Override
//        public void activateObject(ServerDetail key, SftpConnection connection) throws Exception {
//            if (!connection.isConnected()) {
////                print(connection.id, "Connecting...");
//                connection.connect();
//            } else {
////                print(connection.id, "Getting...");
//                //connection.isConnected();
//            }
//        }
//
//        @Override
//        public boolean validateObject(ServerDetail key, SftpConnection connection) {
//            return connection.isConnected();
//        }
//    }
//
//    public static class ServerDetail {
//        final String host;
//        final int port;
//        final String username;
//        final String password;
//        public final int retries;
//
//        public ServerDetail(String host, String username, String password) {
//            this(host, 22, username, password, 0);
//        }
//
//        public ServerDetail(String host, int port, String username, String password, int retries) {
//            this.host = host;
//            this.port = port;
//            this.username = username;
//            this.password = password;
//            this.retries = retries;
//        }
//
//        @Override
//        public boolean equals(Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//            ServerDetail that = (ServerDetail) o;
//            return port == that.port && host.equals(that.host) && username.equals(that.username);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hash(host, port, username);
//        }
//
//        @Override
//        public String toString() {
//            return "ServerDetail{" +
//                    "host='" + host + '\'' +
//                    ", port=" + port +
//                    ", username='" + username + '\'' +
//                    ", retries=" + retries +
//                    '}';
//        }
//    }
//
//    public static class SftpConnection {
//        private volatile boolean connected = false;
//        private static final AtomicInteger step = new AtomicInteger(0);
//        private static final AtomicInteger active = new AtomicInteger(0);
//        public final int number;
//        public final String id;
//
//        SftpConnection() {
//            number = step.incrementAndGet();
//            id = "connection-" + number;
//        }
//
//        public void connect() throws JSchException {
//            if (active.incrementAndGet() >= 5) {
//                active.decrementAndGet();
//                throw new JSchException("Failed to connect: " + number);
//            }
//            print("Connected for : " + number);
//            connected = true;
////            try {
////                Thread.sleep(1000);
////            } catch (InterruptedException e) {
////                e.printStackTrace();
////            }
////            print(id, "Connected");
//        }
//
//        public void disconnect() {
//            connected = false;
//            active.decrementAndGet();
//            print(id, "Disconnected");
//        }
//
//        public boolean isConnected() {
////            print(id, "Connected? " + connected);
//            return connected;
//        }
//
//        public void work() throws InterruptedException {
//            Thread.sleep(500);
//            print(id, "Work COMPLETED!");
//        }
//    }
//
//    public static class SftpConnectionPool implements Closeable {
//        private final GenericKeyedObjectPool<ServerDetail, SftpConnection> pool;
//
////    private static class SingletonHolder {
////        public static final SftpConnectionPool INSTANCE = new SftpConnectionPool();
////    }
//
////    public static SftpConnectionPool getInstance() {
////        return SingletonHolder.INSTANCE;
////    }
//
//        private final int maxActive;
//        private final int initIdle;
//
//        public SftpConnectionPool(int maxActive, int initIdle) {
//
//            this.maxActive = maxActive;
//            this.initIdle = initIdle;
//
//            pool = new SftpKeyedObjectPool(new SftpSessionFactory());//new GenericKeyedObjectPool<>(new SftpSessionFactory());
//            pool.setMaxActive(maxActive);
//            pool.setMinIdle(initIdle);
//            pool.setMaxTotal(maxActive);
//            pool.setTestOnBorrow(true);
//            //when we reach max sessions, we block
//            pool.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_BLOCK);
//        }
//
//        public KeyedObjectPool<ServerDetail, SftpConnection> getPool() {
//            return pool;
//        }
//
//        @Override
//        public void close() {
//            try {
//                print("Closing pool");
//                pool.clear();
//                pool.close();
//            } catch (Exception e) {
//                print(e.getMessage());
//            }
//        }
//
//        @Override
//        public String toString() {
//            return "SftpConnectionPool{" +
//                    "maxActive=" + maxActive +
//                    ", initIdle=" + initIdle +
//                    '}';
//        }
//    }
//
//    public static class SftpKeyedObjectPool extends GenericKeyedObjectPool<ServerDetail, SftpConnection> {
//
//        private final ReentrantLock lock = new ReentrantLock();
//
//        public SftpKeyedObjectPool(SftpSessionFactory sftpSessionFactory) {
//            super(sftpSessionFactory);
//        }
//
//        @Override
//        public SftpConnection borrowObject(ServerDetail key) throws Exception {
//            try {
//                return super.borrowObject(key);
//            } catch (NoSuchElementException | IllegalStateException e) {
//                //pool exhausted. should not happen as we are using block on exhaust strategy
//                // OR
//                // Timeout waiting for idle object
//                // pool closed
//                throw e;
//            } catch (Exception e) {
//                return shrinkPool(key, e);
////                return null;
////                throw e;
//            }
//        }
//
//        private SftpConnection shrinkPool(ServerDetail key, Exception reason) throws Exception {
//            synchronized (lock) {
//                //keep trying until we can borrow from existing pool rather then try to
//                //create a new one
//                while (true) {
//                    try {
//                        //try one more time, maybe the pool has been reduced before.
//                        return super.borrowObject(key);
//                    } catch (NoSuchElementException | IllegalStateException e) {
//                        //pool exhausted. should not happen as we are using block on exhaust strategy
//                        // OR
//                        // Timeout waiting for idle object
//                        // pool closed
//                        throw e;
//                    } catch (Exception e) {
//                        //we may have to reduce the pool
//                        int total = this.getMaxTotal();
//                        if (total <= 1) {
//                            print("Cannot shrink further, pool size: " + total);
//                            throw e;
//                        } else {
//                            print("Shrinking pool from " + total + " to " + (total - 1) + " because " + e.getMessage());
//                            this.setMaxTotal(total - 1);
//                            this.setMaxActive(total - 1);
//                        }
//                    }
//                }
//            }
////            synchronized (lock) {
////                int current = this.getNumActive() + this.getNumIdle();
////                int total = this.getMaxTotal();
////                print("total: " + total + ", current: " + current);
////                if (total <= 1) {
////                    print("Cannot shrink further, pool size: " + total);
////                    throw reason;
////                } else if (total == current) {
////                    //we have already fixed the size, no need to do it again
////                    print("Pool size already fixed. Will not change it");
////                    return;
////                }
////                int newSize = total - current - 1;
////                if (newSize <= 0) {
////                    newSize = 1;
////                }
////                print("Shrinking pool from " + total + " to " + newSize + " because " + (reason.getMessage() != null ? reason.getMessage() : "null"));
////                this.setMaxTotal(newSize);
//
//
////            synchronized (lock) {
////                try {
////                    //try to get it again after getting lock. it maybe that previous thread has
////                    //already shrunk the pool and now we can get an object
////                    return this.borrowObject(key);
////                } catch (NoSuchElementException | IllegalStateException e) {
////                    //pool exhausted. should not happen as we are using block on exhaust strategy
////                    // OR
////                    // Timeout waiting for idle object
////                    // pool closed
////                    throw e;
////                } catch (Exception e) {
////                    //if there is exception, it means we will have to shrink the pool further
////                    int total = this.getMaxTotal();
////                    if (total <= 1) {
////                        print("Cannot shrink further, pool size: " + total);
////                        throw e;
////                    } else {
////                        print("Shrinking pool from " + total + " to " + (total - 1) + " because " + (reason.getMessage() != null ? reason.getMessage() : "null"));
////                        this.setMaxTotal(total - 1);
////                    }
////                }
////            }
//
////            }
//
//
////            if (lock.tryLock()) {
////                try {
////                    //reduce pool size
////                    int total = this.getMaxTotal();
////                    if (total <= 1) {
////                        print("Cannot shrink further, pool size: " + total);
////                    } else {
////                        print("Shrinking pool from " + total + " to " + (total - 1) + " because " + (reason.getMessage() != null ? reason.getMessage() : "null"));
////                        this.setMaxTotal(total - 1);
////                    }
////                } finally {
////                    lock.unlock();
////                }
////            } else {
////                print("Already shrinking...");
////            }
//        }
//    }
}
