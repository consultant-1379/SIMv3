package com.ericsson.sim.sftp.filter.model;

import com.ericsson.sim.sftp.filter.FileFilter;
import com.jcraft.jsch.SftpATTRS;

import java.util.regex.Matcher;

public class DefaultFilter implements FileFilter {
    @Override
    public boolean accept(Matcher filenameMatcher, SftpATTRS sftpAttr) {
        return true;
    }
}
