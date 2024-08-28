package com.ericsson.sim.sftp.processor;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.plugins.model.Config;
import com.ericsson.sim.sftp.processor.interfaces.FilenameProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;

public class ReplacePatternProcessor implements FilenameProcessor {

    private static final Logger logger = LogManager.getLogger(ReplacePatternProcessor.class);

    private final Config configuration;

    private final String patternNodeName;

    private final String patternIPAddress;
    private final String patternHashcode;
    private final String patternRemoteFilename;
    private final String patternRemoteFilenameNoExt;
    private final String patternUniqueId;
    private final String patternRopStart;
    private final String patternRopEnd;

    private final String jobId;
    private final String ropStartDatetime;
    private final String ropEndDatetime;

    public ReplacePatternProcessor(Config configuration, String jobId, String patternNodeName, String patternIPAddress, String patternHashcode, String patternUniqueId, String patternRemoteFilename, String patternRemoteFilenameNoExt, String patternRopStart, String patternRopEnd, String ropStartDatetime, String ropEndDatetime) {

        this.configuration = configuration;
        this.patternNodeName = patternNodeName;
        this.patternIPAddress = patternIPAddress;
        this.patternHashcode = patternHashcode;
        this.patternRemoteFilename = patternRemoteFilename;
        this.patternRemoteFilenameNoExt = patternRemoteFilenameNoExt;
        this.patternUniqueId = patternUniqueId;
        this.patternRopStart = patternRopStart;
        this.patternRopEnd = patternRopEnd;

        this.jobId = jobId;
        this.ropStartDatetime = ropStartDatetime;
        this.ropEndDatetime = ropEndDatetime;
    }

    @Override
    public String process(String src, String dst, Matcher matcher) {

        String updatedFileName = src;

        updatedFileName = replace(updatedFileName, this.patternNodeName, configuration.getPropertyAsString(Constants.CONFIG_NODE_NAME));
        updatedFileName = replace(updatedFileName, this.patternIPAddress, configuration.getPropertyAsString(Constants.CONFIG_IPADDRESS, ""));
        updatedFileName = replace(updatedFileName, this.patternHashcode, HelpUtil.timeHashCode(configuration.getPropertyAsString(Constants.CONFIG_NODE_NAME)));
        updatedFileName = replace(updatedFileName, this.patternRemoteFilename, dst);
        updatedFileName = replace(updatedFileName, this.patternRemoteFilenameNoExt, removeExt(dst));
        updatedFileName = replace(updatedFileName, this.patternUniqueId, jobId);
        updatedFileName = replace(updatedFileName, this.patternRopStart, ropStartDatetime);
        updatedFileName = replace(updatedFileName, this.patternRopEnd, ropEndDatetime);

        return updatedFileName;
    }

    private String removeExt(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        int last = filename.lastIndexOf('.');
        if (last < 0) {
            return filename;
        } else if (last == 0) {
            return filename;
        }
        return filename.substring(0, last);
    }

    private String replace(String source, String find, String replacement) {
        logger.debug("Looking to find {} in name {} to replace with {}", find, source, replacement);
        if (source.contains(find)) {
            source = HelpUtil.replaceAll(source, find, replacement);
            logger.debug("Updated filename after {} replace: {}", find, source);
        }
        return source;
    }

    @Override
    public String getProcessorName() {
        return "LocalFilenameProcessor";
    }
}
