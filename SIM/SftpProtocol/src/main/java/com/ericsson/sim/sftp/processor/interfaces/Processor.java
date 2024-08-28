package com.ericsson.sim.sftp.processor.interfaces;

public interface Processor<T, R, S> {
    R process(T src, T dst, S s);
}
