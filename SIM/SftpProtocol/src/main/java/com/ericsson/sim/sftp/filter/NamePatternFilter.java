package com.ericsson.sim.sftp.filter;

import com.ericsson.sim.sftp.filter.model.TimeFilterConfig;
import com.jcraft.jsch.SftpATTRS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;

public class NamePatternFilter implements FileFilter {

    private static final Logger logger = LogManager.getLogger(NamePatternFilter.class);

    private final TimeFilterConfig timeConfig;
    private final LocalDateTime lookbackDateTime;

    public NamePatternFilter(LocalDateTime currentTime, int lookbackTime, TimeFilterConfig timeConfig) {
        this.lookbackDateTime = currentTime.minusMinutes(lookbackTime);
        this.timeConfig = timeConfig;
    }

    @Override
    public boolean accept(Matcher filenameMatcher, SftpATTRS sftpAttr) {

        if (timeConfig.ropDateGroupId > filenameMatcher.groupCount()) {
            logger.error("File name matcher has only {} groups. Date part group {} in config is not valid", filenameMatcher.groupCount(), timeConfig.ropDateGroupId);
            logger.error("File time cannot be matched, file will be discarded");
            return false;
        } else if (timeConfig.ropTimeGroupId > filenameMatcher.groupCount()) {
            logger.error("File name matcher has only {} groups. Time part group {} in config is not valid", filenameMatcher.groupCount(), timeConfig.ropTimeGroupId);
            logger.error("File time cannot be matched, file will be discarded");
            return false;
        }

        final String dateTime = timeConfig.getDateTime(filenameMatcher.group(timeConfig.ropDateGroupId), filenameMatcher.group(timeConfig.ropTimeGroupId));
        logger.debug("Date time extracted from filename is {} ", dateTime);

        try {
            LocalDateTime remoteFileDateTime = LocalDateTime.parse(dateTime, timeConfig.dateTimeFormatter);

            //if remote time is newer than lookback time
            if (remoteFileDateTime.isAfter(lookbackDateTime)) {
                return true;
            }

        } catch (DateTimeParseException e) {
            logger.error("Failed to parse " + dateTime + " with pattern provided in config. File time cannot be matched, file will be discarded");
            return false;
        }

        return false;
    }
}
