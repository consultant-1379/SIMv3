package com.ericsson.sim.sftp;

import com.ericsson.sim.common.pool.ConnectionPool;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;

public class SftpConnectionPool extends ConnectionPool<SftpServerDetails, SftpConnection> {

    public SftpConnectionPool(GenericKeyedObjectPool<SftpServerDetails, SftpConnection> keyedObjectPool, int maxActive, int initIdle) {
//    public SftpConnectionPool(GenericKeyedObjectPool<SftpServerDetails, SftpConnection> keyedObjectPool, int maxActive) {
//        super(keyedObjectPool, maxActive);
        super(keyedObjectPool, maxActive, initIdle);
    }
}
