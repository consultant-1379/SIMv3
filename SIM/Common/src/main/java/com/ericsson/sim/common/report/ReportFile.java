package com.ericsson.sim.common.report;

import com.ericsson.sim.common.report.model.Report;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class ReportFile {

    private final File reportFile;

    private Report report;
    private static final Logger logger = LogManager.getLogger(ReportFile.class);

    public ReportFile(File reportFile) throws IOException {
        this.reportFile = reportFile;

        if (!reportFile.exists()) {
            logger.debug("Creating report file {}", reportFile.getAbsolutePath());
            if (!reportFile.createNewFile()) {
                throw new IOException("Unable to create history file " + reportFile.getName());
            }
        }
    }

    
}
