package com.ericsson.sim.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExecUtil {
    private static final Logger logger = LogManager.getLogger(ExecUtil.class);

    private ExecUtil() {
    }

    /**
     * Execute an external program
     *
     * @param program Name or path of program to be executed
     * @return Pair whose key is return value and value is a list of lines from output. Returns null if the program cannot be executed
     */
    public static AbstractMap.SimpleEntry<Integer, List<String>> execute(String program) {
        return execute(program, null);
    }

    /**
     * Execute an external program with provided arguments
     *
     * @param program   Name or path of program to be executed
     * @param arguments Arguments to be passed, or null
     * @return Pair whose key is return value and value is a list of lines from output. Returns null if the program cannot be executed
     */
    public static AbstractMap.SimpleEntry<Integer, List<String>> execute(String program, String[] arguments) {
        try {
            logger.debug("Launching: {} with argument list: {}", program, arguments != null ? String.join(",", arguments) : "");

            List<String> command = new ArrayList<>();
            command.add(program);
            if (arguments != null && arguments.length > 0) {
                command.addAll(Arrays.asList(arguments));
            }

            Process process = new ProcessBuilder(command).start();

            try {
                process.waitFor();
            } catch (InterruptedException e) {
                logger.error("Subprocess " + program + " was interrupted: ", e);
                //in any case check if the directory may have still been created
                return null;
            }

            int exitValue = process.exitValue();
            List<String> output = new ArrayList<String>(50);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output.add(reader.readLine());
            }

            return new AbstractMap.SimpleEntry<>(exitValue, output);
        } catch (IOException e) {
            logger.error("Failed to execute " + program + ". Make sure that the program exists and has right permissions", e);
            return null;
        }
    }
}
