package com.prism.jmeter.tabs;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.gui.tree.JMeterTreeModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab bar + single content area. Each "tab" is a header; the same content component
 * is shown for all tabs and we swap the tree model when switching. Prevents the
 * "new tab steals content" issue when using one shared JMeter tree panel.
 */
public class PrismTabPanel extends JPanel implements PrismTabContainer {

    private static final int TAB_ROW_HEIGHT = 26;

    private final List<PrismTabContext> tabContexts = new ArrayList<>();
    private final JPanel tabBar;
    private final PrismTabManager manager;
    private Component content;
    private int selectedIndex = -1;
    private int lastSelectedIndex = -1;
    private javax.swing.Timer titleUpdateTimer;

    public PrismTabPanel(PrismTabManager manager) {
        super(new BorderLayout());
        this.manager = manager;
        this.tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.tabBar.setPreferredSize(new Dimension(0, TAB_ROW_HEIGHT));
        add(tabBar, BorderLayout.NORTH);
    }

    /** Start periodic tab title updates (prism-oss style). Runs every 2s to limit CPU use. */
    public void startTitleUpdateTimer() {
        if (titleUpdateTimer != null) return;
        titleUpdateTimer = new javax.swing.Timer(2000, e -> {
            if (selectedIndex >= 0 && selectedIndex < tabContexts.size()) {
                updateTabTitleFromCurrentPlan(selectedIndex);
            }
        });
        titleUpdateTimer.start();
    }

