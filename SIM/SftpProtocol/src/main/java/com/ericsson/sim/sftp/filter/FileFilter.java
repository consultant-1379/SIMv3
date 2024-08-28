package com.ericsson.sim.sftp.filter;

import com.jcraft.jsch.SftpATTRS;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;

public interface FileFilter {
    boolean accept(Matcher filenameMatcher, SftpATTRS sftpAttr);

    static long toEpoch(LocalDateTime currentTime, int lookbackTime) {
        //aTime and mTime are represented as seconds from Jan 1, 1970 in UTC.
        return currentTime.minusMinutes(lookbackTime).toEpochSecond(ZoneOffset.UTC);
    }
}
