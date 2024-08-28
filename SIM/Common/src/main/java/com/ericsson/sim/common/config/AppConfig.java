package com.ericsson.sim.common.config;

public class AppConfig {
    Paths paths = new Paths();
    ThreadPool threadPool = new ThreadPool();
    Rmi rmi = new Rmi();
    Scheduler scheduler = new Scheduler();
    Jobs jobs = new Jobs();
    Patterns patterns = new Patterns();
    NeConfig neConfig = new NeConfig();

    public Paths getPaths() {
        return paths;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public Rmi getRmi() {
        return rmi;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Jobs getJobs() {
        return jobs;
    }

    public Patterns getPatterns() {
        return patterns;
    }

    public NeConfig getNeConfig() {
        return neConfig;
    }
}
