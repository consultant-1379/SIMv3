package com.ericsson.sim.sftp.processor;

import com.ericsson.sim.sftp.processor.interfaces.FilenameProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;

public class RegexProcessor implements FilenameProcessor {

    private static final Logger logger = LogManager.getLogger(RegexProcessor.class);

    public RegexProcessor() {

    }

    @Override
    public String getProcessorName() {
        return "RegexProcessor";
    }

    @Override
    public String process(String src, String dst, Matcher matcher) {
        if (matcher == null) {
            logger.debug("Remote file pattern matcher is null. Nothing to replace");
            return dst;
        }

        logger.debug("Using local file replace pattern {}", src);
        String result = matcher.replaceFirst(src);
        logger.debug("Updated filename after group replace: {}", result);
        return result;
    }
}
