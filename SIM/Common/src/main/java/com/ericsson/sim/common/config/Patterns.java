package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

public class Patterns {
    String nodeName = Constants.PATTERN_REPLACE_NODENAME;
    String ipAddress = Constants.PATTERN_REPLACE_IPADDRESS;
    String remoteFilename = Constants.PATTERN_REPLACE_REMOTE_FILE_NAME;
    String remoteFilenameNoExt = Constants.PATTERN_REPLACE_REMOTE_FILE_NAME_NO_EXT;
    String uniqueId = Constants.PATTERN_REPLACE_UNIQUE_ID;
    String hash = Constants.PATTERN_REPLACE_HASHCODE;
    String ropStartDatetime = Constants.PATTERN_REPLACE_ROP_START_DATETIME;
    String ropEndDatetime = Constants.PATTERN_REPLACE_ROP_END_DATETIME;

    public String getNodeName() {
        return nodeName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getRemoteFilename() {
        return remoteFilename;
    }

    public String getRemoteFilenameNoExt() {
        return remoteFilenameNoExt;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getHash() {
        return hash;
    }

    public String getRopStartDatetime() {
        return ropStartDatetime;
    }

    public String getRopEndDatetime() {
        return ropEndDatetime;
    }
}
