package com.ericsson.sim.plugins.model;

import java.io.File;

public interface ServerDetails {

    enum AuthType {
        NONE,
        PASSWORD,
        KEYFILE,
    }

    String getHost();

    int getPort();

    String getUser();

    String getPassword();

    File getKeyFile();

    File getPrivateKey();

    AuthType getAuthType();

    int getRetries();

    default boolean isLocalHost() {
        String host = getHost();
        return host != null && (host.equals("localhost") || host.equals("127.0.0.1"));
    }
}