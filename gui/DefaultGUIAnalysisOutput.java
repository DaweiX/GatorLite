/*
 * DefaultGUIAnalysisOutput.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import presto.android.Hierarchy;
import presto.android.MultiMapUtil;
import presto.android.gui.graph.*;
import presto.android.gui.listener.EventType;
import presto.android.gui.listener.ListenerSpecification;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.*;

public class DefaultGUIAnalysisOutput implements GUIAnalysisOutput {
	// Objects that may store analysis results or help with retrieval of result.
	GUIAnalysis analysis;
	FlowGraph flowgraph;
	FixpointSolver solver;
	GraphUtil graphUtil;
	JimpleUtil jimpleUtil;
	Hierarchy hier;
	ListenerSpecification listenerSpec;

	Predicate<EventType> isImplicitEventTypeFilter = EventType::isImplicit;

	Predicate<EventType> isExplicitEventTypeFilter = Predicates.not(isImplicitEventTypeFilter);

	public DefaultGUIAnalysisOutput(GUIAnalysis analysis) {
		this.analysis = analysis;
		this.flowgraph = analysis.flowgraph;
		this.solver = analysis.fixpointSolver;
		this.graphUtil = GraphUtil.v();
		this.jimpleUtil = JimpleUtil.v();
		this.hier = Hierarchy.v();
		this.listenerSpec = ListenerSpecification.v();
	}

	@Override
	public Set<NDialogNode> getDialogs() {
		Set<NDialogNode> dialogs = Sets.newHashSet();
		for (NWindowNode window : NWindowNode.windowNodes) {
			if (window instanceof NDialogNode) {
				dialogs.add((NDialogNode) window);
			}
		}
		return dialogs;
	}

	@Override
	public Set<NNode> getDialogRoots(NDialogNode dialog) {
		Set<NNode> roots = solver.dialogRoots.get(dialog);
		if (roots == null) {
			roots = Collections.emptySet();
		}
		return roots;
	}

	// Main activity
	@Override
	public Set<SootClass> getActivities() {
		return flowgraph.allNActivityNodes.keySet();
	}

	// Root views for a specified activity. Once the root view is obtained, you
	// can traverse the structure by following the parents and children
	// pointers, or use GraphUtil class to find all descendant nodes of the root
	@Override
	public Set<NNode> getActivityRoots(SootClass activity) {
		NActivityNode activityNode = flowgraph.allNActivityNodes.get(activity);
		return MultiMapUtil.getNonNullHashSetByKey(solver.activityRoots, activityNode);
	}

	@Override
	public Set<SootMethod> getActivityHandlers(SootClass activity, List<String> subsigs) {
		Set<SootMethod> result = Sets.newHashSet();
		for (String subsig : subsigs) {
			SootClass onClass = hier.matchForVirtualDispatch(subsig, activity);
			if (onClass != null && onClass.isApplicationClass()) {
				result.add(onClass.getMethod(subsig));
			}
		}

		return result;
	}

	// For a specified GUI object, return all its explicit events and
	// corresponding event handlers.
	@Override
	public Map<EventType, Set<SootMethod>> getExplicitEventsAndTheirHandlers(NObjectNode guiObject) {
		return getSupportedEventsAndTheirHandlers(guiObject, isExplicitEventTypeFilter);
	}

	// For a specified GUI object, based on a specified condition, return the
	// satisfying events that can be triggered on it and the corresponding event
	// handlers.
	public Map<EventType, Set<SootMethod>> getSupportedEventsAndTheirHandlers(NObjectNode guiObject,
			Predicate<EventType> condition) {
		Map<EventType, Set<SootMethod>> result = Maps.newHashMap();
		Set<Stmt> regs = getCallbackRegistrations(guiObject, condition);
		for (Stmt s : regs) {
			EventType eventType = listenerSpec.lookupEventType(s);
            Set<SootMethod> handlers = result.computeIfAbsent(eventType, k -> Sets.newHashSet());
			Collection<SootMethod> handlersToAdd = flowgraph.regToEventHandlers.get(s);
			if (handlersToAdd == null || handlersToAdd.isEmpty()) {
				continue;
			}
			handlers.addAll(handlersToAdd);
		}
		return result;
	}

	// For a specified GUI object, return the set of all its corresponding
	// callback registration statements satisfying a specified condition.
	public Set<Stmt> getCallbackRegistrations(NObjectNode guiObject, Predicate<EventType> condition) {
		Set<Stmt> result = Sets.newHashSet();
		Set<NOpNode> setListenerNodes = NOpNode.getNodes(NSetListenerOpNode.class);
		for (NOpNode opNode : setListenerNodes) {
			NSetListenerOpNode setListener = (NSetListenerOpNode) opNode;
			Set<NNode> receiverSet = solver.solutionReceivers.get(setListener);
			if (receiverSet != null && receiverSet.contains(guiObject)) {
				Stmt regStmt = setListener.callSite.getO1();
				EventType eventType = listenerSpec.lookupEventType(regStmt);
				if (condition.apply(eventType)) {
					result.add(regStmt);
				}
			}
		}

		return result;
	}

	// Return a VariableValueQueryInterface object which provides the ability to
	// query for possible values of variables of some type (e.g., activities,
	// views, IDs).
	@Override
	public VariableValueQueryInterface getVariableValueQueryInterface() {
		return analysis.variableValueQueryInterface;
	}

	// Return the instance of FlowGraph associated with this run of the analysis
	// algorithm. The returned instance is mutable.
	//
	// WARNING: changes to the state of the FlowGraph object may invalidate the
	// result of GUI analysis. Use with caution.
	@Override
	public FlowGraph getFlowgraph() {
		return flowgraph;
	}

	// For a specified GUI object, return the set of associated NContextMenuNode
	// nodes.
	@Override
	public void getContextMenus(NObjectNode view, Set<NContextMenuNode> result) {
		VariableValueQueryInterface variableValues = getVariableValueQueryInterface();
		for (NContextMenuNode contextMenu : flowgraph.menuVarNodeToContextMenus.values()) {
			for (NVarNode viewVarNode : contextMenu.varNodesForRegisteredViews) {
				if (variableValues.guiVariableValues(viewVarNode.l).contains(view)) {
					result.add(contextMenu);
				}
			}
		}
	}

	@Override
	public Set<NContextMenuNode> getContextMenus(NObjectNode view) {
		Set<NContextMenuNode> result = Sets.newHashSet();
		getContextMenus(view, result);
		return result;
	}

	// Given a context menu node, returns the onCreateContextMenu method that
	// "allocates" this menu object.
	@Override
	public SootMethod getOnCreateContextMenuMethod(NContextMenuNode contextMenu) {
		return flowgraph.contextMenuToOnCreateContextMenus.get(contextMenu);
	}

	/*
	 * Given an artificial handler, returns the real handler.
	 */
	@Override
	public SootMethod getRealHandler(SootMethod fakeHandler) {
		return flowgraph.fakeHandlerToRealHandler.get(fakeHandler);
	}


    @Override
	public void setRunningTimeInNanoSeconds(long runningTimeInNanoSeconds) {
        // GUI analysis running time in nano seconds
    }
}
