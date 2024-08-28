package com.ericsson.sim.common.pool;

import com.ericsson.sim.plugins.model.ServerDetails;
import com.ericsson.sim.plugins.protocols.Connection;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.PoolUtils;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.NoSuchElementException;

public class ShrinkableKeyedObjectPool<S extends ServerDetails, C extends Connection> extends GenericKeyedObjectPool<S, C> {
    private static final Logger logger = LogManager.getLogger(ShrinkableKeyedObjectPool.class);


    public ShrinkableKeyedObjectPool(KeyedPoolableObjectFactory<S, C> sftpSessionFactory) {
//        super(sftpSessionFactory);
        super(PoolUtils.synchronizedPoolableFactory(sftpSessionFactory));
    }

    @Override
    public C borrowObject(S key) throws Exception {
        try {
            return super.borrowObject(key);
        } catch (NoSuchElementException | IllegalStateException e) {
            //pool exhausted. should not happen as we are using block on exhaust strategy
            // OR
            // Timeout waiting for idle object
            // pool closed
            throw e;
        } catch (Exception e) {
            return shrinkPool(key, e);
//                return null;
//                throw e;
        }
    }

    private C shrinkPool(S key, Exception reason) throws Exception {
        synchronized (this) {
            //keep trying until we can borrow from existing pool rather then try to
            //create a new one
            while (true) {
                try {
                    //try one more time, maybe the pool has been reduced before.
                    return super.borrowObject(key);
                } catch (NoSuchElementException | IllegalStateException e) {
                    //pool exhausted. should not happen as we are using block on exhaust strategy
                    // OR
                    // Timeout waiting for idle object
                    // pool closed
                    throw e;
                } catch (Exception e) {
                    //if its auth failure then there is no need to shrink anything
                    if (e.getMessage() != null && e.getMessage().contains("com.jcraft.jsch.JSchException: Auth fail")) {
                        throw e;
                    }
//                    int maxTotal = this.getMaxTotal();
                    int maxActive = this.getMaxActive();
//                    int maxIdle = this.getMaxIdle();
                    if (maxActive <= 1) {
                        logger.warn("Cannot shrink further as max total is approaching 0, pool size: {}", maxActive);
                        throw e;
                    } else {
                        logger.info("Shrinking pool from " + maxActive + " to " + (maxActive - 1) + " because " + e.getMessage());
//                        this.setMaxTotal(maxTotal - 1);
                        this.setMaxActive(maxActive - 1);
                    }
                }
            }
        }
    }
}
