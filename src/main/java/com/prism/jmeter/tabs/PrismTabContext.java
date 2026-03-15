package com.prism.jmeter.tabs;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.Load;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jorphan.collections.HashTree;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

/**
 * Isolated context for a single Prism tab. Holds the tab's JMeter tree state
 * and metadata so each tab has its own data and switching does not cross-contaminate.
 * <p>
 * In a full implementation, this would either wrap a per-tab tree/model or
 * participate in a save/restore (or swap) of GuiPackage state when switching tabs.
 * </p>
 */
public class PrismTabContext {

    private final String tabId;
    private String displayName;
    private String fullNameForTooltip;
    private File file;
    private JMeterTreeModel treeModel;
    private JMeterTreeNode rootNode;
    /** Stored test plan so we can restore via Load.insertLoadedTree (fallback). */
    private HashTree testPlanHashTree;
    private boolean dirty;

    /** WorkspaceState-style: full GuiPackage per tab (reference approach). */
    private GuiPackage guiPackage;
    private final List<Integer> expandedRows = new ArrayList<>();
    private int selectedRow = -1;
    private Map<Object, Object> checkDirtyItems;
    private ActionListener actionHandler;
    /** File to load when this tab is first switched to (Open in new tab). */
    private File fileToLoad;

    public PrismTabContext(String tabId) {
        this.tabId = Objects.requireNonNull(tabId, "tabId");
        this.displayName = PrismTabConstants.DEFAULT_TAB_TITLE;
        this.fullNameForTooltip = PrismTabConstants.DEFAULT_TAB_TITLE;
        this.dirty = false;
    }

    public GuiPackage getGuiPackage() { return guiPackage; }
    public void setGuiPackage(GuiPackage guiPackage) { this.guiPackage = guiPackage; }
    public List<Integer> getExpandedRows() { return expandedRows; }
    public int getSelectedRow() { return selectedRow; }
    public void setSelectedRow(int selectedRow) { this.selectedRow = selectedRow; }
    public Map<Object, Object> getCheckDirtyItems() { return checkDirtyItems; }
    public void setCheckDirtyItems(Map<Object, Object> checkDirtyItems) { this.checkDirtyItems = checkDirtyItems; }
    public ActionListener getActionHandler() { return actionHandler; }
    public void setActionHandler(ActionListener actionHandler) { this.actionHandler = actionHandler; }
    public File getFileToLoad() { return fileToLoad; }
    public void setFileToLoad(File fileToLoad) { this.fileToLoad = fileToLoad; }

    public String getTabId() {
        return tabId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? PrismTabConstants.DEFAULT_TAB_TITLE : displayName;
    }

    public String getFullNameForTooltip() {
        return fullNameForTooltip;
    }

    public void setFullNameForTooltip(String fullNameForTooltip) {
        this.fullNameForTooltip = fullNameForTooltip == null ? displayName : fullNameForTooltip;
    }

