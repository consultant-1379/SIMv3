package com.ericsson.sim.plugins.protocols;

import com.ericsson.sim.plugins.model.Config;
import com.ericsson.sim.plugins.model.ProtocolException;

public interface ConnectionFactory {
    Connection getConnection(Config config) throws ProtocolException;
}
