package com.prism.jmeter.tabs;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.Load;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.HashTree;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Switches the active tab by saving the current GuiPackage state and restoring
 * the target tab's GuiPackage (or creating a new one for new tabs). Follows the
 * prism-oss approach: one GuiPackage per tab, swap singleton on switch.
 */
public final class PrismTabSwitcher {

    private PrismTabSwitcher() {}

    /**
     * When the last tab is closed, switch to a new empty test plan (act as new).
     * Replaces the current GuiPackage with a fresh empty one so the workspace is cleared.
     */
    public static void switchToEmptyState() {
        GuiPackage currentGui = GuiPackage.getInstance();
        if (currentGui == null) return;
        Object mainFrame = invokeReturn(currentGui, "getMainFrame");
        if (mainFrame == null) return;
        Object treeObj = invokeReturn(mainFrame, "getTree");
        if (!(treeObj instanceof JTree)) return;
        JTree tree = (JTree) treeObj;

        PrismTabContext previous = new PrismTabContext("_previous_");
        previous.setGuiPackage(currentGui);
        try {
            Object listener = currentGui.getTreeListener();
            if (listener != null) {
                Field actionHandlerField = JMeterTreeListener.class.getDeclaredField("actionHandler");
                actionHandlerField.setAccessible(true);
                previous.setActionHandler((java.awt.event.ActionListener) actionHandlerField.get(listener));
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("switchToEmptyState actionHandler", e);
        }

        PrismTabContext emptyTarget = new PrismTabContext("_empty_");
        createNewGuiPackageForTab(emptyTarget, previous, mainFrame);
        swapTreeListenersAndModel(tree, previous, emptyTarget);
        emptyTarget.setCheckDirtyItems(new java.util.HashMap<>());
        swapCheckDirtyState(previous, emptyTarget);
        updateFileServerBase(emptyTarget.getGuiPackage());

        GuiPackage newGui = emptyTarget.getGuiPackage();
        SwingUtilities.invokeLater(() -> {
            if (tree.getRowCount() > 0) tree.setSelectionRow(0);
            invoke(newGui, "updateCurrentGui");
            try {
                mainFrame.getClass().getMethod("setExtendedFrameTitle", String.class).invoke(mainFrame, "");
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("switchToEmptyState setExtendedFrameTitle", e);
            }
            try {
                Method setDirty = GuiPackage.class.getMethod("setDirty", boolean.class);
                setDirty.invoke(newGui, false);
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("switchToEmptyState setDirty", e);
            }
            if (mainFrame instanceof java.awt.Component) ((java.awt.Component) mainFrame).repaint();
        });
    }

    /**
     * Performs the full tab switch: save current workspace, restore or create target's
     * GuiPackage, swap tree model and listeners, CheckDirty, FileServer, restore expansion/selection.
     */
    public static void switchTab(List<PrismTabContext> contexts, int fromIndex, int toIndex,
                                 Runnable onAfterSwitch) {
        if (contexts == null || toIndex < 0 || toIndex >= contexts.size()) {
            return;
        }
        PrismTabContext oldContext = (fromIndex >= 0 && fromIndex < contexts.size()) ? contexts.get(fromIndex) : null;
        GuiPackage currentGui = GuiPackage.getInstance();
        if (currentGui == null) {
            return;
        }
        Object mainFrame = invokeReturn(currentGui, "getMainFrame");
        if (mainFrame == null) {
            return;
        }
        Object treeObj = invokeReturn(mainFrame, "getTree");
        if (!(treeObj instanceof JTree)) {
            return;
        }
        JTree tree = (JTree) treeObj;

        // 1. Save current state
        if (oldContext != null) {
            saveCurrentState(oldContext, currentGui, tree);
        }

        PrismTabContext target = contexts.get(toIndex);

        // 2. Prepare target: first tab takes current GuiPackage; else create new or restore singleton
        boolean isFirstTab = (fromIndex < 0 && target.getGuiPackage() == null);
        boolean isNewlyCreated = false;
        if (isFirstTab) {
            target.setGuiPackage(currentGui);
            saveCurrentState(target, currentGui, tree);
        } else if (target.getGuiPackage() == null) {
            isNewlyCreated = true;
            createNewGuiPackageForTab(target, oldContext, mainFrame);
            if (target.getFileToLoad() != null) {
                loadFileIntoGui(target.getFileToLoad());
                target.setFileToLoad(null);
            }
        } else {
            restoreGuiPackageSingleton(target);
        }

        // 3. Swap tree model and listeners on the JTree (skip if first tab - tree already correct)
        GuiPackage targetGui = target.getGuiPackage();
        if (targetGui != null && !isFirstTab) {
            swapTreeListenersAndModel(tree, oldContext, target);
        }

        // 4. Swap CheckDirty state (first tab already saved into target)
        swapCheckDirtyState(oldContext, target);

        // 5. FileServer base for active tab
        updateFileServerBase(targetGui);

        // 6. Restore tree expansion and selection, then sync so Run/Start use active tab's plan
        final List<Integer> rowsToExpand = new ArrayList<>(target.getExpandedRows());
        final int rowToSelect = target.getSelectedRow();
        final boolean newlyCreated = isNewlyCreated;
        SwingUtilities.invokeLater(() -> {
            for (int row : rowsToExpand) {
                if (row >= 0 && row < tree.getRowCount()) {
                    tree.expandRow(row);
                }
            }
            if (rowToSelect >= 0 && rowToSelect < tree.getRowCount()) {
                tree.setSelectionRow(rowToSelect);
            } else if (tree.getRowCount() > 0) {
                tree.setSelectionRow(0);
            }
            // Re-apply singleton so Start/Stop and any actions use this tab's GuiPackage
            restoreGuiPackageSingleton(target);
            invoke(targetGui, "updateCurrentNode");
            invoke(targetGui, "updateCurrentGui");
            try {
                String title = targetGui != null ? targetGui.getTestPlanFile() : "";
                mainFrame.getClass().getMethod("setExtendedFrameTitle", String.class).invoke(mainFrame, title);
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("switchTab setExtendedFrameTitle", e);
            }
            if (mainFrame instanceof java.awt.Component) {
                ((java.awt.Component) mainFrame).repaint();
            }
            if (newlyCreated && targetGui != null) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Method setDirty = GuiPackage.class.getMethod("setDirty", boolean.class);
                        setDirty.invoke(targetGui, false);
                    } catch (Exception e) {
                        PrismTabsLog.reflectionFailure("switchTab setDirty (new tab)", e);
                    }
                });
            }
            if (onAfterSwitch != null) {
                onAfterSwitch.run();
            }
        });
    }

    private static void saveCurrentState(PrismTabContext ctx, GuiPackage guiPackage, JTree tree) {
        ctx.setGuiPackage(guiPackage);
        if (guiPackage != null) {
            try {
                Method updateNode = GuiPackage.class.getMethod("updateCurrentNode");
                updateNode.invoke(guiPackage);
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("saveCurrentState updateCurrentNode", e);
            }
            ctx.getExpandedRows().clear();
            for (int i = 0; i < tree.getRowCount(); i++) {
                if (tree.isExpanded(i)) {
                    ctx.getExpandedRows().add(i);
                }
            }
            ctx.setSelectedRow(tree.getMinSelectionRow());
            try {
                Object listener = guiPackage.getTreeListener();
                if (listener != null) {
                    Field actionHandlerField = JMeterTreeListener.class.getDeclaredField("actionHandler");
                    actionHandlerField.setAccessible(true);
                    ctx.setActionHandler((java.awt.event.ActionListener) actionHandlerField.get(listener));
                }
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("saveCurrentState actionHandler", e);
            }
            ctx.setCheckDirtyItems(captureCheckDirtyItems());
        }
    }

    private static void createNewGuiPackageForTab(PrismTabContext target, PrismTabContext previous, Object mainFrame) {
        JMeterTreeModel newModel = new JMeterTreeModel();
        newModel.clearTestPlan();
        JMeterTreeListener newListener = new JMeterTreeListener();
        newListener.setModel(newModel);
        if (previous != null && previous.getActionHandler() != null) {
            newListener.setActionHandler(previous.getActionHandler());
        }
        GuiPackage.initInstance(newListener, newModel);
        GuiPackage newGui = GuiPackage.getInstance();
        target.setGuiPackage(newGui);
        if (previous != null && previous.getGuiPackage() != null) {
            GuiPackage oldGui = previous.getGuiPackage();
            invokeReturn(newGui, "setMainFrame", invokeReturn(oldGui, "getMainFrame"));
            invokeReturn(newGui, "setMainToolbar", invokeReturn(oldGui, "getMainToolbar"));
        }
        try {
            Field logPanelField = mainFrame.getClass().getDeclaredField("logPanel");
            logPanelField.setAccessible(true);
            Object logPanel = logPanelField.get(mainFrame);
            if (logPanel != null) {
                invokeReturn(newGui, "setLoggerPanel", logPanel);
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("setLoggerPanel on new GuiPackage", e);
        }
        try {
            Method register = GuiPackage.class.getMethod("registerAsListener");
            register.invoke(newGui);
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("registerAsListener", e);
        }
    }

    private static void restoreGuiPackageSingleton(PrismTabContext target) {
        if (target == null || target.getGuiPackage() == null) return;
        try {
            Field guiPackField = GuiPackage.class.getDeclaredField("guiPack");
            guiPackField.setAccessible(true);
            guiPackField.set(null, target.getGuiPackage());
        } catch (Exception e) {
            PrismTabsLog.reflectionCritical("restoreGuiPackageSingleton", e);
        }
    }

    private static void swapTreeListenersAndModel(JTree tree, PrismTabContext oldContext, PrismTabContext target) {
        GuiPackage oldGui = oldContext != null ? oldContext.getGuiPackage() : null;
        GuiPackage targetGui = target.getGuiPackage();
        if (oldGui != null) {
            Object oldListener = oldGui.getTreeListener();
            if (oldListener != null) {
                tree.removeTreeSelectionListener((javax.swing.event.TreeSelectionListener) oldListener);
                tree.removeMouseListener((java.awt.event.MouseListener) oldListener);
                tree.removeKeyListener((java.awt.event.KeyListener) oldListener);
            }
        }
        tree.setModel(targetGui.getTreeModel());
        Object newListener = targetGui.getTreeListener();
        if (newListener != null) {
            tree.addTreeSelectionListener((javax.swing.event.TreeSelectionListener) newListener);
            tree.addMouseListener((java.awt.event.MouseListener) newListener);
            tree.addKeyListener((java.awt.event.KeyListener) newListener);
            try {
                Method setJTree = newListener.getClass().getMethod("setJTree", JTree.class);
                setJTree.invoke(newListener, tree);
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("swapTreeListenersAndModel setJTree", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> captureCheckDirtyItems() {
        try {
            Object router = Class.forName("org.apache.jmeter.gui.action.ActionRouter").getMethod("getInstance").invoke(null);
            Object checkDirty = router.getClass().getMethod("getAction", String.class, Class.class)
                    .invoke(router, "check_dirty", Class.forName("org.apache.jmeter.gui.action.CheckDirty"));
            if (checkDirty != null) {
                Field itemsField = checkDirty.getClass().getDeclaredField("previousGuiItems");
                itemsField.setAccessible(true);
                return (Map<Object, Object>) itemsField.get(checkDirty);
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("captureCheckDirtyItems", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void swapCheckDirtyState(PrismTabContext oldState, PrismTabContext targetState) {
        try {
            Object router = Class.forName("org.apache.jmeter.gui.action.ActionRouter").getMethod("getInstance").invoke(null);
            Object checkDirty = router.getClass().getMethod("getAction", String.class, Class.class)
                    .invoke(router, "check_dirty", Class.forName("org.apache.jmeter.gui.action.CheckDirty"));
            if (checkDirty != null) {
                Field itemsField = checkDirty.getClass().getDeclaredField("previousGuiItems");
                itemsField.setAccessible(true);
                Map<Object, Object> toRestore = targetState.getCheckDirtyItems();
                itemsField.set(checkDirty, toRestore != null ? toRestore : new java.util.HashMap<>());
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("swapCheckDirtyState", e);
        }
    }

    private static void updateFileServerBase(GuiPackage guiPackage) {
        if (guiPackage == null) return;
        try {
            String path = guiPackage.getTestPlanFile();
            Object fileServer = Class.forName("org.apache.jmeter.services.FileServer").getMethod("getFileServer").invoke(null);
            if (path != null && !path.isEmpty()) {
                fileServer.getClass().getMethod("setBaseForScript", File.class).invoke(fileServer, new File(path));
            } else {
                fileServer.getClass().getMethod("resetBase").invoke(fileServer);
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("updateFileServerBase", e);
        }
    }

    private static void loadFileIntoGui(File file) {
        if (file == null || !file.isFile()) {
            PrismTabsLog.warn("loadFileIntoGui: invalid file " + (file != null ? file.getPath() : "null"));
            return;
        }
        try {
            SaveService.loadProperties();
            HashTree tree = SaveService.loadTree(file);
            if (tree != null && !tree.isEmpty()) {
                Load.insertLoadedTree(ActionEvent.ACTION_FIRST, tree, false);
                GuiPackage gui = GuiPackage.getInstance();
                if (gui != null) {
                    try {
                        gui.getClass().getMethod("setTestPlanFile", String.class).invoke(gui, file.getAbsolutePath());
                    } catch (Exception e) {
                        PrismTabsLog.reflectionFailure("setTestPlanFile after load", e);
                    }
                }
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("loadFileIntoGui: " + (file != null ? file.getName() : "null"), e);
        }
    }

    /** Invoke and return result (use for methods that return a value). */
    private static Object invokeReturn(Object target, String methodName, Object... args) {
        if (target == null) return null;
        try {
            if (args == null || args.length == 0) {
                Method m = target.getClass().getMethod(methodName);
                return m.invoke(target);
            }
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            Method m = target.getClass().getMethod(methodName, paramTypes);
            return m.invoke(target, args);
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("invokeReturn " + methodName, e);
            return null;
        }
    }

    /** Invoke and ignore return (use for void methods). */
    private static void invoke(Object target, String methodName) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.invoke(target);
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("invoke " + methodName, e);
        }
    }
}
