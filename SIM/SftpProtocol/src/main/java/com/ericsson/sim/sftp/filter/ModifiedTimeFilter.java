package com.ericsson.sim.sftp.filter;

import com.jcraft.jsch.SftpATTRS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.regex.Matcher;

public class ModifiedTimeFilter implements FileFilter {
    private static final Logger logger = LogManager.getLogger(ModifiedTimeFilter.class);

    private final long lookbackEpochSec;

    public ModifiedTimeFilter(LocalDateTime currentTime, int lookbackTime) {
        this.lookbackEpochSec = FileFilter.toEpoch(currentTime, lookbackTime);
    }

    public boolean accept(Matcher filenameMatcher, SftpATTRS sftpAttr) {
        //getMTime return seconds.
        int mTime = sftpAttr.getMTime();

        logger.debug("Remote file mTime is {}sec and look-back time since epoch is {}sec", mTime, lookbackEpochSec);

        //accept if mTime is after the lookback time only (or equal)
        return mTime >= lookbackEpochSec;
    }
}
