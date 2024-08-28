package com.ericsson.sim.sftp;

import com.ericsson.sim.common.pool.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//TODO: make the class internal once decoupled from engine
public class SftpSessionFactory extends ConnectionFactory<SftpServerDetails, SftpConnection> {
    private static final Logger logger = LogManager.getLogger(SftpSessionFactory.class);

    @Override
    public SftpConnection makeObject(SftpServerDetails serverDetail) throws Exception {
        SftpConnection connection = new SftpConnection();
        logger.debug("Calling connect for: {}", serverDetail);
        connection.connect(serverDetail);
        if (!connection.isConnected()) {
            throw new Exception("Failed to create connection");
        }
        return connection;
    }
}
