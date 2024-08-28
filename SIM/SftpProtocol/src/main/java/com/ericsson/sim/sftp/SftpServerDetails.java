package com.ericsson.sim.sftp;

import com.ericsson.sim.plugins.model.ServerDetails;

import java.io.File;

//TODO: make the class internal once decoupled from engine
public class SftpServerDetails implements ServerDetails {

    private final String host;
    private final int port;
    private final String username;
    private final AuthType authType;
    private final String password;
    private final File keyFile;
    private final File privateKey;
    private final int retries;

    public SftpServerDetails(String host, int port, String username, String password, int retries) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.keyFile = null;
        this.privateKey = null;
        this.retries = retries;
        this.authType = AuthType.PASSWORD;
    }

    public SftpServerDetails(String host, int port, String username, File keyFile, int retries) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = null;
        this.keyFile = keyFile;
        this.privateKey = null;
        this.retries = retries;
        this.authType = AuthType.KEYFILE;
    }

    public SftpServerDetails(File privateKey, String host, int port, String username, int retries) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = null;
        this.keyFile = null;
        this.privateKey = privateKey;
        this.retries = retries;
        this.authType = AuthType.NONE;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public String getUser() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public File getKeyFile() {
        return this.keyFile;
    }

    @Override
    public File getPrivateKey() {
        return this.privateKey;
    }

    @Override
    public int getRetries() {
        return retries;
    }

    @Override
    public AuthType getAuthType() {
        return this.authType;
    }

    @Override
    public String toString() {
        return "SftpServerDetails{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", authType=" + authType +
                (authType == AuthType.KEYFILE ? ", keyFile=" + keyFile : "") +
                ", retries=" + retries +
                '}';
    }
}
