package com.mojang.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 1.20 LogUtils facade backed by the logger available in Cleanroom 1.12. */
public final class LogUtils {
    private LogUtils() {
    }

    public static Logger getLogger() {
        return LoggerFactory.getLogger("ApricityUI");
    }
}
