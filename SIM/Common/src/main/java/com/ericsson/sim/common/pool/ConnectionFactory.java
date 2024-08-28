package com.ericsson.sim.common.pool;

import com.ericsson.sim.plugins.protocols.Connection;
import com.ericsson.sim.plugins.model.ServerDetails;
import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ConnectionFactory<S extends ServerDetails, C extends Connection> extends BaseKeyedPoolableObjectFactory<S, C> {
    private static final Logger logger = LogManager.getLogger(ConnectionFactory.class);
    protected final Map<S, ConnectionIdPool> idPool = new HashMap<>();

    @Override
    public void destroyObject(S key, C connection) {
        if (connection != null) {
            logger.debug("Destroying {}", connection.getId());
            connection.disconnect();
        }
    }

    @Override
    public void activateObject(S key, C connection) throws Exception {
        if (!connection.isConnected()) {
            logger.debug("{} is not active, connecting again", connection.getId());
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
            connection.connect(key);
        }
    }

    @Override
    public boolean validateObject(S key, C connection) {
        logger.debug("Validating {}", connection.getId());
        return connection.isConnected();
    }

    protected String getId(S key) {
        ConnectionIdPool connectionIdPool = getIdPool(key);

        if (connectionIdPool != null) {
            synchronized (connectionIdPool) {
                return connectionIdPool.getAvailable();
            }
        }
        return "";
    }

    private ConnectionIdPool getIdPool(S key) {
        if (key == null) {
            logger.warn("Server detail passed is null. Cannot provide ID pool facilities");
            return null;
        }
        ConnectionIdPool connectionIdPool = null;
        synchronized (idPool) {
            connectionIdPool = idPool.get(key);
            if (connectionIdPool == null) {
                connectionIdPool = new ConnectionIdPool(key.getHost());
                idPool.put(key, connectionIdPool);
            }
        }

        return connectionIdPool;
    }

    private static final class ConnectionIdPool {
        private final AtomicInteger step = new AtomicInteger(0);
        private Map<String, Boolean> pool = new HashMap<>();

        private final String host;

        public ConnectionIdPool(String host) {
            this.host = host;
        }

        public synchronized String getAvailable() {
            Optional<Map.Entry<String, Boolean>> available = pool.entrySet().stream().filter(Map.Entry::getValue).findFirst();
            if (available.isPresent()) {
                return available.get().getKey();
            } else {
                String id = host + "_con_" + step.getAndIncrement();
                pool.put(id, false);
                return id;
            }
        }

        public synchronized void returnId(String id) {
            pool.put(id, true);
        }
    }
}
