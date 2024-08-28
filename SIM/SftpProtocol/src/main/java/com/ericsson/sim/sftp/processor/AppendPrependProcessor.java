package com.ericsson.sim.sftp.processor;

import com.ericsson.sim.sftp.processor.interfaces.FilenameProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;

public class AppendPrependProcessor implements FilenameProcessor {

    private static final Logger logger = LogManager.getLogger(AppendPrependProcessor.class);

    //TODO: It may all be good but the best is to also get the file extension expression from config and use them before checking known extension
    final String[] knownExtensions = {".xml.zip", ".xml.7z", ".xml.gz", ".xml.tar", ".xml.tar.gz", ".xml.tar.7z"
            , ".csv.zip", ".csv.7z", ".csv.gz", ".csv.tar", ".csv.tar.gz", ".csv.tar.7z"
            , ".asn1.zip", ".asn1.7z", ".asn1.gz", ".asn1.tar", ".asn1.tar.gz", ".asn1.tar.7z"
            , ".xml", ".csv", ".asn1", ".zip", ".7z", ".gz", ".tar", ".tar.gz"};

    private final String id;
    private final boolean prepend;
    private final boolean append;

    public AppendPrependProcessor(String jobId, boolean prepend, boolean append) {
        this.id = jobId;
        this.prepend = prepend;
        this.append = append;
    }

    public String process(String src, String dst, Matcher matcher) {

        if (!append && !prepend) {
            //nothing to process
            logger.debug("Append and Prepend is set to false. No filename processing required. Returning {}", dst);
            return dst;
        }

        if (dst == null) {
            logger.info("Filename passed is null, no file renaming operation will be done");
            return null;
        }

        if (id == null) {
            logger.debug("Id for file renaming is null. Would not be able to prepend or append id");
            return dst;
        }

        if (prepend) {
            if (logger.isDebugEnabled()) {
                logger.debug("Prepending {} to file {}", id, dst);
            }
            dst = id + "_" + dst;
        }

        if (append) {
            if (logger.isDebugEnabled()) {
                logger.debug("Appending {} to file {}", id, dst);
            }
            final String name = dst;
            Optional<String> result = Arrays.stream(knownExtensions).filter(name::endsWith).findFirst();
            if (result.isPresent()) {
                //we have a match
                dst = dst.substring(0, dst.lastIndexOf(result.get())) + "_" + id + result.get();
            } else {
                //we take the approach of first . to not mess with file extensions combinations unknown.
                String[] split = dst.split("\\.", 2);
                if (split.length == 1) {
                    dst = dst + "_" + id;
                } else {
                    dst = split[0] + "_" + id + "." + split[1];
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("New file name now is {}", dst);
        }
        return dst;
    }

    @Override
    public String getProcessorName() {
        return "AppendPrependProcessor";
    }
}
