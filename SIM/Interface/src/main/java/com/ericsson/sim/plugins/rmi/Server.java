package com.ericsson.sim.plugins.rmi;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface Server extends Remote {

    enum AddNodeResult implements Remote {
        ALREADY_EXISTS,
        ADDED,
        FAILURE,
        INVALID_PATH
    }

    enum UpdateConfigResult implements Remote {
        NO_UPDATE,
        INVALID,
        UPDATED
    }

    List<JobStatus> listJobStatus() throws RuntimeException, RemoteException;

    AddNodeResult addNodeType(String jarPath) throws RuntimeException, IOException;

    UpdateConfigResult updateConfig() throws RuntimeException, IOException;

    default List<String> getInstrumentation() throws RemoteException {
        return null;
    }

    default List<String> getExecutionDetails() throws RemoteException {
        return null;
    }
}
