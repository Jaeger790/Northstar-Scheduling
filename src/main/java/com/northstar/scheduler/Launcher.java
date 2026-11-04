package com.northstar.scheduler;

/** Keep the JVM entry point separate from the JavaFX Application class. */
public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) { SchedulerApp.main(args); }
}
