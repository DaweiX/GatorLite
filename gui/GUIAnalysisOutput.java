/*
 * GUIAnalysisOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import presto.android.gui.graph.*;
import presto.android.gui.listener.EventType;
import soot.SootClass;
import soot.SootMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GUIAnalysisOutput {
  // === Results
  FlowGraph getFlowgraph();

  VariableValueQueryInterface getVariableValueQueryInterface();

  // === Activities
  Set<SootClass> getActivities();
  Set<NNode> getActivityRoots(SootClass activity);
  Set<SootMethod> getActivityHandlers(SootClass activity,
      List<String> subsigs);

  void getContextMenus(NObjectNode view, Set<NContextMenuNode> result);
  Set<NContextMenuNode> getContextMenus(NObjectNode view);
  SootMethod getOnCreateContextMenuMethod(NContextMenuNode contextMenu);

  // === Dialogs
  Set<NDialogNode> getDialogs();
  Set<NNode> getDialogRoots(NDialogNode dialog);

  Map<EventType, Set<SootMethod>> getExplicitEventsAndTheirHandlers(NObjectNode guiObject);

  SootMethod getRealHandler(SootMethod fakeHandler);

  // === Measurements
  void setRunningTimeInNanoSeconds(long runningTimeInNanoSeconds);
}
