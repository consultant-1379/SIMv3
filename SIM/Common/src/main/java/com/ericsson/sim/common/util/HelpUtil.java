package com.ericsson.sim.common.util;

import org.apache.logging.log4j.core.util.Throwables;

import java.util.PrimitiveIterator;
import java.util.concurrent.TimeUnit;

public class HelpUtil {

    public static String getArg(String[] args, String matching, boolean fullMatch) {
        for (String arg : args) {
            if (fullMatch && arg.equals(matching)) {
                return arg;
            } else if (arg.startsWith(matching)) {
                return arg;
            }
        }
        return null;
    }

    public static boolean hasArg(String[] args, String find) {
        String arg = getArg(args, find, true);
        return arg != null;
    }

    /**
     * Start precision timer.
     *
     * @return Time in nanoseconds
     */
    public static long startTimer() {
        return System.nanoTime();
    }

    /**
     * End timer and calculate difference
     *
     * @param startTimeNano Start timer in nanoseconds
     * @return Timer end value in millisecond
     */
    public static long endTimerMilli(long startTimeNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNano);
    }

    /**
     * Replace all occurrences of {@code find} from source {@code source} with {@code replacement}
     *
     * @param source      Source string. A null or empty source will result is same return value
     * @param find        String to find in {@code source} for replacement
     * @param replacement Replacement string if {@code find} is found
     * @return Update string
     */
    public static String replaceAll(final String source, final String find, final String replacement) {
        if (isNullOrEmpty(source)) {
            return source;
        }
        if (isNullOrEmpty(find)) {
            return source;
        }

        StringBuilder builder = new StringBuilder();
        int index = source.indexOf(find);
        int prev = 0;
        while (index > -1) {
            builder.append(source, prev, index).append(replacement);
            prev = index + find.length();
            index = source.indexOf(find, prev);
        }

        if (prev < source.length()) {
            builder.append(source, prev, source.length());
        }

        return builder.toString();
    }

    public static String replaceFirst(final String source, final String find, final String replacement) {
        if (isNullOrEmpty(source)) {
            return source;
        }
        if (isNullOrEmpty(find)) {
            return source;
        }

        int pos = source.indexOf(find);
        if (pos > -1) {
            return replacement + source.substring(pos);
        }

        return source;
    }

    /**
     * Replace last occurrence of {@code find} from source {@code source} with {@code replacement}
     *
     * @param source      Source string. A null or empty source will result is same return value
     * @param find        String to find in {@code source} for replacement
     * @param replacement Replacement string if {@code find} is found
     * @return Updated string
     */
    public static String replaceLast(final String source, final String find, final String replacement) {
        if (isNullOrEmpty(source)) {
            return source;
        }
        if (isNullOrEmpty(find)) {
            return source;
        }

        int pos = source.lastIndexOf(find);
        if (pos > -1) {
            return source.substring(0, pos) + replacement;
        }

        return source;
    }

    /**
     * Create a unique hashcode considering time values
     *
     * @return Created hashcode
     */
    public static String timeHashCode() {
        return timeHashCode("");
    }

    /**
     * Creates hashcode from the seed provided and time values
     *
     * @param seed Start point to create hash code from
     * @return Created hashcode
     */
    public static String timeHashCode(String seed) {
//        long uuid = UUID.randomUUID().hashCode();
//        int uuid = (UUID.randomUUID().toString() + System.currentTimeMillis() + "-" + System.nanoTime()).hashCode();
//        if (uuid < 0) {
//            uuid = uuid * -1;
//        }

//        try {
//            MessageDigest md = MessageDigest.getInstance("MD5");
//            byte[] digest = md.digest((UUID.randomUUID().toString() + System.currentTimeMillis() + "-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
//            BigInteger bigInteger = new BigInteger(1, digest);
//            return bigInteger.toString(10);
//
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//            return "";
//        }

//
//        int uuid = (seed + System.currentTimeMillis() + "-" + System.nanoTime()).hashCode();
//        if (uuid < 0) {
//            uuid = uuid * -1;
//        }
//        int uuid = Objects.hash(seed, System.currentTimeMillis(), "-" + System.nanoTime());
        final int prime = 31;
        final long currentTimeMilli = System.currentTimeMillis();
        final long currentTimeNano = System.nanoTime();

        int uuid = 1;

        uuid = prime * uuid + ((seed == null) ? 0 : seed.hashCode());
        uuid = prime * uuid + (int) (currentTimeMilli ^ (currentTimeMilli >>> 32));
        uuid = prime * uuid + (int) (currentTimeNano ^ (currentTimeNano >>> 32));

        if (uuid < 0) {
            uuid = uuid * -1;
        }
        return String.format("%10s", uuid).replace(' ', '0');
    }

    public static int captureGroupCount(String regex) {
        int groups = 0;

        if (regex == null) {
            return groups;
        }

        //captured groups are within (). however (? is non-capturing group
        PrimitiveIterator.OfInt iterator = regex.chars().iterator();
        int prev = -1;
        while (iterator.hasNext()) {
            int c = iterator.nextInt();
            if (prev != 92) { // '\'
                if (c == 40 && iterator.hasNext()) { // '('
                    c = iterator.nextInt();
                    if (c != 63) { // '?'
                        groups++;
                    }
                }
            }
            prev = c;
        }

        return groups;
    }

    /**
     * Checks if a string value is valid integer or not
     *
     * @param value String representation of possible integer
     * @return True if the value can be parsed to integer, false otherwise
     */
    public static boolean isInteger(String value) {
//        Objects.requireNonNull(value);
//        return value.matches("\\d+");
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    /**
     * Check if supplied string is null or empty
     *
     * @param str String to check
     * @return True if string is either null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNullOrEmpty(char[] str) {
        return str == null || str.length == 0;
    }

    public static Throwable getCause(Throwable t) {
        return Throwables.getRootCause(t);
    }
}
