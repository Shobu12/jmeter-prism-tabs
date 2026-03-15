package com.prism.jmeter.tabs;

/**
 * Constants for the Prism tab plugin (max tabs, labels, etc.).
 */
public final class PrismTabConstants {

    /** Maximum number of open tabs in the open-source version. */
    public static final int MAX_TABS = 10;

    /** Default tab title when no file or plan name is available. */
    public static final String DEFAULT_TAB_TITLE = "Untitled";

    /** Maximum display length for tab title before truncation. */
    public static final int TAB_TITLE_MAX_LENGTH = 24;

    /** Ellipsis used when truncating tab titles. */
    public static final String TAB_TITLE_ELLIPSIS = "…";

    private PrismTabConstants() {}
}
