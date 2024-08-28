package com.ericsson.sim.cli;


import com.ericsson.sim.plugins.rmi.JobStatus;
import com.ericsson.sim.plugins.rmi.Server;
import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.config.AppConfig;
import com.ericsson.sim.common.config.AppConfigReader;
import com.ericsson.sim.common.util.CliUtil;
import com.ericsson.sim.common.util.HelpUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Main {
    private final static DateTimeFormatter jobFireTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm:ss");

    public static void main(String[] args) {

        //Pre-checks
        final String appHome = System.getProperty("app.home");
        if (appHome == null || appHome.isEmpty()) {
            CliUtil.print("Property app.home must be set to " + Constants.APP_NAME + " install directory. Start the program with -Dapp.home=<path>. Program will now exit");
            System.exit(1);
        }
        final int bypassChoice;

        if (HelpUtil.hasArg(args, "--reloadConfig")) {
            CliUtil.success("Reload node configuration flag is passed. Will only reload node config");
            CliUtil.print("");
            bypassChoice = 3;
        } else {
            bypassChoice = 0;
        }

        //Read config
        CliUtil.header("Reading configuration");
        String configPath = getConfigPath(args);
        AppConfigReader configReader = readAppConfig(configPath);
        AppConfig model = configReader.getConfigModel();
        CliUtil.success("Configuration read");

        CliUtil.header("Connecting with server");
        Registry registry = getRegistry(model.getRmi().getRegistryPort());
        Server server = null;
        try {
            server = (Server) registry.lookup(model.getRmi().getServerName());
        } catch (RemoteException | NotBoundException e) {
            CliUtil.fatal("Failed to get RMI server instance", e);
        }
        if (server == null) {
            CliUtil.fatal("Server instance is null");
        }
        CliUtil.success("Connected with " + model.getRmi().getServerName());

        do {
            String option = "" + bypassChoice;
            if (bypassChoice == 0) {
                CliUtil.header("Select an option to execute");
                CliUtil.print(CliUtil.formatGreen("1-") + " Print jobs");
                CliUtil.print(CliUtil.formatGreen("2-") + " Install node type");
                CliUtil.print(CliUtil.formatGreen("3-") + " Reload NE configuration");
                CliUtil.print(CliUtil.formatGreen("x-") + " Exit");
                option = CliUtil.input("Choice");
            }

            switch (option) {
                case "1":
                    CliUtil.header("Listing scheduled jobs");
                    List<JobStatus> jobs = null;
                    try {
                        jobs = server.listJobStatus();

                        if (jobs != null) {
                            for (JobStatus jobStatus : jobs) {
                                CliUtil.header("Job name: " + jobStatus.jobName);
                                CliUtil.print(CliUtil.formatBlue("Expression:       ") + jobStatus.executionExpression);
                                CliUtil.print(CliUtil.formatBlue("Summary:          ") + jobStatus.executionSummary);
                                CliUtil.print(CliUtil.formatBlue("Next execution:   ") + jobFireTimeFormatter.format(jobStatus.nextExecution.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
                                CliUtil.print(CliUtil.formatBlue("Statistics: ") + jobStatus.stats.toString());
                                CliUtil.endHeader();
                            }
                        }
                    } catch (RemoteException e) {
                        CliUtil.error("Failed to list jobs: " + e.getMessage());
                        continue;
                    }

                    break;


                case "2":
                    CliUtil.header("Adding new node type");
                    File jarPath = new File(CliUtil.input("Enter absolute path to jar file"));
                    if (!jarPath.isAbsolute()) {
                        CliUtil.error("Path is not absolute.");
                        continue;
                    }
                    if (!jarPath.exists()) {
                        CliUtil.error("Path '" + jarPath.getAbsolutePath() + "' does not exist");
                    } else {

                        CliUtil.print("Jar file: " + jarPath.getAbsolutePath());
                        File nodeTypePath = new File(model.getPaths().getNodeTypes());
                        if (!nodeTypePath.exists()) {
                            CliUtil.error("Path " + model.getPaths().getNodeTypes() + " does not exit. New node installation requires this path");
                            continue;
                        }

                        Server.AddNodeResult result = null;
                        try {
                            CliUtil.print("Installing node type");
                            result = server.addNodeType(jarPath.getAbsolutePath());
                        } catch (IOException e) {
                            CliUtil.error("Failed to add node: " + e.getMessage());
                            continue;
                        }
                        if (result == Server.AddNodeResult.ADDED) {
                            CliUtil.success("Node type added");
                            try {
                                // copy if not already there
                                if (!Files.isSameFile(nodeTypePath.toPath(), jarPath.getParentFile().toPath())) {
                                    CliUtil.print("Copying " + jarPath.getName() + " to node type dir: " + model.getPaths().getNodeTypes());
                                    Files.copy(jarPath.toPath(), Paths.get(model.getPaths().getNodeTypes(), jarPath.getName()));
                                    CliUtil.success("File copied");
                                }
                            } catch (IOException e) {
                                CliUtil.error("", e);
                                continue;
                            }
                        } else if (result == Server.AddNodeResult.ALREADY_EXISTS) {
                            CliUtil.warn("Node type is already loaded. Will not load it again");
                        } else if (result == Server.AddNodeResult.FAILURE) {
                            CliUtil.error("Failed to add node type. Check " + Constants.APP_NAME + " logs for more information");
                        } else if (result == Server.AddNodeResult.INVALID_PATH) {
                            CliUtil.error("Jar path provided is not valid. Check " + Constants.APP_NAME + " logs for more information");
                        }
                    }

                    break;


                case "3":
                    CliUtil.header("Reloading configuration");
                    try {
                        Server.UpdateConfigResult result = server.updateConfig();
                        switch (result) {
                            case INVALID:
                                CliUtil.error("Failed to reload config. Check logs for more information");
                                break;
                            case NO_UPDATE:
                                CliUtil.success("Loading configuration is same as before. Nothing to do");
                                break;
                            case UPDATED:
                                CliUtil.success("Configuration reloaded");
                                break;
                        }
                    } catch (Throwable e) {
                        CliUtil.error("Failed to update: " + e.getMessage());
                        continue;
                    }
                    break;


                case "x":
                    CliUtil.print("Exiting ...");
                    return;
            }

            if (bypassChoice > 0) {
                break;
            }
        } while (bypassChoice == 0);
    }

    private static AppConfigReader readAppConfig(String configPath) {
        AppConfigReader reader = new AppConfigReader(configPath);
        try {
            reader.read();
            if (!reader.verify()) {
                CliUtil.fatal("Config contains errors.");
            }
        } catch (IOException e) {
            CliUtil.fatal("Failed to read config.", e);
        }
        return reader;
    }

    private static String getConfigPath(String[] args) {
        String configPath = HelpUtil.getArg(args, "--config", false);
        if (configPath == null || configPath.isEmpty()) {
            configPath = System.getProperty("config");
            if (configPath == null || configPath.isEmpty()) {
                configPath = Constants.PATHS.APP_CONFIG;
            }
        } else {
            String[] split = configPath.split("=");
            if (split.length < 2) {
                CliUtil.warn("--config argument provided should be in format --config=<path>. We go: " + configPath);
                configPath = System.getProperty("config");
            } else {
                configPath = split[1];
            }
        }

        if (configPath == null || "".equals(configPath)) {
            CliUtil.fatal("Config path not provided in property or passed as argument.");
            return null;
        }
        return configPath;
    }

    private static Registry getRegistry(int registryPort) {
        try {
            return LocateRegistry.createRegistry(registryPort);
        } catch (Exception ignored) {
        }
        try {
            return LocateRegistry.getRegistry(registryPort);
        } catch (RemoteException e) {
            CliUtil.fatal("Failed to get RMI registry", e);
            return null;
        }
    }
}