    /** Updates display name and tooltip with auto-truncation. */
    public void setTitleFrom(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            fullName = PrismTabConstants.DEFAULT_TAB_TITLE;
        }
        this.fullNameForTooltip = fullName;
        if (fullName.length() <= PrismTabConstants.TAB_TITLE_MAX_LENGTH) {
            this.displayName = fullName;
        } else {
            this.displayName = fullName.substring(0, PrismTabConstants.TAB_TITLE_MAX_LENGTH - 1)
                    + PrismTabConstants.TAB_TITLE_ELLIPSIS;
        }
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            setTitleFrom(file.getName());
        }
    }

    /**
     * Updates display name and tooltip from the Root Test Plan node.
     * Call after loading a test plan so the tab title reflects the plan name.
     */
    public void setTitleFromRootNode(JMeterTreeNode root) {
        setTitleFrom(PrismTabTitleUtil.getTitleFromRootNode(root));
    }

    public JMeterTreeModel getTreeModel() {
        return treeModel;
    }

    public void setTreeModel(JMeterTreeModel treeModel) {
        this.treeModel = treeModel;
    }

    /** Set the test plan as HashTree (e.g. from SaveService.loadTree). Used for restore via Load.insertLoadedTree. */
    public void setTestPlanHashTree(HashTree tree) {
        this.testPlanHashTree = tree;
    }

    public HashTree getTestPlanHashTree() {
        return testPlanHashTree;
    }

    public JMeterTreeNode getRootNode() {
        return rootNode;
    }

    public void setRootNode(JMeterTreeNode rootNode) {
        this.rootNode = rootNode;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * Activates this tab's state in the current JMeter GUI. Prefers stored HashTree
     * and restores via Load.insertLoadedTree so each tab has its own plan copy.
     */
    public void activate() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            return;
        }
        if (testPlanHashTree != null && !testPlanHashTree.isEmpty()) {
            try {
                Load.insertLoadedTree(ActionEvent.ACTION_FIRST, testPlanHashTree, false);
                return;
            } catch (Exception e) {
                PrismTabsLog.reflectionFailure("activate insertLoadedTree", e);
            }
        }
        if (treeModel != null) {
            setTreeModelOnGuiPackage(guiPackage, treeModel);
        }
    }

    /**
     * Captures the current tree state into this context. Stores a clone of the test plan
     * as HashTree (via getTestPlanTree when available) so each tab has its own copy.
     */
    @SuppressWarnings("unchecked")
    public void captureFromGui() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) {
            return;
        }
        JMeterTreeModel model = null;
        JTree jtree = getJTreeFromListener(guiPackage);
        if (jtree == null) {
            jtree = findJTreeInFrame(guiPackage);
        }
        if (jtree != null && jtree.getModel() instanceof JMeterTreeModel) {
            model = (JMeterTreeModel) jtree.getModel();
        } else {
            model = guiPackage.getTreeModel();
        }
        this.treeModel = model;
        if (model != null) {
            this.rootNode = (JMeterTreeNode) model.getRoot();
            HashTree planTree = getTestPlanTreeFromModel(model);
            if (planTree != null) {
                try {
                    this.testPlanHashTree = (HashTree) planTree.clone();
                } catch (Exception e) {
                    PrismTabsLog.reflectionFailure("captureFromGui clone HashTree", e);
                    this.testPlanHashTree = planTree;
                }
            }
        }
    }

    private static HashTree getTestPlanTreeFromModel(JMeterTreeModel model) {
        try {
            Method m = model.getClass().getMethod("getTestPlanTree");
            Object tree = m.invoke(model);
            return tree instanceof HashTree ? (HashTree) tree : null;
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("getTestPlanTreeFromModel", e);
            return null;
        }
    }

    /**
     * Attempts to set the tree model on GuiPackage so the visible tree and
     * getTreeModel() (used by Run) both reflect this tab's plan. Tries, in order:
     * (1) public setTreeModel, (2) declared setTreeModel with setAccessible,
     * (3) set the treeModel field via reflection, (4) set the JTree's model directly
     * so at least the display is correct.
     */
    private static void setTreeModelOnGuiPackage(GuiPackage guiPackage, JMeterTreeModel model) {
        if (guiPackage == null || model == null) {
            return;
        }
        // 1) Try public setTreeModel (e.g. future JMeter)
        if (invokeSetTreeModel(guiPackage, model, false)) {
            return;
        }
        // 2) Try declared (package-private / protected) setTreeModel
        if (invokeSetTreeModel(guiPackage, model, true)) {
            return;
        }
        // 3) Set the treeModel field on GuiPackage so getTreeModel() returns the right model (for Run)
        if (setTreeModelField(guiPackage, model)) {
            updateJTreeModel(guiPackage, model);
            return;
        }
        // 4) Fallback: at least set the JTree's model so the visible tree switches
        updateJTreeModel(guiPackage, model);
    }

    private static boolean invokeSetTreeModel(GuiPackage guiPackage, JMeterTreeModel model, boolean declaredOnly) {
        try {
            Method setter = declaredOnly
                    ? GuiPackage.class.getDeclaredMethod("setTreeModel", JMeterTreeModel.class)
                    : GuiPackage.class.getMethod("setTreeModel", JMeterTreeModel.class);
            setter.setAccessible(true);
            setter.invoke(guiPackage, model);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean setTreeModelField(GuiPackage guiPackage, JMeterTreeModel model) {
        for (String fieldName : new String[]{"treeModel", "model"}) {
            try {
                Field f = GuiPackage.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(guiPackage, model);
                return true;
            } catch (Exception e) {
                // try next field name
            }
        }
        return false;
    }

    /** Sets the JTree's model so the displayed tree updates. Tries listener first, then finds JTree in frame hierarchy. */
    private static void updateJTreeModel(GuiPackage guiPackage, JMeterTreeModel model) {
        JTree jtree = getJTreeFromListener(guiPackage);
        if (jtree == null) {
            jtree = findJTreeInFrame(guiPackage);
        }
        if (jtree != null) {
            applyModelToTree(jtree, model);
        }
    }

    private static JTree getJTreeFromListener(GuiPackage guiPackage) {
        try {
            Object listener = guiPackage.getTreeListener();
            if (listener == null) return null;
            for (String methodName : new String[]{"getJTree", "getTree"}) {
                try {
                    Method getTree = listener.getClass().getMethod(methodName);
                    Object tree = getTree.invoke(listener);
                    if (tree instanceof JTree) return (JTree) tree;
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("getJTreeFromListener", e);
        }
        return null;
    }

    private static JTree findJTreeInFrame(GuiPackage guiPackage) {
        try {
            Method getMainFrame = GuiPackage.class.getMethod("getMainFrame");
            Object frame = getMainFrame.invoke(guiPackage);
            if (frame instanceof Container) {
                return findJTreeIn((Container) frame);
            }
            if (frame != null) {
                Method getContentPane = frame.getClass().getMethod("getContentPane");
                Object cp = getContentPane.invoke(frame);
                if (cp instanceof Container) {
                    return findJTreeIn((Container) cp);
                }
            }
        } catch (Exception e) {
            PrismTabsLog.reflectionFailure("findJTreeInFrame", e);
        }
        return null;
    }

    private static JTree findJTreeIn(Container root) {
        if (root instanceof JTree) {
            return (JTree) root;
        }
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof JTree) {
                return (JTree) c;
            }
            if (c instanceof Container) {
                JTree found = findJTreeIn((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void applyModelToTree(JTree jtree, JMeterTreeModel model) {
        SwingUtilities.invokeLater(() -> {
            jtree.setModel(model);
            jtree.revalidate();
            jtree.repaint();
        });
    }
}
