package com.prism.jmeter.tabs;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates tab lifecycle for the Prism tabbed interface: close single tab,
 * close others, close right, close all. Enforces max tabs and notifies listeners.
 */
public class PrismTabManager {

    private PrismTabContainer tabbedPane;
    private final List<PrismTabManagerListener> listeners = new ArrayList<>();

    public void setTabbedPane(PrismTabContainer pane) {
        this.tabbedPane = pane;
    }

    public PrismTabContainer getTabbedPane() {
        return tabbedPane;
    }

    public void addListener(PrismTabManagerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(PrismTabManagerListener listener) {
        listeners.remove(listener);
    }

    private void fireTabClosed(int index, PrismTabContext context) {
        for (PrismTabManagerListener l : new ArrayList<>(listeners)) {
            l.tabClosed(index, context);
        }
    }

    private void fireAllTabsClosed() {
        for (PrismTabManagerListener l : new ArrayList<>(listeners)) {
            l.allTabsClosed();
        }
    }

    /** Close the tab at the given index. */
    public void closeTab(int index) {
        if (tabbedPane == null || index < 0 || index >= tabbedPane.getTabCount()) {
            return;
        }
        PrismTabContext ctx = tabbedPane.getContextAt(index);
        tabbedPane.removeTabAt(index);
        fireTabClosed(index, ctx);
        ensureSelection();
    }

    /** Close all tabs except the one at the given index. */
    public void closeOthers(int index) {
        if (tabbedPane == null || index < 0 || index >= tabbedPane.getTabCount()) {
            return;
        }
        // Close from end toward start so indices remain valid
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            if (i != index) {
                PrismTabContext ctx = tabbedPane.getContextAt(i);
                tabbedPane.removeTabAt(i);
                fireTabClosed(i, ctx);
            }
        }
        tabbedPane.setSelectedIndex(0);
    }

    /** Close all tabs to the right of the given index (exclusive). */
    public void closeRight(int index) {
        if (tabbedPane == null || index < 0) {
            return;
        }
        int count = tabbedPane.getTabCount();
        for (int i = count - 1; i > index; i--) {
            PrismTabContext ctx = tabbedPane.getContextAt(i);
            tabbedPane.removeTabAt(i);
            fireTabClosed(i, ctx);
        }
        ensureSelection();
    }

    /** Close all tabs. */
    public void closeAll() {
        if (tabbedPane == null) {
            return;
        }
        while (tabbedPane.getTabCount() > 0) {
            PrismTabContext ctx = tabbedPane.getContextAt(0);
            tabbedPane.removeTabAt(0);
            fireTabClosed(0, ctx);
        }
        fireAllTabsClosed();
    }

    private void ensureSelection() {
        if (tabbedPane != null && tabbedPane.getTabCount() > 0) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    /** Listener for tab close events. */
    public interface PrismTabManagerListener {
        void tabClosed(int index, PrismTabContext context);
        void allTabsClosed();
    }
}