    /** Set the single content component (call once before or when adding the first tab). */
    public void setContent(Component content) {
        if (this.content != null && this.content.getParent() == this) {
            remove(this.content);
        }
        this.content = content;
        if (content != null) {
            add(content, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    /** Add a new tab (context only; content is the single shared component). Selects it by default. */
    public int addTab(PrismTabContext context) {
        return addTab(context, true);
    }

    /**
     * Add a new tab, optionally selecting it. When select is false, the current tab stays active
     * and the new tab's workspace is not activated until later (e.g. after loading a file).
     */
    public int addTab(PrismTabContext context, boolean select) {
        if (tabContexts.size() >= PrismTabConstants.MAX_TABS) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "More than 10 Tab is not allowed in current window",
                    "Tab limit",
                    JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        if (content == null) {
            return -1;
        }
        int idx = tabContexts.size();
        tabContexts.add(context);
        JPanel tab = makeTabButton(idx, context.getDisplayName(), context.getFullNameForTooltip());
        tabBar.add(tab);
        tabBar.revalidate();
        tabBar.repaint();
        if (select) {
            selectTab(idx);
        }
        lastSelectedIndex = selectedIndex;
        return idx;
    }

    /** Capture the currently selected tab's workspace (tree state) from the GUI. Call before loading a new file. */
    public void captureCurrentTab() {
        if (selectedIndex >= 0 && selectedIndex < tabContexts.size()) {
            tabContexts.get(selectedIndex).captureFromGui();
        }
    }

    /** Index of the currently selected tab, or -1 if none. */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    private JPanel makeTabButton(int index, String title, String tooltip) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setOpaque(true);
        panel.setBackground(UIManager.getColor("TabbedPane.background"));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 6, 2, 2));
        panel.setMaximumSize(new Dimension(Short.MAX_VALUE, TAB_ROW_HEIGHT));
        JLabel label = new JLabel(title);
        label.setToolTipText(tooltip);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setForeground(UIManager.getColor("TabbedPane.foreground") != null
                ? UIManager.getColor("TabbedPane.foreground")
                : UIManager.getColor("Label.foreground"));
        JButton close = new JButton("\u00D7");
        close.setMargin(new Insets(0, 2, 0, 2));
        close.setFocusPainted(false);
        close.setToolTipText("Close");
        MouseAdapter clickAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = indexOfTabPanel(panel);
                if (idx < 0) return;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectTab(idx);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showTabContextMenu(e.getX(), e.getY(), idx, panel);
                }
            }
        };
        label.addMouseListener(clickAdapter);
        close.addActionListener(e -> {
            int idx = indexOfTabPanel(panel);
            if (idx >= 0) manager.closeTab(idx);
        });
        panel.add(label);
        addRunButtonsToTab(panel, index);
        panel.add(close);
        panel.addMouseListener(clickAdapter);
        return panel;
    }

    private static final int RUN_BUTTON_SIZE = 22;
    /** Darker green so the play icon is clearly visible. */
    private static final Color RUN_GREEN = new Color(0, 120, 0);
    /** Light green background so the Start button stands out from the tab. */
    private static final Color RUN_GREEN_BG = new Color(220, 255, 220);
    private static final Color STOP_RED = new Color(200, 50, 50);

    /** Add Start and Stop buttons for this tab (visible; Start = green, Stop = round red circle). */
    private void addRunButtonsToTab(JPanel tabPanel, int tabIndex) {
        JButton start = new JButton("\u25B6");
        start.setToolTipText("Start");
        start.setMargin(new Insets(0, 2, 0, 2));
        start.setFocusPainted(false);
        start.setForeground(RUN_GREEN);
        start.setBackground(RUN_GREEN_BG);
        start.setContentAreaFilled(true);
        start.setBorderPainted(true);
        start.setOpaque(true);
        start.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(RUN_GREEN, 1),
                javax.swing.BorderFactory.createEmptyBorder(0, 2, 0, 0)));
        start.setFont(start.getFont().deriveFont(Font.BOLD, 14f));
        start.setPreferredSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        start.setMinimumSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        start.setMaximumSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        start.addActionListener(e -> runActionForTab(tabIndex, PrismRunActions.ACTION_START));

        JButton stop = new RoundStopButton();
        stop.setToolTipText("Stop");
        stop.setPreferredSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        stop.setMinimumSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        stop.setMaximumSize(new Dimension(RUN_BUTTON_SIZE, RUN_BUTTON_SIZE));
        stop.addActionListener(e -> runActionForTab(tabIndex, PrismRunActions.ACTION_STOP));

        tabPanel.add(start);
        tabPanel.add(stop);
    }

    /** Round red Stop button (like JMeter toolbar). */
    private static class RoundStopButton extends JButton {
        RoundStopButton() {
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int d = Math.min(w, h) - 4;
            int x0 = (w - d) / 2;
            int y0 = (h - d) / 2;
            g2.setColor(STOP_RED);
            g2.fillOval(x0, y0, d, d);
            g2.dispose();
        }
    }

    /**
     * Run a JMeter action (Start, Stop, etc.) for the given tab. Switches to that tab first
     * so the correct test plan is active, then triggers the action.
     */
    private void runActionForTab(int index, String actionCommand) {
        if (index < 0 || index >= tabContexts.size()) return;
        if (selectedIndex != index) {
            final int idx = index;
            PrismTabSwitcher.switchTab(tabContexts, lastSelectedIndex, index, () -> {
                updateTabTitleFromCurrentPlan(idx);
                highlightSelectedTab(idx);
                PrismRunActions.doRunAction(actionCommand);
            });
            lastSelectedIndex = selectedIndex;
            selectedIndex = index;
        } else {
            PrismRunActions.doRunAction(actionCommand);
        }
    }

    private int indexOfTabPanel(Component tabPanel) {
        for (int i = 0; i < tabBar.getComponentCount(); i++) {
            if (tabBar.getComponent(i) == tabPanel) return i;
        }
        return -1;
    }

    private void showTabContextMenu(int x, int y, int index, Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(e -> manager.closeTab(index));
        menu.add(close);
        JMenuItem closeOthers = new JMenuItem("Close Others");
        closeOthers.addActionListener(e -> manager.closeOthers(index));
        menu.add(closeOthers);
        JMenuItem closeRight = new JMenuItem("Close Right");
        closeRight.addActionListener(e -> manager.closeRight(index));
        menu.add(closeRight);
        JMenuItem closeAll = new JMenuItem("Close All");
        closeAll.addActionListener(e -> manager.closeAll());
        menu.add(closeAll);
        menu.show(invoker, x, y);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabContexts.size()) return;
        final int idx = index;
        PrismTabSwitcher.switchTab(tabContexts, lastSelectedIndex, index, () -> {
            updateTabTitleFromCurrentPlan(idx);
            highlightSelectedTab(idx);
        });
        lastSelectedIndex = index;
        selectedIndex = index;
    }

    private void highlightSelectedTab(int selected) {
        Color selectedBg = UIManager.getColor("TabbedPane.selected");
        Color normalBg = UIManager.getColor("TabbedPane.background");
        if (selectedBg == null) selectedBg = UIManager.getColor("Panel.background");
        if (normalBg == null) normalBg = UIManager.getColor("Panel.background");
        for (int i = 0; i < tabBar.getComponentCount(); i++) {
            Component c = tabBar.getComponent(i);
            if (c instanceof JPanel) {
                JPanel p = (JPanel) c;
                p.setBackground(i == selected ? selectedBg : normalBg);
                for (Component child : p.getComponents()) {
                    if (child instanceof JLabel) {
                        child.setForeground(UIManager.getColor("TabbedPane.foreground") != null
                                ? UIManager.getColor("TabbedPane.foreground")
                                : UIManager.getColor("Label.foreground"));
                        break;
                    }
                }
            }
        }
        tabBar.repaint();
    }

    @Override
    public int getTabCount() {
        return tabContexts.size();
    }

    @Override
    public PrismTabContext getContextAt(int index) {
        if (index < 0 || index >= tabContexts.size()) return null;
        return tabContexts.get(index);
    }

    @Override
    public void removeTabAt(int index) {
        if (index < 0 || index >= tabContexts.size()) return;
        tabContexts.remove(index);
        tabBar.remove(index);
        if (selectedIndex >= index && selectedIndex > 0) selectedIndex--;
        selectedIndex = Math.min(selectedIndex, tabContexts.size() - 1);
        lastSelectedIndex = selectedIndex;
        if (selectedIndex >= 0) {
            final int idx = selectedIndex;
            PrismTabSwitcher.switchTab(tabContexts, -1, selectedIndex, () -> {
                updateTabTitleFromCurrentPlan(idx);
                highlightSelectedTab(idx);
            });
        } else if (tabContexts.isEmpty()) {
            PrismTabSwitcher.switchToEmptyState();
        }
        tabBar.revalidate();
        tabBar.repaint();
    }

    @Override
    public void setSelectedIndex(int index) {
        selectTab(index);
    }

    public void updateTabTitle(int index) {
        if (index < 0 || index >= tabContexts.size() || index >= tabBar.getComponentCount()) return;
        Component comp = tabBar.getComponent(index);
        if (!(comp instanceof JPanel)) return;
        PrismTabContext ctx = tabContexts.get(index);
        JPanel tab = (JPanel) comp;
        for (Component c : tab.getComponents()) {
            if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                String display = ctx.getDisplayName();
                String tooltip = ctx.getFullNameForTooltip();
                if (!display.equals(label.getText())) label.setText(display);
                if (!tooltip.equals(label.getToolTipText())) label.setToolTipText(tooltip);
                break;
            }
        }
    }

    public void updateTabTitleFromFile(int index, File file) {
        if (index < 0 || index >= tabContexts.size()) return;
        tabContexts.get(index).setFile(file);
        updateTabTitle(index);
    }

    public void updateTabTitleFromRootNode(int index, JMeterTreeNode rootNode) {
        if (index < 0 || index >= tabContexts.size()) return;
        tabContexts.get(index).setTitleFromRootNode(rootNode);
        updateTabTitle(index);
    }

    /**
     * Updates the tab title from the current plan (prism-oss style).
     * Prefers file name from GuiPackage.getTestPlanFile() when set (e.g. after opening a JMX),
     * otherwise uses the root test plan node name.
     */
    public void updateTabTitleFromCurrentPlan(int index) {
        if (index < 0 || index >= tabContexts.size()) return;
        PrismTabContext ctx = tabContexts.get(index);
        GuiPackage gui = ctx.getGuiPackage();
        if (gui == null) {
            ctx.setTitleFrom(PrismTabConstants.DEFAULT_TAB_TITLE);
            updateTabTitle(index);
            return;
        }
        String fullTitle = PrismTabConstants.DEFAULT_TAB_TITLE;
        String path = gui.getTestPlanFile();
        if (path != null && !path.trim().isEmpty()) {
            fullTitle = new File(path).getName();
        } else {
            JMeterTreeModel model = gui.getTreeModel();
            if (model != null) {
                Object root = model.getRoot();
                if (root instanceof JMeterTreeNode) {
                    fullTitle = PrismTabTitleUtil.getTitleFromRootNode((JMeterTreeNode) root);
                }
            }
        }
        ctx.setTitleFrom(fullTitle);
        updateTabTitle(index);
    }

    public boolean canOpenNewTab() {
        return tabContexts.size() < PrismTabConstants.MAX_TABS;
    }

    public Component getContent() {
        return content;
    }
}
