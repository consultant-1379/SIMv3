package com.ericsson.sim.sftp.filter.model;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

public class TimeFilterConfig {

    private static final ConcurrentHashMap<String, DateTimeFormatter> patternCache = new ConcurrentHashMap<>();

    public final int ropDateGroupId;
    public final String ropDatePattern;

    public final int ropTimeGroupId;
    public final String ropTimePattern;

    public final DateTimeFormatter dateTimeFormatter;

    private static final String concatenator = "-";

    public TimeFilterConfig(int ropDateGroupId, int ropTimeGroupId, String ropDatePattern, String ropTimePattern) {

        this.ropDateGroupId = ropDateGroupId;
        this.ropDatePattern = ropDatePattern;

        this.ropTimeGroupId = ropTimeGroupId;
        this.ropTimePattern = ropTimePattern;

        final String dateTimePattern = getDateTime(ropDatePattern, ropTimePattern);
        dateTimeFormatter = patternCache.computeIfAbsent(dateTimePattern, DateTimeFormatter::ofPattern);
    }

    public String getDateTime(String datePart, String timePart) {
        return datePart + concatenator + timePart;
    }
}