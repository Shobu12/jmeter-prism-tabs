package com.prism.jmeter.tabs.integration;

import com.prism.jmeter.tabs.PrismTabPanel;

/**
 * Holds the Prism tab panel (tab bar + single content) after integration is installed.
 */
final class PrismIntegrationHolder {

    private static PrismTabPanel tabPanel;

    static PrismTabPanel getTabPanel() {
        return tabPanel;
    }

    static void set(PrismTabPanel panel) {
        tabPanel = panel;
    }
}
