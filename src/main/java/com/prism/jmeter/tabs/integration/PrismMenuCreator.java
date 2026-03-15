package com.prism.jmeter.tabs.integration;

import com.prism.jmeter.tabs.PrismTabContext;
import com.prism.jmeter.tabs.PrismTabManager;
import com.prism.jmeter.tabs.PrismTabPanel;
import com.prism.jmeter.tabs.PrismTabsLog;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.plugin.MenuCreator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Adds "Open in new tab" and "New tab" to JMeter's File menu so the Prism tab UI
 * is used and files can be opened in separate tabs.
 * <p>
 * Menu order: JMeter assembles the File menu and inserts plugin items (from all
 * MenuCreator implementations) in plugin load order. These items are intended to
 * appear after "Open Recent"; the exact position is controlled by JMeter's menu assembly.
 * </p>
 */
public class PrismMenuCreator implements MenuCreator {

    private static final String OPEN_IN_NEW_TAB = "Open in new tab";
    private static final String NEW_TAB = "New tab";

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.FILE) {
            return new JMenuItem[0];
        }
        // Order: Open in new tab first, then New tab (shown after Open Recent when JMeter allows).
        JMenuItem openInNewTab = new JMenuItem(OPEN_IN_NEW_TAB);
        openInNewTab.addActionListener(this::openInNewTab);
        JMenuItem newTab = new JMenuItem(NEW_TAB);
        newTab.addActionListener(this::newTab);
        return new JMenuItem[]{openInNewTab, newTab};
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public void localeChanged() {
        // no-op
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    private void openInNewTab(ActionEvent e) {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            return;
        }
        Object mainFrame = getMainFrame(guiPackage);
        if (!(mainFrame instanceof Window)) {
            return;
        }
        ensurePrismInstalled(guiPackage, (Window) mainFrame);
        PrismTabPanel tabPanel = PrismIntegrationHolder.getTabPanel();
        if (tabPanel == null || !tabPanel.canOpenNewTab()) {
            JOptionPane.showMessageDialog((Window) mainFrame,
                    "Cannot add more tabs.",
                    "Tab Workspace",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open test plan in new tab");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JMeter test plan (.jmx)", "jmx"));
        if (chooser.showOpenDialog((Window) mainFrame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null || !file.isFile()) {
            return;
        }
        tabPanel.captureCurrentTab();
        PrismTabContext context = new PrismTabContext(UUID.randomUUID().toString());
        context.setFile(file);
        context.setFileToLoad(file);
        int idx = tabPanel.addTab(context, true);
        if (idx >= 0) {
            tabPanel.updateTabTitleFromFile(idx, file);
        }
    }

    private void newTab(ActionEvent e) {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            return;
        }
        Object mainFrame = getMainFrame(guiPackage);
        if (!(mainFrame instanceof Window)) {
            return;
        }
        ensurePrismInstalled(guiPackage, (Window) mainFrame);
        PrismTabPanel tabPanel = PrismIntegrationHolder.getTabPanel();
        if (tabPanel == null || !tabPanel.canOpenNewTab()) {
            JOptionPane.showMessageDialog((Window) mainFrame,
                    "Cannot add more tabs.",
                    "Tab Workspace",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        tabPanel.captureCurrentTab();
        PrismTabContext context = new PrismTabContext(UUID.randomUUID().toString());
        int idx = tabPanel.addTab(context, true);
        if (idx >= 0) {
            tabPanel.updateTabTitle(idx);
        }
    }

    private void ensurePrismInstalled(GuiPackage guiPackage, Window mainFrame) {
        if (PrismIntegrationHolder.getTabPanel() != null) {
            return;
        }
        Component content = findMainContentPanel(guiPackage, mainFrame);
        if (content == null) {
            PrismTabsLog.warn("Tab Workspace: could not find main content panel; tabs not installed.");
            return;
        }
        Container parent = content.getParent();
        if (parent == null) {
            PrismTabsLog.warn("Tab Workspace: content has no parent; tabs not installed.");
            return;
        }
        Object constraints = getLayoutConstraints(parent, content);
        PrismTabManager manager = new PrismTabManager();
        PrismTabPanel tabPanel = new PrismTabPanel(manager);
        tabPanel.setContent(content);
        PrismTabContext initialContext = new PrismTabContext(UUID.randomUUID().toString());
        tabPanel.addTab(initialContext);
        manager.setTabbedPane(tabPanel);
        parent.remove(content);
        parent.add(tabPanel, constraints != null ? constraints : BorderLayout.CENTER);
        parent.revalidate();
        parent.repaint();
        PrismIntegrationHolder.set(tabPanel);
        tabPanel.startTitleUpdateTimer();
    }

    /**
     * Find the main content panel by walking from the JMeter tree up to a direct child of the frame's content pane.
     */
    private static Component findMainContentPanel(GuiPackage guiPackage, Window frame) {
        Component tree = getTreeFromGuiPackage(guiPackage);
        if (tree == null || !(frame instanceof JFrame)) {
            return getMainPanelViaReflection(frame);
        }
        Container contentPane = ((JFrame) frame).getContentPane();
        if (contentPane == null) {
            return getMainPanelViaReflection(frame);
        }
        Component p = tree;
        while (p != null && p.getParent() != contentPane) {
            p = p.getParent();
        }
        if (p != null) {
            return p;
        }
        return getMainPanelViaReflection(frame);
    }

    private static Component getTreeFromGuiPackage(GuiPackage guiPackage) {
        try {
            Object listener = guiPackage.getTreeListener();
            if (listener == null) return null;
            Method getTree = listener.getClass().getMethod("getJTree");
            Object tree = getTree.invoke(listener);
            return tree instanceof Component ? (Component) tree : null;
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("getTreeFromGuiPackage", e);
            return null;
        }
    }

    private static Object getLayoutConstraints(Container parent, Component child) {
        LayoutManager layout = parent.getLayout();
        if (layout instanceof BorderLayout) {
            BorderLayout bl = (BorderLayout) layout;
            if (bl.getLayoutComponent(parent, BorderLayout.CENTER) == child) return BorderLayout.CENTER;
            if (bl.getLayoutComponent(parent, BorderLayout.NORTH) == child) return BorderLayout.NORTH;
            if (bl.getLayoutComponent(parent, BorderLayout.SOUTH) == child) return BorderLayout.SOUTH;
            if (bl.getLayoutComponent(parent, BorderLayout.EAST) == child) return BorderLayout.EAST;
            if (bl.getLayoutComponent(parent, BorderLayout.WEST) == child) return BorderLayout.WEST;
            return BorderLayout.CENTER;
        }
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == child) return i;
        }
        return BorderLayout.CENTER;
    }

    private static Object getMainFrame(GuiPackage guiPackage) {
        try {
            Method m = GuiPackage.class.getMethod("getMainFrame");
            return m.invoke(guiPackage);
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("getMainFrame", e);
            return null;
        }
    }

    private static Component getMainPanelViaReflection(Window frame) {
        try {
            Method m = frame.getClass().getMethod("getMainPanel");
            Object panel = m.invoke(frame);
            return panel instanceof Component ? (Component) panel : null;
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("getMainPanel", e);
            if (frame instanceof JFrame) {
                Container cp = ((JFrame) frame).getContentPane();
                if (cp != null && cp.getComponentCount() > 0) {
                    return cp.getComponent(0);
                }
            }
            return null;
        }
    }
}
