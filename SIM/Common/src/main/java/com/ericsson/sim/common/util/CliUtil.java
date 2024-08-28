package com.ericsson.sim.common.util;

public class CliUtil {

    private static final String NRL = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";


    public static void print(String str) {
        System.out.println(str);
    }

    public static String input(String str) {
        System.out.print(str + ": ");
        return System.console().readLine();
    }

    public static char[] inputPass(String str) {
        System.out.print(str + ": ");
        return System.console().readPassword();
    }

    public static void print(String str, Throwable t) {
        print(str);
        t.printStackTrace();
    }

    public static void success(String str) {
        print(formatGreen(str));
    }

    public static void warn(String str) {
        print(formatOrange(str));
    }

    public static void error(String str) {
        print(formatRed(str));
    }

    public static void error(String str, Throwable t) {
        print(formatRed(str), t);
    }

    public static void fatal(String str) {
        error(str);
        System.exit(1);
    }

    public static void fatal(String str, Throwable t) {
        error(str, t);
        System.exit(1);
    }

    public static void header(String str) {
        print("");
        print("--------- " + formatBlue(str) + " ---------");
    }

    public static void endHeader() {
        print("---------------------------");
    }

    public static String formatRed(String str) {
        return RED + str + NRL;
    }

    public static String formatGreen(String str) {
        return GREEN + str + NRL;
    }

    public static String formatBlue(String str) {
        return BLUE + str + NRL;
    }

    public static String formatOrange(String str) {
        return YELLOW + str + NRL;
    }
}
