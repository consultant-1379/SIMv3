package com.ericsson.sim.engine;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.common.config.AppConfigReader;
import com.ericsson.sim.engine.config.ConfigService;
import com.ericsson.sim.engine.server.SimServer;
import com.ericsson.sim.engine.server.SimServerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.util.Map;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    private static Thread shutdownHook;

    public static void main(String[] args) {

        final String appHome = System.getProperty("app.home");
        if (appHome == null || appHome.isEmpty()) {
            logger.error("Property app.home must be set to {} install directory. Start the program with -Dapp.home=<path>. Program will now exit", Constants.APP_NAME);
            System.exit(1);
        }

        SimServer.ServerErrors status = null;
        try {
            final SimServer server = new SimServerImpl();
            setupShutdownHook(server);

            status = startServer(server, args);
            if (status != SimServer.ServerErrors.STARTED) {
                logger.info("{} exiting with status {}", Constants.APP_NAME, status.name());
                //remove the hook now that we are closing things ourselves
                if (shutdownHook != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
                server.stopServer();
                exit(status.ordinal());
            } else {
                logger.info("Server startup complete");
            }
        } catch (RuntimeException e) {
            logger.error("{}", e.getMessage());
            status = SimServer.ServerErrors.UNKNOWN_ERROR;
            exit(status.ordinal());
        } catch (RemoteException e) {
            logger.error("Remote exception caught", e);
            status = SimServer.ServerErrors.UNKNOWN_ERROR;
            exit(status.ordinal());
        }
    }

    private static void dumpAll() {
        Map<Thread, StackTraceElement[]> traceElements = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> stackTraceElement : traceElements.entrySet()) {
            for (StackTraceElement element : stackTraceElement.getValue()) {
                logger.debug("{}: {}", stackTraceElement.getKey().getName(), element.toString());
            }
        }
    }

    private static SimServer.ServerErrors startServer(SimServer server, String[] args) throws RemoteException {

        //Step 1- Get app config path from passed arguments
        String configPath = getConfigPath(args);
        if (configPath == null) {
            return SimServer.ServerErrors.APP_CONFIG_ERROR;
        }

        //Step 2- Read app config
        AppConfigReader configReader = readAppConfig(configPath);
        if (configReader == null) {
            return SimServer.ServerErrors.APP_CONFIG_ERROR;
        }

        if (isRunning(configReader.getConfigModel().getRmi().getRegistryPort())) {
            logger.error("Another server may be running. Cannot have two servers running on {}", configReader.getConfigModel().getRmi().getRegistryPort());
            return SimServer.ServerErrors.GENERAL_ERROR;
        }

        return server.startServer(new ConfigService(configReader.getConfigModel()));
    }

    private static boolean isRunning(int serverPort) {
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            return false;
        } catch (Exception e) {
            logger.debug("Unable to create socket, another server may be running on port {}. {}", serverPort, e.getMessage());
            return true;
        }
    }

    private static void setupShutdownHook(final SimServer server) {
        shutdownHook = new Thread(() -> {
            try {
                logger.info("Shutdown hook called!");
                server.stopServer();
                logger.info("Shutdown completed!");
            } catch (Throwable t) {
                logger.error("Failed to complete shutdown", t);
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static AppConfigReader readAppConfig(String configPath) {
        AppConfigReader reader = new AppConfigReader(configPath);
        try {
            reader.read();
            if (!reader.verify()) {
                logger.error("Config contains errors. ");
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to read config.", e);
            return null;
        }
        return reader;
    }

    private static String getConfigPath(String[] args) {
        String configPath = HelpUtil.getArg(args, "--config", false);
        if (configPath == null || configPath.isEmpty()) {
            logger.debug("Config not provided through --config argument. Checking for config property (set as -Dconfig=<path>) when starting {}", Constants.APP_NAME);
            configPath = System.getProperty("config");
            if (configPath == null || configPath.isEmpty()) {
                logger.debug("Config not provided through -Dconfig option. Trying default {}", Constants.PATHS.APP_CONFIG);
                configPath = Constants.PATHS.APP_CONFIG;
            }
        } else {
            String[] split = configPath.split("=");
            if (split.length < 2) {
                logger.warn("--config argument provided should be in format --config=<path>. We go {}", configPath);
                configPath = System.getProperty("config");
            } else {
                configPath = split[1];
                logger.debug("Config path passed through argument is {}", configPath);
            }
        }

        if (configPath == null || "".equals(configPath)) {
            logger.error("Config path not provided in property or passed as argument.");
            return null;
        }
        return configPath;
    }

    private static void exit(int exitValue) {
        logger.info("Exiting with code {}", exitValue);
        System.exit(exitValue);
    }
}
