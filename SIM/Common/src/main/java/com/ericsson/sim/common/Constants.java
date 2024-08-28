package com.ericsson.sim.common;

import java.nio.file.Paths;

public interface Constants {
    String APP_NAME = "SIM";
    String PRIMARY_TYPE = "Node";

    /**
     * CSV separator regex with escape character protection
     */
    String CSV_DEFAULT_SEPARATOR = "(?<!\\\\),";
    String CSV_COMMENT = "#";

    String HEADER_PLUGIN_NAMES = "pluginNames";

    String CONFIG_NODE_NAME = "name";

    //Cron keys
    String CONFIG_CRON_ROP = "rop";
    String CONFIG_CRON_OFFSET = "Offset";
    String CONFIG_CRON_BASED_ROP = "cronBasedRop";

    String QJOB_EXECUTOR = "quartzJobExecutor";
    String QJOB_RUNTIMECONFIG = "quartzJobRuntimeConfig";
    String QJOB_HEALTH_CHECK = "quartzHealthCheckService";
    String QJOB_HISTORYDIR = "quartzHistoryDir";
    String QJOB_PRIVATEKEY = "quartzPrivateKey";
    String QJOB_EXEC_INSTRUMENTATION = "quartzExecutionInstrumentation";
    String QJOB_BATCH_SIZE = "quartzBatchSize";
    String QJOB_WINDDOWN_TIME = "quartzWinddownTime";
    String QJOB_RP_NODENAME = "quartzReplacePatternNodeName";
    String QJOB_RP_IPADDRESS = "quartzReplacePatternIpaddress";
    String QJOB_RP_HASHCODE = "quartzReplacePatternHashcode";
    String QJOB_RP_REMOTE_FILE_NAME = "quartzReplacePatternRemoteFilename";
    String QJOB_RP_REMOTE_FILE_NAME_NO_EXT = "quartzReplacePatternRemoteFilenameNoExt";
    String QJOB_RP_UNIQUE_ID = "quartzReplacePatternUniqueId";
    String QJOB_RP_ROP_START = "quartzReplacePatternRopStart";
    String QJOB_RP_ROP_END = "quartzReplacePatternRopEnd";

    //Server details
    String CONFIG_IPADDRESS = "IPAddress";
    String CONFIG_PORT = "sftpPort";
    String CONFIG_USER = "sftpUserName";
    String CONFIG_PASS = "sftpUserPass";
    String CONFIG_KEYFILE = "sftpKeyFile";
    String CONFIG_RETRIES = "sftpRetries";
    String CONFIG_MAX_SFTP_CONNECTION = "maxSftpConnections";
    String CONFIG_REMOTE_DIR = "remoteDirectory";
    String CONFIG_LOCAL_DIR = "destinationDirectory";
    String CONFIG_REMOTE_FILE_REGEX = "namePattern";
    String CONFIG_IGNORE_HISTORY = "ignoreHistory";
    String CONFIG_FILE_RENAME_PATTERN = "renamePattern";

    String PATTERN_REPLACE_NODENAME = "%NODE_NAME%";
    String PATTERN_REPLACE_IPADDRESS = "%IP_ADDRESS%";
    String PATTERN_REPLACE_HASHCODE = "%HASH_CODE%";
    String PATTERN_REPLACE_REMOTE_FILE_NAME = "%R_FILE_NAME%";
    String PATTERN_REPLACE_REMOTE_FILE_NAME_NO_EXT = "%R_FILE_NAME_NO_EXT%";
    String PATTERN_REPLACE_UNIQUE_ID = "%UNIQUE_ID%";
    /**
     * This is local ROP start time when job started
     */
    String PATTERN_REPLACE_ROP_START_DATETIME = "%ROP_START_DATETIME%";
    /**
     * This is local ROP end time calculated by adding ROP time to end time
     */
    String PATTERN_REPLACE_ROP_END_DATETIME = "%ROP_END_DATETIME%";
    /**
     * This is local ROP start time when job start used for CSV files
     */
    String PATTERN_REPLACE_CSV_ROP_START_DATETIME = "%CSV_ROP_START_DATETIME%";
    /**
     * This is local ROP end time when job start used for CSV files
     */
    String PATTERN_REPLACE_CSV_ROP_END_DATETIME = "%CSV_ROP_END_DATETIME%";

    String PATTERN_REPLACE_ROP_START_DATETIME_FORMAT = "yyyyMMdd-HHmm";
    String PATTERN_REPLACE_ROP_END_DATETIME_FORMAT = "yyyyMMdd-HHmm";

    int THREADPOOL_MIN_SIZE = 1;
    int THREADPOOL_MAX_SIZE = 100;
    long THREADPOOL_IDLE_SEC = 900;
    int THREADPOOL_AWAIT_TERMINATION = 15;

    String RMI_SERVER_NAME = APP_NAME + "_RMI";
    int RMI_REGISTRY_PORT = 1099;


    int BATCH_SIZE = 1000;
    int MARGIN_TO_NEXT_EXECUTION = 45;

    //--- Append constants --- //
    int APPEND_RETRIES = 3;

    class PATHS {
        public static final String userHome = System.getProperty("user.home");
        public static final String currentDir = System.getProperty("user.dir");
        public static final String appHome = System.getProperty("app.home");

        public static final String APP_CONFIG = Paths.get(appHome, "etc", "config.json").toString();
        public static final String NE_CONFIG = Paths.get(appHome, "etc", "nodes.config").toString();
        public static final String NODETYPES = Paths.get(appHome, "nodetypes").toString();
        public static final String HISTORY_DB = Paths.get(appHome, "history").toString();
        public static final String POLICY_CONFIG = Paths.get(appHome, "etc", "ehc.json").toString();
    }
}
