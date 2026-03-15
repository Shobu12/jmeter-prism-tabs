package com.prism.jmeter.tabs;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central logging for JMeter Tab Workspace. Uses java.util.logging so the plugin does not
 * depend on JMeter's logging. Reflection and compatibility failures are logged at FINE
 * so they can be enabled for diagnostics without spamming the console.
 */
public final class PrismTabsLog {

    private static final String LOGGER_NAME = "com.prism.jmeter.tabs";
    private static final Logger LOG = Logger.getLogger(LOGGER_NAME);

    private PrismTabsLog() {}

    /** Log a reflection or compatibility failure (fine level; enable FINE for diagnostics). */
    public static void reflectionFailure(String operation, Exception e) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Tab Workspace: " + operation, e);
        }
    }

    /** Log a critical reflection/state failure at WARNING so it is visible by default. */
    public static void reflectionCritical(String operation, Exception e) {
        LOG.log(Level.WARNING, "Tab Workspace: " + operation, e);
    }

    /** Log a warning (e.g. unexpected state). */
    public static void warn(String message) {
        LOG.warning("Tab Workspace: " + message);
    }

    /** Log an informational message. */
    public static void info(String message) {
        LOG.info("Tab Workspace: " + message);
    }
}
