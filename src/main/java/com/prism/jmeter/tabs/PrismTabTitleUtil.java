package com.prism.jmeter.tabs;

import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;

/**
 * Derives a tab title from the Root Test Plan node or file name,
 * with truncation and tooltip text.
 */
public final class PrismTabTitleUtil {

    private PrismTabTitleUtil() {}

    /**
     * Builds display name and full tooltip from the root node's name.
     * If the root is the default "Test Plan" node, consider using fileName instead.
     */
    public static String getTitleFromRootNode(JMeterTreeNode root) {
        if (root == null) {
            return PrismTabConstants.DEFAULT_TAB_TITLE;
        }
        TestElement el = root.getTestElement();
        String name = el != null ? el.getName() : root.toString();
        return name != null && !name.isEmpty() ? name : PrismTabConstants.DEFAULT_TAB_TITLE;
    }

}
