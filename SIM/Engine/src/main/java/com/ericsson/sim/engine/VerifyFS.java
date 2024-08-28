package com.ericsson.sim.engine;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.config.AppConfigReader;
import com.ericsson.sim.common.history.model.HistoryModel;
import com.ericsson.sim.common.util.CliUtil;
import com.ericsson.sim.common.util.HelpUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class VerifyFS {

    public static void main(String[] args) throws IOException {
//
//        FileStore fileStore = Files.getFileStore(Paths.get("/eniq/data/pmdata_sim/eniq_nokia_2/").toAbsolutePath());
//        System.out.println("Total: " + fileStore.getTotalSpace() / 1024);
//        System.out.println("Usable: " + fileStore.getUsableSpace() / 1024);
//        System.out.println("Free: " + (fileStore.getTotalSpace() - fileStore.getUsableSpace()) / 1024);
//        System.out.println("Unallocated: " + fileStore.getUnallocatedSpace() / 1024);
//
//        File file1 = new File("/eniq/data/pmdata_sim/eniq_nokia_2/");
//        System.out.println("\nTotal: " + file1.getTotalSpace() / 1024);
//        System.out.println("Usable: " + file1.getUsableSpace() / 1024);
//        System.out.println("Free: " + file1.getFreeSpace() / 1024);
//
//        System.exit(0);

        final String appHome = System.getProperty("app.home");
        if (appHome == null || appHome.isEmpty()) {
            CliUtil.fatal("Property app.home must be set to app install directory. Start the program with -Dapp.home=<path>. Program will now exit");
        }

        try {
            AppConfigReader reader = readAppConfig(getConfigPath(args));
            assert reader != null;
            reader.read();

            //read the ls output files from script
            HashMap<String, String> lsoutput = new HashMap<>();
            String[] lsfilepaths = {
                    "/var/ericsson/SIM/diff/vm14_nokiaNRBTS.list",
                    "/var/ericsson/SIM/diff/vm15_nokiaNRBTS.list",
                    "/var/ericsson/SIM/diff/vm14_nokiaMRBTS.list",
                    "/var/ericsson/SIM/diff/vm15_nokiaMRBTS.list"};

            for (String file : lsfilepaths) {
                List<String> lines = Files.readAllLines(Paths.get(file));
                lsoutput.putAll(lines.stream().collect(Collectors.toMap(l -> l, l -> l)));
            }

            //read json files
            HashMap<String, String> jsonoutput = new HashMap<>();
            String[] jsonfilepaths = {
                    "/var/ericsson/SIM/diff/vm14_nokiaNRBTS.json",
                    "/var/ericsson/SIM/diff/vm15_nokiaNRBTS.json",
                    "/var/ericsson/SIM/diff/vm14_nokiaMRBTS.json",
                    "/var/ericsson/SIM/diff/vm15_nokiaMRBTS.json"
            };

            for (String file : jsonfilepaths) {
                try (FileReader fileReader = new FileReader(file)) {
                    Gson gson = new GsonBuilder().create();
                    HistoryModel historyModel = gson.fromJson(fileReader, HistoryModel.class);
                    jsonoutput.putAll(historyModel.getFileNames().stream().collect(Collectors.toMap(e -> e, e -> e)));
                }
            }

            HashSet<String> jsonset = new HashSet<>(jsonoutput.keySet());
            jsonset.removeAll(lsoutput.keySet());

            HashSet<String> lsset = new HashSet<>(lsoutput.keySet());
            lsset.removeAll(jsonoutput.keySet());

            CliUtil.header("Files in JSON that are not from LS");
            CliUtil.print(String.join("\n", jsonset));

            CliUtil.header("Files in LS that are not from JSON");
            CliUtil.print(String.join("\n", lsset));

        } catch (Throwable t) {
            CliUtil.fatal("Error: ", t);
        }
    }


    private static AppConfigReader readAppConfig(String configPath) {
        AppConfigReader reader = new AppConfigReader(configPath);
        try {
            reader.read();
            if (!reader.verify()) {
                CliUtil.error("Config contains errors. ");
                return null;
            }
        } catch (IOException e) {
            CliUtil.error("Failed to read config.", e);
            return null;
        }
        return reader;
    }

    private static String getConfigPath(String[] args) {
        String configPath = HelpUtil.getArg(args, "--config", false);
        if (configPath == null || configPath.isEmpty()) {
            CliUtil.print("Config not provided through --config argument. Checking for config property (set as -Dconfig=<path>) when starting " + Constants.APP_NAME);
            configPath = System.getProperty("config");
            if (configPath == null || configPath.isEmpty()) {
                CliUtil.print("Config not provided through -Dconfig option. Trying default " + Constants.PATHS.APP_CONFIG);
                configPath = Constants.PATHS.APP_CONFIG;
            }
        } else {
            String[] split = configPath.split("=");
            if (split.length < 2) {
                CliUtil.warn("--config argument provided should be in format --config=<path>. We go " + configPath);
                configPath = System.getProperty("config");
            } else {
                configPath = split[1];
                CliUtil.print("Config path passed through argument is " + configPath);
            }
        }

        if (configPath == null || "".equals(configPath)) {
            CliUtil.error("Config path not provided in property or passed as argument.");
            return null;
        }
        return configPath;
    }

    private static void exit(int exitValue) {
        CliUtil.error("Exiting with code " + exitValue);
        System.exit(exitValue);
    }
}
