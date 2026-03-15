package com.prism.jmeter.tabs;

import java.awt.event.ActionEvent;

/**
 * Triggers JMeter run actions (Start, Stop) via ActionRouter.
 * Use when the active tab's GuiPackage is already set so the correct test plan runs.
 */
public final class PrismRunActions {

    private PrismRunActions() {}

    /** Action command for Start. */
    public static final String ACTION_START = "start";
    /** Action command for Stop. */
    public static final String ACTION_STOP = "stop";

    private static String actionName(String constantName, String defaultVal) {
        try {
            Class<?> actionNames = Class.forName("org.apache.jmeter.gui.action.ActionNames");
            java.lang.reflect.Field f = actionNames.getField(constantName);
            return (String) f.get(null);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * Trigger a JMeter run action. Call after the tab's GuiPackage is the current singleton.
     */
    public static void doRunAction(String actionCommand) {
        if (actionCommand == null || actionCommand.isEmpty()) return;
        try {
            String cmd = actionCommand;
            if (ACTION_START.equals(actionCommand)) cmd = actionName("ACTION_START", ACTION_START);
            else if (ACTION_STOP.equals(actionCommand)) cmd = actionName("ACTION_STOP", ACTION_STOP);

            Object router = Class.forName("org.apache.jmeter.gui.action.ActionRouter").getMethod("getInstance").invoke(null);
            if (router == null) return;
            ActionEvent evt = new ActionEvent(router, ActionEvent.ACTION_PERFORMED, cmd);
            router.getClass().getMethod("actionPerformed", ActionEvent.class).invoke(router, evt);
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("doRunAction " + actionCommand, e);
        }
    }
}
