# JMeter Tab Workspace

A plugin that adds a **multi-tabbed interface** to Apache JMeter, similar to browser tabs. Open multiple test plans in separate tabs, switch instantly, and run each plan independently with full data isolation.

## Features

- **Multi-tabbed interface**: Open test plans in separate tabs instead of overwriting the current workspace. The current tab's state is captured before opening or loading in a new tab so each tab keeps its own workspace.
- **Fast switching**: Switch between complex test plans without rebuilding the UI.
- **Data isolation**: Each tab keeps its own JMeter `GuiPackage` tree state; switching tabs captures/restores state so there is no cross-contamination.
- **Dynamic tab names**: Tab titles are derived from the loaded file name or Root Test Plan node, with auto-truncation and full-name tooltips.
- **Tab limit**: Open-source version allows up to **10** simultaneous tabs.
- **Context menu**: Right-click on a tab for **Close**, **Close Others**, **Close Right**, **Close All**.
- **Per-tab Run**: The active tab's test plan is the one used when you run; Start/Stop in the tab bar apply to the active tab only.

## Installation & Requirements

**Requirements**

- **JDK 11+** (to build and run)
- **Apache JMeter 5.6.x** (provided at runtime; plugin is built against 5.6.3)
- **Maven 3.x** (to build from source)

**Installation**

1. Build the plugin (see [How to build from source](#how-to-build-from-source)).
2. Copy the built JAR into your JMeter installation:
   ```bash
   cp target/jmeter-tab-workspace-*.jar /path/to/apache-jmeter-5.x/lib/ext/
   ```
3. Start JMeter. The first time you use **Open in new tab** or **New tab**, the main content area is wrapped in the tab workspace UI.

## How to build from source

```bash
mvn clean package
```

- **Output**: `target/jmeter-tab-workspace-1.0.0-SNAPSHOT.jar` (or the version in `pom.xml`)
- **Skip tests**: `mvn clean package -DskipTests`

## Limitations

- **Maximum 10 tabs** in the open-source version (configurable via `PrismTabConstants.MAX_TABS`).
- **One test run at a time**: Only the active tab's test plan is executed when you click Start; JMeter's engine runs a single test.
- **JMeter API compatibility**: `GuiPackage` and related APIs may differ across JMeter versions; the plugin uses reflection where needed. If your JMeter build does not expose `setTreeModel`, the plugin tries several fallbacks (public/declared setter, field injection, `JTree.setModel`).
- **Menu position**: "Open in new tab" and "New tab" appear in the File menu where JMeter inserts plugin items (typically after core items); exact position depends on plugin load order.

## Architecture

- **`PrismTabPanel`**: Tab bar + single content area; holds one `PrismTabContext` per tab. On tab change, it captures the previous tab's tree from `GuiPackage` and activates the selected tab's stored tree.
- **`PrismTabContext`**: Per-tab state: tab id, display/full name, file, `JMeterTreeModel`, root node, dirty flag. `activate()` restores this context into `GuiPackage`; `captureFromGui()` saves the current tree into the context.
- **`PrismTabManager`**: Handles close actions (close tab, close others, close right, close all) and notifies optional listeners.
- **GuiPackage**: JMeter's GUI state is a singleton. Isolation is achieved by **swapping** the tree model in `GuiPackage` when changing tabs (save current tree into the leaving tab's context, load the entering tab's tree into `GuiPackage`).

## Usage

- **Open in new tab**: File → **Open in new tab** → choose a .jmx file. A new tab is added and the file is loaded. (The first time you use it, the main area becomes tabbed.)
- **Switch tabs**: Click a tab to switch; the tree and state switch to that tab's test plan.
- **Close tab**: Right-click the tab → **Close** (or **Close Others**, **Close Right**, **Close All**).
- **Run**: Run uses the **active tab's** test plan only.

## License

Open-source; see project license file.

## Apache JMeter

This project is affiliated with the Apache Software Foundation. Apache JMeter is a trademark of the Apache Software Foundation.
