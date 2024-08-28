package com.ericsson.sim.engine.server;

import com.ericsson.sim.engine.config.ConfigService;
import com.ericsson.sim.engine.model.RuntimeConfig;
import com.ericsson.sim.engine.policy.eniq.EniqHealthCheck;
import com.ericsson.sim.engine.config.NetworkElementConfigReader;
import com.ericsson.sim.engine.nodetype.model.NodeType;
import com.ericsson.sim.engine.scheduler.SchedulerService;
import com.ericsson.sim.plugins.policy.HealthCheck;
import com.ericsson.sim.plugins.rmi.JobStatus;
import com.ericsson.sim.plugins.rmi.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.SchedulerException;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SimServerImpl extends UnicastRemoteObject implements SimServer {

    private static final Logger logger = LogManager.getLogger(SimServerImpl.class);

    private ConfigService configService = null;
    private SchedulerService schedulerService = null;
    private HealthCheck healthCheckService = null;
    private Map<String, RuntimeConfig> runtimeConfig = null;
    private boolean shutdown = false;

    public SimServerImpl() throws RemoteException {
    }

    @Override
    public List<JobStatus> listJobStatus() {
        return schedulerService.listJobStatus();
    }

    @Override
    public AddNodeResult addNodeType(String jarPath) throws IOException {
//        String jarPath = Paths.get(configService.getAppConfig().getNodetype_path(), jarPath).toString();
        return configService.addNodeType(jarPath);
    }

    @Override
    public synchronized UpdateConfigResult updateConfig() throws RuntimeException, IOException {
        UpdateConfigResult result = null;

        Map<String, NodeType> nodeTypes = configService.getNodeTypes(false);
        List<NetworkElementConfigReader.Line> neConfig = configService.loadNetworkElementConfig(true);
        if (neConfig == null) {
            logger.error("New network element config is null. Will not do anything");
            return UpdateConfigResult.INVALID;
        }

        Map<String, RuntimeConfig> newRuntimeConfig = configService.getRuntimeConfig(nodeTypes, neConfig, true);
        if (newRuntimeConfig == null) {
            logger.error("New runtime config is null. Will not do anything");
            return UpdateConfigResult.INVALID;
        }

        //check if the current config is same as the one loaded before
        if (this.runtimeConfig.equals(newRuntimeConfig)) {
            logger.info("Newly loaded network element config is same as the one loaded before. Will do nothing");
            return UpdateConfigResult.NO_UPDATE;
        }

        if (this.schedulerService != null) {
            try {
                this.schedulerService.reschedule(configService, this.runtimeConfig, newRuntimeConfig, healthCheckService);
            } catch (SchedulerException e) {
                logger.error("Failed to reschedule updated configuration", e);
                throw new RuntimeException(e);
            }
        }

        this.runtimeConfig = newRuntimeConfig;
        result = UpdateConfigResult.UPDATED;
        return result;
    }

    public ServerErrors startServer(ConfigService configService) {
        logger.debug("Call to startServer");
        this.configService = configService;
        this.schedulerService = new SchedulerService();
        try {

            final String serverName = configService.getAppConfig().getRmi().getServerName();
            logger.info("Starting RMI server '{}'", serverName);
            try {
                Registry registry = getRegistry();
                logger.debug("Names bound to registry: {}", Arrays.toString(registry.list()));
                registry.rebind(serverName, (Server) this);
                logger.info("RMI server ready. Binding: {}", serverName);
            } catch (RemoteException e) {
                logger.error("Failed to start RMI server. CLI features may not work", e);
            }

            //Step 3- Read the deployed node types configuration
            Map<String, NodeType> nodeTypes = configService.getNodeTypes(true);
            if (nodeTypes == null) {
                return ServerErrors.NODE_TYPE_CONFIG_ERROR;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Printing known types: \n{}", Arrays.toString(nodeTypes.entrySet().toArray()));
            }

            //Step 4- Load commissioned network element config
            List<NetworkElementConfigReader.Line> configuredNEs = configService.loadNetworkElementConfig(false);
            if (configuredNEs == null) {
                return ServerErrors.NE_CONFIG_ERROR;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Printing configured NEs: \n{}", Arrays.toString(configuredNEs.toArray()));
            }

            //Step 5- Create runtime config for commissioned nodes using node types configuration
            runtimeConfig = configService.getRuntimeConfig(nodeTypes, configuredNEs, false);
            if (runtimeConfig == null || runtimeConfig.size() == 0) {
                logger.error("No runtime configuration can be created either because no node type exists, or NE config is empty, or config has errors");
                return ServerErrors.NE_CONFIG_ERROR;
            }
            logger.debug("Runtime config containing {} nodes", runtimeConfig.size());
            if (logger.isTraceEnabled()) {
                logger.trace("Printing runtime config: \n{}", Arrays.toString(runtimeConfig.entrySet().toArray()));
            }

            //Step 6- Start health service
            try {
                healthCheckService = new EniqHealthCheck(configService.getAppConfig().getPaths().getPolicyConfig());
                healthCheckService.startup();
            } catch (RuntimeException e) {
                healthCheckService = EniqHealthCheck.DEFAULT_CHECK;
                logger.error("Failed to start health check service. A default state of " + healthCheckService.isOk() + " will be returned to let service run", e);
            }

            //Step 7- Start scheduler
            try {
                schedulerService.initialize(configService);
                schedulerService.scheduleJob(configService, null, runtimeConfig, healthCheckService);
            } catch (SchedulerException e) {
                logger.error("Failed to start scheduler.", e);
                return SimServer.ServerErrors.SCHEDULER_ERROR;
            }
        } catch (Throwable t) {
            logger.error("Unknown error occurred", t);
            return ServerErrors.GENERAL_ERROR;
        }

        logger.debug("startServer completed");
        return ServerErrors.STARTED;
    }

    private synchronized void shutdown() {
        //if already shutdown
        if (shutdown) {
            logger.info("Server is already shutdown. Nothing to do");
            return;
        }

        //Stop scheduler
        if (schedulerService != null) {
            logger.info("Shutting down scheduler");
            try {
                schedulerService.shutdown();
            } catch (Throwable e) {
                logger.error("Error while trying to shutdown scheduler", e);
            }
        } else {
            logger.error("SchedulerService is null. Was it initialized correctly?");
        }

        if (healthCheckService != null) {
            logger.info("Shutting down health service");
            try {
                healthCheckService.shutdown();
            } catch (Throwable e) {
                logger.error("Error while trying to shutdown health check service", e);
            }
        } else {
            logger.error("Health check service is null. Was it initialized correctly?");
        }

        //Stop RMI server
        try {
            logger.info("Shutting down RMI server");
            if (this.configService != null) {
                final String serverName = configService.getAppConfig().getRmi().getServerName();
                Registry registry = getRegistry();
                logger.debug("{}", Arrays.toString(registry.list()));
                if (Arrays.asList(registry.list()).contains(serverName)) {
                    logger.debug("Unbinding server named {}", serverName);
                    registry.unbind(serverName);
                } else {
                    logger.debug("{} is not bound. RMI server may not be running. Nothing to do", serverName);
                }
//                UnicastRemoteObject.unexportObject(this, true);
            } else {
                logger.warn("Config service is not set, cannot shutdown RMI server properly");
            }
        } catch (Throwable e) {
            logger.error("Failed to stop RMI server", e);
        }

        shutdown = true;
        logger.info("Shutdown complete");
    }

    private Registry getRegistry() throws RemoteException {
        final int registryPort = configService.getAppConfig().getRmi().getRegistryPort();
        try {
            logger.debug("Creating and returning registry at port {}", registryPort);
            return LocateRegistry.createRegistry(registryPort);
        } catch (Exception e) {
            logger.debug("Registry may already exist. {}", e.getMessage());
        }

        logger.debug("Getting registry that exists on port {}", registryPort);
        return LocateRegistry.getRegistry(registryPort);
    }

    @Override
    public void stopServer() {
        logger.info("Call to shutdown server");
        shutdown();
        logger.info("Shutdown complete");
    }

    @Override
    public boolean safeStopServer() {
        try {
            if (schedulerService != null) {
                if (schedulerService.canShutdown()) {
                    stopServer();
                }
            } else
                logger.error("SchedulerService is null. Was it initialized correctly?");
        } catch (Exception e) {
            logger.error("Failed to safe stop server.", e);
            throw new RuntimeException(e);
        }

        return true;
    }
}
