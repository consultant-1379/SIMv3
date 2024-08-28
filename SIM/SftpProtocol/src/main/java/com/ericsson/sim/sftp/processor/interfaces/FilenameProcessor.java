package com.ericsson.sim.sftp.processor.interfaces;

import java.util.regex.Matcher;

public interface FilenameProcessor extends Processor<String, String, Matcher> {
    String getProcessorName();
}
