package com.ericsson.sim.sftp.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.function.Function;

public final class GenerateCsvOutputStream extends CsvOutputStream {

    private static final Logger logger = LogManager.getLogger(GenerateCsvOutputStream.class);

    private static class OutputFileInfo {
        boolean closeStream = false;
        boolean headerWritten = false;

        String header = null;
        String filepath = null;
        String currentFilename = null;
        FileOutputStream fileOutputStream = null;
    }

    private final String csvDelimiter;
    private final Function<LocalDateTime, String> filenameEval;
    private final DateTimeFormatter csvDateHeaderFormat;
    private final int csvDateHeaderIndex;
    private final OutputFileInfo outputFileInfo;
    private final boolean hasHeader;

    private final HashMap<String, File> generatedFiles = new HashMap<>();

    /**
     * Generates outputs as required based on date and rop times. Once enough information is read from header and lines
     * the original {@link OutputStream} is ignored and new files are generated as required.
     * Caller is responsible for closing the original {@link OutputStream}
     *
     * @param out                 Original file output stream
     * @param header              Save header if there is any, null otherwise. In case of null, we will try to read header
     * @param filenameEval        A function to provide file name
     * @param filepath            Local file path
     * @param initialFilename     Initial file name
     * @param lineDelimiter       Line delimiter in CSV file
     * @param csvDelimiter        CSV separator
     * @param csvDateHeaderFormat Date header formatter
     * @param csvDateHeaderIndex  0 based column containing date information
     * @param hasHeader           If remote file will have any header entry to look for
     */
    public GenerateCsvOutputStream(FileOutputStream out, String header, Function<LocalDateTime, String> filenameEval, String filepath, String initialFilename,
                                   String lineDelimiter, String csvDelimiter, DateTimeFormatter csvDateHeaderFormat, int csvDateHeaderIndex, boolean hasHeader) {
        super(out, lineDelimiter, header);
        this.csvDelimiter = csvDelimiter;
        this.filenameEval = filenameEval;
        this.csvDateHeaderFormat = csvDateHeaderFormat;
        this.csvDateHeaderIndex = csvDateHeaderIndex;
        this.hasHeader = hasHeader;

        this.outputFileInfo = new OutputFileInfo();
        this.outputFileInfo.header = header;
        this.outputFileInfo.fileOutputStream = out;
        this.outputFileInfo.filepath = filepath;
        this.outputFileInfo.currentFilename = initialFilename;
    }

    @Override
    protected void _write(byte[] b, int off, int len, int indexAt, boolean addToCount) throws IOException {

        //if we can any index, we cannot do much as the information is not
        //reliable. Just write it to current handler
        if (indexAt < 0) {
            logger.debug("Delimiter index was not found, writing all bytes as it is to {}", this.outputFileInfo.currentFilename);
            super._write(b, off, len, indexAt, true);
            return;
        }

        String header = getHeader();

        //if we don't have any headers, try read from first line and save it
        if (header == null && hasHeader) {
            //header is not read yet
            processHeader(b, off, len);
            header = getHeader();
            if (header == null) {
                //something went wrong it seems, or we don't have any header yet. we will continue writing to
                // current stream as it is
                logger.warn("Unable to read header, we will write to current file {}", this.outputFileInfo.currentFilename);
                super._write(b, off, len, indexAt, true);
            }

            //save the header for now, don't write
            outputFileInfo.header = header;
        } else {
            //it's not header, treat as normal line
            String str = new String(b, off, len);
            //TODO: change to trace
            logger.debug("Checking line: {}", str);

            if (str.isEmpty() || str.length() == finishDelimiter.length) {
                // seems like just the delimiter, write it as it is
                logger.debug("Looks like the line is either empty or just the delimiter, writing it as it is");
                super._write(b, off, len, indexAt, true);

            } else {

                logger.debug("Using {} to split line {}", csvDelimiter, str);
                String[] split = str.split(csvDelimiter);

                //either configuration is wrong or we didn't get the information we wanted
                if (csvDateHeaderIndex >= split.length) {
                    logger.warn("Splitting csv line with '{}' yielded {} columns which is less then provided date header index of {}. Check configuration!", csvDelimiter, split.length, csvDateHeaderIndex);
                    logger.warn("No further processing can be done, we will write to current file {}", this.outputFileInfo.currentFilename);
                    super._write(b, off, len, indexAt, true);
                } else {
                    //we have date information, check with evaluator and get the file name. See if its something different.
                    //If so, create new file and save its information
                    String csvDateStr = split[csvDateHeaderIndex];
                    LocalDateTime csvDate;
                    logger.debug("Identified date from column {} is {}. Parsing it", csvDateHeaderIndex, csvDateStr);
                    try {
                        //TODO: if we save the ROP start and ROP end times, we can do a local
                        // check rather then evaluating name everytime
                        logger.debug("Trying to parse date {} from file", csvDateStr);
                        csvDate = LocalDateTime.parse(csvDateStr, csvDateHeaderFormat);
                        String evaluatedFilename = filenameEval.apply(csvDate);

                        logger.debug("Evaluated filename is {}, current filename is {}", evaluatedFilename, this.outputFileInfo.currentFilename);

                        if (!evaluatedFilename.equals(this.outputFileInfo.currentFilename)) {
                            //different file name now
                            logger.debug("New filename {}. Closing old stream and replacing with new", evaluatedFilename);
                            if (this.outputFileInfo.closeStream) {
                                try {
                                    this.outputFileInfo.fileOutputStream.close();
                                } catch (IOException ignored) {
                                }
                            }

                            logger.debug("Creating new file {}", evaluatedFilename);
                            File newFile = Paths.get(this.outputFileInfo.filepath, evaluatedFilename).toFile();
                            this.outputFileInfo.fileOutputStream = new FileOutputStream(newFile, true);
                            this.outputFileInfo.currentFilename = evaluatedFilename;

                            this.outputFileInfo.headerWritten = false; //headers not written for new file
                            this.outputFileInfo.closeStream = true; //our created streams will be closed

                            this.generatedFiles.put(evaluatedFilename, newFile);
                        }

                        if (!this.outputFileInfo.headerWritten && hasHeader) {
                            writeHeader(addHeaderBytesToCount());
                        }

                        //write the remaining line
                        super._write(b, off, len, indexAt, true);

                    } catch (DateTimeParseException e) {
                        logger.error("Failed to parse string date {} from header. {}", csvDateStr, e.getMessage());
                        logger.error("No further processing can be done, we will write to current file {}", this.outputFileInfo.currentFilename);

                        super._write(b, off, len, indexAt, true);
                    }
                }
            }
        }
    }

    private void writeHeader(boolean addToCount) throws IOException {
        //write header
        byte[] headerBuffer = this.outputFileInfo.header.getBytes();
        super._write(headerBuffer, 0, headerBuffer.length, headerBuffer.length, addToCount);
        this.outputFileInfo.headerWritten = true;
    }

    /**
     * Get set of filenames  that are generated. The set contains only the filename
     *
     * @return Set of generated filename
     */
    public HashMap<String, File> getGeneratedFiles() {
        return generatedFiles;
    }

    @Override
    protected OutputStream getInner() {
        return this.outputFileInfo.fileOutputStream;
    }
}
