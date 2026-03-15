package com.prism.jmeter.tabs;

/**
 * Common interface for a container that holds multiple tab contexts (e.g. tab bar + content).
 * Used by PrismTabManager for close operations.
 */
public interface PrismTabContainer {

    int getTabCount();

    PrismTabContext getContextAt(int index);

    void removeTabAt(int index);

    void setSelectedIndex(int index);
}
