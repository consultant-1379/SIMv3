package com.ericsson.sim.plugins.protocols;

import com.ericsson.sim.plugins.model.ProtocolException;
import com.ericsson.sim.plugins.model.ServerDetails;

import java.io.InputStream;
import java.io.OutputStream;

public interface Connection {

    /**
     * Try to connect using the server details provided
     *
     * @param serverDetails Connectivity serverDetails
     */
    void connect(ServerDetails serverDetails) throws ProtocolException;

    /**
     * Disconnect from server
     */
    void disconnect();

    /**
     * Check if connection with server is valid or not
     *
     * @return True if the connection is working, false otherwise
     */
    boolean isConnected();

    /**
     * Execute protocol and save output as file
     *
     * @param remotePath      Path to directory containing remote files
     * @param remoteFileName  Remote file name
     * @param localPath       Path to directory where remote files will be downloaded
     * @param localFileName   File name on local machine
     * @param setModifiedTime Update the modified time of file being transferred to match the remote file
     * @throws ProtocolException If an error occurred during execution
     */
    void download(String remotePath, String remoteFileName, String localPath, String localFileName, boolean setModifiedTime) throws ProtocolException;

    /**
     * Move remote file from one remote path to another
     *
     * @param fromPath Absolute remote path, including filename, from where the file will be moved from
     * @param toPath   Absolute remote path, including filename, to where the file will be moved to
     * @throws ProtocolException If an error occurred during execution
     */
    void move(String fromPath, String toPath) throws ProtocolException;

    /**
     * Execute delete operation on remote file on specified path
     *
     * @param remotePath     Path to directory containing remote files
     * @param remoteFileName Remote file name to be removed
     */
    void delete(String remotePath, String remoteFileName) throws ProtocolException;

    /**
     * Get input stream to underlying protocol for reading
     *
     * @return An instance of input stream
     * @throws ProtocolException If an error occurred during execution
     */
    InputStream read() throws ProtocolException;

    /**
     * Open the protocol for writing to destination
     *
     * @param outputStream An instance to output stream
     * @throws ProtocolException If an error occurred during execution
     */
    void write(OutputStream outputStream) throws ProtocolException;

    String getId();

    void setId(String id);

    //some instrumentation

    /**
     * Average time take by the download operation
     *
     * @return Time in milliseconds
     */
//    public float downloadThroughput();

    /**
     * Total download request for this connection
     * @return
     */
    int totalDownloadRequests();

    /**
     * Total time it took for all download requests through this connection
     * @return Time in milliseconds
     */
    long totalDownloadTime();

    /**
     * Total size downloaded by this connection
     * @return Size in bytes
     */
    long totalDownloadSize();
}
