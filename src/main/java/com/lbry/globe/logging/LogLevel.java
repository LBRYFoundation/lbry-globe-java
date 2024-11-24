package com.lbry.globe.logging;

import java.util.logging.Level;

public class LogLevel extends Level {

    public static final Level FATAL = new LogLevel("FATAL",5000);

    public static final Level ERROR = new LogLevel("ERROR",1000);

    public static final Level DEBUG = new LogLevel("DEBUG",800);

    protected LogLevel(String name, int value) {
        super(name, value);
    }

    protected LogLevel(String name, int value, String resourceBundleName) {
        super(name, value, resourceBundleName);
    }

}
