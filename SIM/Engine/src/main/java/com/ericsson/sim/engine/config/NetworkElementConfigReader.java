package com.ericsson.sim.engine.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class NetworkElementConfigReader {

    private static final Logger logger = LogManager.getLogger(NetworkElementConfigReader.class);

    private final File configPath;
    private final String commentStart;
    private final String delimiter;

    public NetworkElementConfigReader(String configPath, String commentStart, String delimiter) {
        this.configPath = new File(configPath);
        this.commentStart = commentStart;
        this.delimiter = delimiter;
    }

    /**
     * Load NE configuration from file line by line
     *
     * @return List of read lines from file
     * @throws IOException If an error occurred while opening or reading file
     */
    public List<Line> read() throws IOException {
        logger.info("Going to read config from {}", configPath.getAbsolutePath());
        List<Line> lines = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(this.configPath))) {
            String line = null;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                line = line.trim();

                if (line.length() == 0 || line.startsWith(commentStart)) {
                    continue;
                }
                logger.debug("Parsing line: {}", line);
                String[] split = Arrays.stream(line.split(delimiter, -1)).map(String::trim).toArray(String[]::new);
                logger.debug("Split length: {}", split.length);
                lines.add(new Line(row, split));
            }
        }
        return lines;
    }

    /**
     * Represents a split line in config along with its actual line number
     */
    public static class Line {
        public final String[] line;
        public final int row;

        private Line(int row, String[] line) {
            this.row = row;
            this.line = line;
        }
    }
}
