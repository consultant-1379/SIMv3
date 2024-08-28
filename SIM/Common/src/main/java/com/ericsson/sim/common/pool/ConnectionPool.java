package com.ericsson.sim.common.pool;

import com.ericsson.sim.plugins.model.ServerDetails;
import com.ericsson.sim.plugins.protocols.Connection;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;

public class ConnectionPool<S extends ServerDetails, C extends Connection> implements Closeable {
    private static final Logger logger = LogManager.getLogger(ConnectionPool.class);

    private final GenericKeyedObjectPool<S, C> pool;

    private final int maxActive;
    private final int initIdle;

    public ConnectionPool(GenericKeyedObjectPool<S, C> keyedObjectPool, int maxActive, int initIdle) {
//    public ConnectionPool(GenericKeyedObjectPool<S, C> keyedObjectPool, int maxActive) {

        this.maxActive = maxActive;
        this.initIdle = initIdle;

        this.pool = keyedObjectPool;
        pool.setMaxActive(maxActive);
        pool.setMinIdle(initIdle);
//        pool.setMaxTotal(maxActive);
        pool.setTestOnBorrow(true);

        //when we reach max sessions, we block
        pool.setWhenExhaustedAction(GenericKeyedObjectPool.WHEN_EXHAUSTED_BLOCK);
    }

    public GenericKeyedObjectPool<S, C> getPool() {
        return pool;
    }

    @Override
    public void close() {
        try {
            logger.debug("Closing pool");
//            pool.clear();
            pool.close();
        } catch (Exception ignore) {
        }
    }

    @Override
    public String toString() {
        return "ConnectionPool{" +
                "maxActive=" + maxActive +
                ", initIdle=" + initIdle +
                '}';
    }
}
