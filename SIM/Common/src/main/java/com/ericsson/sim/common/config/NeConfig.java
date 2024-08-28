package com.ericsson.sim.common.config;

import com.ericsson.sim.common.Constants;

public class NeConfig {
    String commentStart = Constants.CSV_COMMENT;
    String delimiter = Constants.CSV_DEFAULT_SEPARATOR;

    public String getDelimiter() {
        return delimiter;
    }

    public String getCommentStart() {
        return commentStart;
    }
}
