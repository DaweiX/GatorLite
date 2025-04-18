/*
 * FixpointSolver.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.*;

import presto.android.*;
import presto.android.Hierarchy;
import presto.android.gui.graph.NActivityNode;
import presto.android.gui.graph.NAddView1OpNode;
import presto.android.gui.graph.NAddView2OpNode;
import presto.android.gui.graph.NAnonymousIdNode;
import presto.android.gui.graph.NContextMenuNode;
import presto.android.gui.graph.NDialogNode;
import presto.android.gui.graph.NFindView1OpNode;
import presto.android.gui.graph.NFindView1OpNode.FindView1Type;
import presto.android.gui.graph.NFindView2OpNode;
import presto.android.gui.graph.NFindView3OpNode;
import presto.android.gui.graph.NFindView3OpNode.FindView3Type;
import presto.android.gui.graph.NIdNode;
import presto.android.gui.graph.NInflNode;
import presto.android.gui.graph.NInflate1OpNode;
import presto.android.gui.graph.NInflate2OpNode;
import presto.android.gui.graph.NLayoutIdNode;
import presto.android.gui.graph.NMenuIdNode;
import presto.android.gui.graph.NMenuInflateOpNode;
import presto.android.gui.graph.NMenuNode;
import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NObjectNode;
import presto.android.gui.graph.NOpNode;
import presto.android.gui.graph.NOptionsMenuNode;
import presto.android.gui.graph.NSetIdOpNode;
import presto.android.gui.graph.NSetListenerOpNode;
import presto.android.gui.graph.NVarNode;
import presto.android.gui.graph.NViewAllocNode;
import presto.android.gui.graph.NWidgetIdNode;
import presto.android.gui.graph.NWindowNode;
import presto.android.gui.listener.ListenerSpecification;
import presto.android.xml.AndroidView;
import presto.android.xml.XMLParser;
import soot.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

/**
 * Fixed-point computation to resolve the mutual-dependence between NOpNode
 * nodes. First, it performs simple graph reachability. Then, it does the actual
 * fixed-point computation. Right now, it is iterative but seems to be pretty
 * efficient.
 */
public class FixpointSolver {
    public FlowGraph flowgraph;

    JimpleUtil jimpleUtil;
    GraphUtil graphUtil;

    ListenerSpecification listenerSpecs = ListenerSpecification.v();

    // Maps to record operation nodes where certain ID constants are used.
    public Map<NOpNode, Set<NLayoutIdNode>> reachingLayoutIds;
    public Map<NOpNode, Set<NMenuIdNode>> reachingMenuIds;
    public Map<NOpNode, Set<NIdNode>> reachingViewIds;

    // Record view objects used as parameter or receiver at operation nodes.
    public Map<NOpNode, Set<NNode>> reachingParameterViews;
    public Map<NOpNode, Set<NNode>> reachingReceiverViews;

    // Record operation nodes where certain special object nodes are used.
    public Map<NOpNode, Set<NWindowNode>> reachingWindows = Maps.newHashMap();
    public Map<NOpNode, Set<NOptionsMenuNode>> reachingOptionsMenus;
    public Map<NOpNode, Set<NContextMenuNode>> reachingContextMenus;

    // NOpNodes that flow into some other NOpNode (Inflate1, FindView1/2/3) as a
    // parameter or a receiver. Key is source, and value is target.
    public Map<NOpNode, Set<NOpNode>> reachedParameterViews;
    public Map<NOpNode, Set<NOpNode>> reachedReceiverViews;

    // NListenerAllocNode or node of framework-managed objects (activities,
    // views)
    public Map<NOpNode, Set<NNode>> reachingListeners;
    // NOpNodes that flow into SetListener as a listener parameter;
    public Map<NOpNode, Set<NOpNode>> reachedListeners;

    // NViewAllocNode or NInflNode that flow into some NOpNode as a parameter
    public Map<NOpNode, Set<NNode>> solutionParameters;
    // NViewAllocNode or NInflNode that flow into some NOpNode as a receiver
    public Map<NOpNode, Set<NNode>> solutionReceivers;
    // NViewAllocNode or NInflNode that flow to the lhs of other NOpNode
    public Map<NOpNode, Set<NNode>> solutionResults;

    // NListenerAllocNode, NViewAllocNode, NInflNode, NActivityNode that can be
    // a parameter listener object to some SetListener NOpNode
    public Map<NOpNode, Set<NNode>> solutionListeners;

    // Mapping from an activity to the set of views that could be its root.
    public Map<NActivityNode, Set<NNode>> activityRoots;
    public Map<NDialogNode, Set<NNode>> dialogRoots = Maps.newHashMap();

    public Map<NNode, Set<NOpNode>> viewProducers;
    public Map<NObjectNode, Set<NIdNode>> viewLayoutIds;
    public Map<SootClass, Set<Pair<String, String>>> class2IdName;

    public FixpointSolver(FlowGraph g) {
        this.flowgraph = g;
        this.graphUtil = GraphUtil.v();
        this.hier = Hierarchy.v();
        this.jimpleUtil = JimpleUtil.v(this.hier);
        this.class2IdName = new HashMap<>();
    }

    public void solve() {
        reachingLayoutIds = Maps.newHashMap();
        reachingMenuIds = Maps.newHashMap();

        reachingOptionsMenus = Maps.newHashMap();
        reachingContextMenus = Maps.newHashMap();
        reachingViewIds = Maps.newHashMap();

        reachingParameterViews = Maps.newHashMap();
        reachingReceiverViews = Maps.newHashMap();

        reachedParameterViews = Maps.newHashMap();
        reachedReceiverViews = Maps.newHashMap();

        reachingListeners = Maps.newHashMap();
        reachedListeners = Maps.newHashMap();

        solutionParameters = Maps.newHashMap();
        solutionReceivers = Maps.newHashMap();
        solutionResults = Maps.newHashMap();

        solutionListeners = Maps.newHashMap();
        for (NOpNode n : NOpNode.getNodes(NSetListenerOpNode.class)) {
            solutionListeners.put(n, new HashSet<>());
        }

        activityRoots = Maps.newHashMap();

        viewProducers = Maps.newHashMap();
        viewLayoutIds = Maps.newHashMap();

        solveCore();
    }

    void computePathsFromViewProducerToViewConsumer() {
        viewAndListenerAsParameterAndReceiverReachability();

        // reverse
        reverseParameterReachability();
        reverseReceiverReachability();
        reverseListenerReachability();

        // solution
        solutionParameterReachability();
        solutionReceiverReachability();
        solutionResultsReachability();
        solutionListenersReachability();
    }

    public void solveCore() {
        System.out.println("    Solve: 1");
        layoutIdReachability();
        menuIdReachability();
        windowReachability();
        optionsMenuReachability();
        contextMenuReachability();
        viewIdReachability();

        System.out.println("    Solve: 2");
        computePathsFromViewProducerToViewConsumer();

        // process inflate calls
        System.out.println("    Solve: 3");
        processInflateCalls();

        // propagation
        System.out.println("    Solve: 4");
        viewAndListenerPropagation();
    }

    // AddView1, AddView2
    void solutionParameterReachability() {
        for (Map.Entry<NOpNode, Set<NNode>> entry : reachingParameterViews.entrySet()) {
            NOpNode key = entry.getKey();
            Set<NNode> value = solutionParameters.get(key);
            for (NNode n : entry.getValue()) {
                if (!(n instanceof NObjectNode)) {
                    continue;
                }
                if (!isValidFlowByType(n, key, VarType.Parameter)) {
                    continue;
                }
                if (n instanceof NViewAllocNode || n instanceof NInflNode) {
                    if (value == null) {
                        value = Sets.newHashSet();
                        solutionParameters.put(key, value);
                    }
                    value.add(n);
                } else {
                    if (Configs.sanityCheck) {
                        throw new RuntimeException("Unhandled reaching parameter at " + key + " for " + n);
                    } else {
                        Logger.verb("WARNING", "Unhandled reaching parameter at " + key + " for " + n);
                    }
                }
            }
        }
    }

    // AddView2, SetId, SetText, FindView1, FindView3
    void solutionReceiverReachability() {
        for (Map.Entry<NOpNode, Set<NNode>> entry : reachingReceiverViews.entrySet()) {
            NOpNode src = entry.getKey();
            Set<NNode> receivers = solutionReceivers.get(src);
            for (NNode tgt : entry.getValue()) {
                if (!(tgt instanceof NObjectNode)) {
                    continue;
                }
                if (!isValidFlowByType(tgt, src, VarType.Receiver)) {
                    continue;
                }
                if (tgt instanceof NViewAllocNode || tgt instanceof NOptionsMenuNode ||
                        tgt instanceof NContextMenuNode || tgt instanceof NInflNode) {
                    if (receivers == null) {
                        receivers = Sets.newHashSet();
                        solutionReceivers.put(src, receivers);
                    }
                    receivers.add(tgt);
                } else {
                    if (Configs.sanityCheck) {
                        throw new RuntimeException("Unhandled reaching receiver at " + src + " for " + tgt);
                    } else {
                        Logger.verb("WARNING", "Unhandled reaching receiver at " + src + " for " + tgt);
                    }
                }
            }
        }
    }

    // Inflate1, FindView1/2/3
    void solutionResultsReachability() {
        solutionResultsReachability(NOpNode.getNodes(NInflate1OpNode.class));
        solutionResultsReachability(NOpNode.getNodes(NFindView1OpNode.class));
        solutionResultsReachability(NOpNode.getNodes(NFindView2OpNode.class));
        solutionResultsReachability(NOpNode.getNodes(NFindView3OpNode.class));
    }

    // Add empty set to results map
    void solutionResultsReachability(Set<NOpNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (NOpNode n : nodes) {
            solutionResults.computeIfAbsent(n, k -> Sets.newHashSet());
        }
    }

    void solutionListenersReachability() {
        // SetListener
        for (Map.Entry<NOpNode, Set<NNode>> entry : reachingListeners.entrySet()) {
            NOpNode setListener = entry.getKey();
            Set<NNode> reachables = entry.getValue();
            for (NNode n : reachables) {
                if (!(n instanceof NObjectNode)) {
                    continue;
                }
                NObjectNode listenerObject = (NObjectNode) n;
                // Sanity check. If fail, exception.
                if (graphUtil.reachableNodes(listenerObject).contains(setListener.getParameter())) {
                    if (!listenerSpecs.isListenerType(listenerObject.getClassType())) {
                        continue;
                    }
                    MultiMapUtil.addKeyAndHashSetElement(solutionListeners, setListener, n);
                } else {
                    if (Configs.sanityCheck) {
                        throw new RuntimeException("Unhandled reaching listeners at " + setListener + " for " + n);
                    } else {
                        Logger.verb("WARNING", "Unhandled reaching listeners at " + setListener + " for " + n);
                    }
                }
            }
        }
    }

    void reverseReachability(Map<NOpNode, Set<NNode>> map, Map<NOpNode, Set<NOpNode>> reverseMap) {
        for (Map.Entry<NOpNode, Set<NNode>> entry : map.entrySet()) {
            NOpNode target = entry.getKey();
            for (NNode n : entry.getValue()) {
                if (!(n instanceof NOpNode)) {
                    continue;
                }
                NOpNode source = (NOpNode) n;
                Set<NOpNode> targetSet = reverseMap.computeIfAbsent(source, k -> Sets.newHashSet());
                targetSet.add(target);
            }
        }
    }

    void reverseParameterReachability() {
        reverseReachability(reachingParameterViews, reachedParameterViews);
    }

    void reverseReceiverReachability() {
        reverseReachability(reachingReceiverViews, reachedReceiverViews);
    }

    void reverseListenerReachability() {
        reverseReachability(reachingListeners, reachedListeners);
    }

    /**
     * Look at all layout IDs and see what operation nodes use them. Both
     * application and framework layout IDs are looked at. However, operation
     * nodes are only created for application code. The result is saved in the
     * map reachingLayoutIds.
     */
    void layoutIdReachability() {
        for (NLayoutIdNode layoutIdNode : flowgraph.allNLayoutIdNodes.values()) {
            for (NNode target : graphUtil.reachableNodes(layoutIdNode)) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                // NOTE: setId() could use layout id as parameter as well.
                if (opNode.consumesLayoutId()) {
                    Set<NLayoutIdNode> layouts = reachingLayoutIds.computeIfAbsent(opNode, k -> Sets.newHashSet());
                    layouts.add(layoutIdNode);
                } else {
                    // TODO Temp workaround for variable reuse issue
                    if (Configs.sanityCheck) {
                        throw new RuntimeException(layoutIdNode + " reaching " + opNode);
                    } else {
                        Logger.verb("WARNING",
                                layoutIdNode + " reaching " + opNode + " in layoutIdReachability" + " method");
                    }
                }
            }
        }
    }

    /**
     * Look at all menu IDs and see what operation nodes use them.
     */
    void menuIdReachability() {
        for (NMenuIdNode menuIdNode : flowgraph.allNMenuIdNodes.values()) {
            for (NNode target : graphUtil.reachableNodes(menuIdNode)) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                if (opNode.consumesMenuId()) {
                    Set<NMenuIdNode> menus = reachingMenuIds.computeIfAbsent(opNode, k -> Sets.newHashSet());
                    menus.add(menuIdNode);
                } else {
                    // TODO Temp workaround for variable reuse issue
                    if (Configs.sanityCheck) {
                        throw new RuntimeException(menuIdNode + " reaching " + opNode);
                    } else {
                        Logger.verb("WARNING",
                                menuIdNode + " reaching " + opNode + " in menuIdReachability" + " method");
                    }
                }
            }
        }
    }

    void windowReachability() {
        for (NWindowNode windowNode : NWindowNode.windowNodes) {
            Set<NNode> reachables = graphUtil.reachableNodes(windowNode);
            for (NNode target : reachables) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                if ((opNode instanceof NInflate2OpNode || opNode instanceof NAddView1OpNode
                        || opNode instanceof NFindView2OpNode) && reachables.contains(opNode.getReceiver())) {
                    MultiMapUtil.addKeyAndHashSetElement(reachingWindows, opNode, windowNode);
                } else if (opNode instanceof NSetListenerOpNode && reachables.contains(opNode.getParameter())) {
                    MultiMapUtil.addKeyAndHashSetElement(reachingListeners, opNode, windowNode);
                }
            }
        }
    }

    void optionsMenuReachability() {
        for (NOptionsMenuNode optionsMenu : flowgraph.activityClassToOptionsMenu.values()) {
            for (NNode target : graphUtil.reachableNodes(optionsMenu)) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView3OpNode
                        || opNode instanceof NAddView2OpNode || opNode instanceof NMenuInflateOpNode) {
                    Set<NOptionsMenuNode> optionsMenus = reachingOptionsMenus.computeIfAbsent(opNode, k -> Sets.newHashSet());
                    optionsMenus.add(optionsMenu);
                } else {
                    String fullClassName = Thread.currentThread().getStackTrace()[2].getClassName();
                    String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
                    int lineNumber = Thread.currentThread().getStackTrace()[2].getLineNumber();
                    Logger.verb("WARNING", String.format("OptionsMenu Reached %s at %s at %s at line %d",
                            opNode, fullClassName, methodName, lineNumber));
                }
            }
        }
    }

    void contextMenuReachability() {
        for (NContextMenuNode contextMenu : flowgraph.menuVarNodeToContextMenus.values()) {
            contextMenuReachability(contextMenu);
        }
    }

    Set<NOpNode> contextMenuReachability(NContextMenuNode contextMenu) {
        Set<NOpNode> opNodes = Sets.newHashSet();
        for (NNode target : graphUtil.reachableNodes(contextMenu)) {
            if (!(target instanceof NOpNode)) {
                continue;
            }
            NOpNode opNode = (NOpNode) target;
            if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView3OpNode
                    || opNode instanceof NAddView2OpNode || opNode instanceof NMenuInflateOpNode) {
                Set<NContextMenuNode> contextMenus = reachingContextMenus.computeIfAbsent(opNode, k -> Sets.newHashSet());
                contextMenus.add(contextMenu);
                opNodes.add(opNode);
            } else {
                throw new RuntimeException("ContextMenu reaches " + opNode);
            }
        }
        return opNodes;
    }

    void viewIdReachability() {
        for (NWidgetIdNode viewIdNode : flowgraph.allNWidgetIdNodes.values()) {
            for (NNode target : graphUtil.reachableNodes(viewIdNode)) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView2OpNode
                        || opNode instanceof NSetIdOpNode) {
                    Set<NIdNode> views = reachingViewIds.computeIfAbsent(opNode, k -> Sets.newHashSet());
                    views.add(viewIdNode);
                }
            }
        }
        for (NAnonymousIdNode anonymousIdNode : flowgraph.anonymousIdNodes.values()) {
            for (NNode target : graphUtil.reachableNodes(anonymousIdNode)) {
                if (!(target instanceof NOpNode)) {
                    continue;
                }
                NOpNode opNode = (NOpNode) target;
                if (opNode instanceof NFindView1OpNode) {
                    MultiMapUtil.addKeyAndHashSetElement(reachingViewIds, opNode, anonymousIdNode);
                } else {
                    if (Configs.sanityCheck) {
                        throw new RuntimeException(anonymousIdNode + " reaching " + opNode);
                    } else {
                        Logger.verb("WARNING", anonymousIdNode + " reaching " + opNode);
                    }
                }
            }
        }
    }

    // For both AddView1 and AddView2, there is a formal parameter that is a
    // view.
    // In AddView2, this is the *child* to be added.
    void viewAndListenerAsParameterAndReceiverReachability() {
        // Find all nodes that can "produce" view objects
        for (NNode n : flowgraph.allNNodes) {
            if (n instanceof NViewAllocNode || n instanceof NInflNode || n instanceof NOptionsMenuNode
                    || n instanceof NContextMenuNode) {
                parameterAndReceiverViewReachability(n);
            } else if (n instanceof NOpNode) {
                NOpNode opNode = (NOpNode) n;
                if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView2OpNode
                        || opNode instanceof NFindView3OpNode || opNode instanceof NInflate1OpNode) {
                    parameterAndReceiverViewReachability(n);
                }
            }
            // Any object could be a listener
            if (n instanceof NObjectNode) {
                listenerReachability(n);
            }
        }
        // patch in some special cases

        // OptionsMenu as receiver
         propagateOptionsMenuToReceivers();
        // ContextMenu as receiver
         propagateContextMenuToReceivers();
    }

    void propagateOptionsMenuToReceivers() {
        for (Map.Entry<NOpNode, Set<NOptionsMenuNode>> entry : reachingOptionsMenus.entrySet()) {
            NOpNode key = entry.getKey();
            if (key instanceof NMenuInflateOpNode) {
                continue;
            }
            Set<NNode> set = reachingReceiverViews.computeIfAbsent(key, k -> Sets.newHashSet());
            set.addAll(entry.getValue());
        }
    }

    void propagateContextMenuToReceivers() {
        // handle ContextMenu
        for (Map.Entry<NOpNode, Set<NContextMenuNode>> entry : reachingContextMenus.entrySet()) {
            NOpNode key = entry.getKey();
            if (key instanceof NMenuInflateOpNode) {
                continue;
            }
            MultiMapUtil.addKeyAndHashSet(reachingReceiverViews, key, entry.getValue());
        }
    }

    // DDDD
    void parameterAndReceiverViewReachability(NNode source) {
        Set<NNode> reachableNodes = graphUtil.reachableNodes(source);
        for (NNode target : reachableNodes) {
            if (!(target instanceof NOpNode)) {
                continue;
            }
            NOpNode opNode = (NOpNode) target;
            // View as parameter
            if (opNode instanceof NAddView1OpNode
                    || (opNode instanceof NAddView2OpNode && reachableNodes.contains(opNode.getParameter()))) {
                MultiMapUtil.addKeyAndHashSetElement(reachingParameterViews, opNode, source);
            } else {
                // Maybe SetListener
                listenerReachability(source, opNode, reachableNodes);
            }
            // View as receiver
            if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView3OpNode
                    || opNode instanceof NSetIdOpNode) {
                MultiMapUtil.addKeyAndHashSetElement(reachingReceiverViews, opNode, source);
            } else if (opNode instanceof NSetListenerOpNode) {
                SootMethod call = opNode.callSite.getO2();
                MethodHelper mh = new MethodHelper(call);
                SootClass klass = call.getDeclaringClass();
                if (!class2IdName.containsKey(klass)) {
                    class2IdName.put(klass, mh.getClassField());
                }
                Stmt s = opNode.callSite.getO1();
                String uiIdFromUnit = mh.getUIIdFromSetListenerStmt(s);
                if (uiIdFromUnit == null) //noinspection SpellCheckingInspection
                {
                    // TODO: currently we do not handle this.viewXYZ.setListener()
                    // $r0 := @this: com.applovin.impl.adview.az
                    // $r10 = $r0.<com.applovin.impl.adview.az: android.view.View B>
                    // $r13 = new com.applovin.impl.adview.y
                    // specialinvoke $r13.<com.applovin.impl.adview.y: void <init>(com.applovin.impl.adview.az)>($r0)
                    // virtualinvoke $r10.<android.view.View: void setOnClickListener(android.view.View$OnClickListener)>($r13)
                    continue;
                }

                if (!uiIdFromUnit.chars().allMatch(Character::isDigit)) {
                    // if is a field name, we should turn it to numeric id
                    Set<Pair<String, String>> tuple2s = class2IdName.get(klass);
                    if (tuple2s == null) {
                        continue;
                    }
                    for (Pair<String, String> tuple2 : tuple2s) {
                        String id = tuple2.getO1();
                        String name = tuple2.getO2();
                        if (uiIdFromUnit.equals(name)) {
                            uiIdFromUnit = id;
                            break;
                        }
                    }
                }

                NVarNode receiver = opNode.getReceiver();
                if (!reachableNodes.contains(receiver)) continue;
                if (source.toString().contains(uiIdFromUnit)) {
                    MultiMapUtil.addKeyAndHashSetElement(reachingReceiverViews, opNode, source);
                }
            } else if (opNode instanceof NAddView2OpNode) {
                NVarNode receiver = opNode.getReceiver();
                if (!reachableNodes.contains(receiver)) continue;
                MultiMapUtil.addKeyAndHashSetElement(reachingReceiverViews, opNode, source);
            }
        }
    }

    void listenerReachability(NNode source) {
        Set<NNode> reachables = graphUtil.reachableNodes(source);
        for (NNode target : reachables) {
            if (!(target instanceof NOpNode)) {
                continue;
            }
            listenerReachability(source, (NOpNode) target, reachables);
        }
    }

    void listenerReachability(NNode source, NOpNode target, Set<NNode> reachables) {
        if (target instanceof NSetListenerOpNode && reachables.contains(target.getParameter())) {
            // view as listener parameter
            MultiMapUtil.addKeyAndHashSetElement(reachingListeners, target, source);
        }
    }

    // ==== inflater calls
    XMLParser xmlParser;
    Hierarchy hier;

    // This is a hack to bridge the flow gap between framework and app. Right
    // now,
    // we only handle NInflNode, but in general we need to cover other types of
    // nodes (e.g., NViewAllocNode) as well.
    void recordViewProducers(NNode objectNode, NOpNode producerNode) {
        Set<NOpNode> flows = viewProducers.computeIfAbsent(objectNode, k -> Sets.newHashSet());
        flows.add(producerNode);
    }

    void processInflateCalls() {
        xmlParser = XMLParser.Factory.getXMLParser();
        processViewInflateCalls();
        processMenuInflateCalls();
    }

    void addViewToWindowRoot(NWindowNode window, NNode rootView) {
        if (window instanceof NActivityNode) {
            addViewToWindowRoot(activityRoots, (NActivityNode) window, rootView);
        } else if (window instanceof NDialogNode) {
            addViewToWindowRoot(dialogRoots, (NDialogNode) window, rootView);
        } else {
            if (Configs.sanityCheck) {
                throw new RuntimeException("Unknown window " + window);
            } else {
                Logger.verb("WARNING", "Unknown window " + window);
            }
        }
    }

    <E extends NWindowNode> void addViewToWindowRoot(Map<E, Set<NNode>> roots, E window, NNode rootView) {
        Set<NNode> rootSet = roots.computeIfAbsent(window, k -> Sets.newHashSet());
        rootSet.add(rootView);
    }

    void processViewInflateCalls() {
        for (Map.Entry<NOpNode, Set<NLayoutIdNode>> entry : reachingLayoutIds.entrySet()) {
            NOpNode opNode = entry.getKey();
            for (NLayoutIdNode layoutIdNode : entry.getValue()) {
                if (opNode instanceof NInflate1OpNode) {
                    NInflNode inflNode = doLayoutInflate(layoutIdNode.getIdValue(), null);
                    if (inflNode == null) {
                        continue;
                    }
                    solutionResults.get(opNode).add(inflNode);
                    recordViewProducers(inflNode, opNode);

                    inflationEffectsToParameters(opNode, inflNode);
                    inflationEffectsToReceivers(opNode, inflNode);

                } else if (opNode instanceof NInflate2OpNode) {
                    Set<NWindowNode> windows = reachingWindows.get(opNode);
                    if (windows == null || windows.isEmpty()) {
                        continue;
                    }
                    for (NWindowNode windowNode : windows) {
                        NInflNode root = doLayoutInflate(layoutIdNode.getIdValue(), windowNode);
                        if (root == null) {
                            continue;
                        }

                        addViewToWindowRoot(windowNode, root);

                        if(!viewLayoutIds.containsKey(windowNode)){
                            viewLayoutIds.put(windowNode, Sets.newHashSet());
                        }

                        viewLayoutIds.get(windowNode).add(layoutIdNode);

                    }
                }
            }
        }
    }

    void processMenuInflateCalls() {
        for (Map.Entry<NOpNode, Set<NMenuIdNode>> entry : reachingMenuIds.entrySet()) {
            NOpNode opNode = entry.getKey();
            if (!(opNode instanceof NMenuInflateOpNode)) {
                throw new RuntimeException("Menu id reaching non MenuInfalte node - " + opNode);
            }
            for (NMenuIdNode menuId : entry.getValue()) {
                Set<NMenuNode> menuNodes = Sets.newHashSet();
                Set<NOptionsMenuNode> optionsMenu = reachingOptionsMenus.get(opNode);
                Set<NContextMenuNode> contextMenu = reachingContextMenus.get(opNode);
                if (optionsMenu != null) {
                    menuNodes.addAll(optionsMenu);
                }
                if (contextMenu != null) {
                    menuNodes.addAll(contextMenu);
                }
                if (!menuNodes.isEmpty()) {
                    for (NMenuNode menuNode : menuNodes) {
                        doMenuInflate(menuId.getIdValue(), menuNode);
                    }
                }
            }
        }
    }

    void inflationEffectsToParameters(NOpNode opNode, NInflNode inflNode) {
        inflationEffectsToParameters(opNode, inflNode, reachedParameterViews, solutionParameters);
        inflationEffectsToParameters(opNode, inflNode, reachedListeners, solutionListeners);
    }

    void inflationEffectsToParameters(NOpNode opNode, NInflNode inflNode, Map<NOpNode, Set<NOpNode>> reached,
                                      Map<NOpNode, Set<NNode>> solution) {
        for (Map.Entry<NOpNode, Set<NOpNode>> parameterAndCalls : reached.entrySet()) {
            NOpNode parameter = parameterAndCalls.getKey();
            Set<NOpNode> calls = parameterAndCalls.getValue();
            if (!parameter.equals(opNode)) {
                continue;
            }
            // "parameter" flows into "call", and a solution for "parameter" is
            // "inflNode"
            // solutionParameters saves the parameter solution for each call
            // i.e., solutionParameters: call -> set<inflNode>. Same for
            // receivers.
            for (NOpNode call : calls) {
                if (!isValidFlowByType(inflNode, call, VarType.Parameter)) {
                    continue;
                }
                Set<NNode> solutions = solution.computeIfAbsent(call, k -> Sets.newHashSet());
                solutions.add(inflNode);
            }
        }
    }

    void inflationEffectsToReceivers(NOpNode opNode, NInflNode inflNode) {
        for (Map.Entry<NOpNode, Set<NOpNode>> receiverAndCalls : reachedReceiverViews.entrySet()) {
            NOpNode receiver = receiverAndCalls.getKey();
            Set<NOpNode> calls = receiverAndCalls.getValue();
            if (!receiver.equals(opNode)) {
                continue;
            }
            for (NOpNode call : calls) {
                if (!isValidFlowByType(inflNode, call, VarType.Receiver)) {
                    continue;
                }
                Set<NNode> solutions = solutionReceivers.computeIfAbsent(call, k -> Sets.newHashSet());
                solutions.add(inflNode);
            }
        }
    }

    /*
     * Connect the node to <this> of constructor. Note that
     * View.<init>(Context) is only called from code, not from inflation.
     * However, let's pretend all constructors could be called to simplify life.
     * This is fine because we only consider application code.
     */
    void inflationImplicitEffects(NInflNode inflNode) {
        SootClass c = inflNode.c;
        if (c != null && c.isApplicationClass()) {
            for (SootMethod m : c.getMethods()) {
                // constructor
                if (m.isConstructor()) {
                    NVarNode thisNode = flowgraph.varNode(jimpleUtil.thisLocal(m));
                    inflNode.addEdgeTo(thisNode);
                }
            }
            // Try to find onCreateContextMenu
            SootClass matched = hier.matchForVirtualDispatch(MethodNames.viewOnCreateContextMenuSubSig, c);
            if (matched != null && matched.isApplicationClass()) {
                SootMethod m = matched.getMethod(MethodNames.viewOnCreateContextMenuSubSig);
                NVarNode actualViewNode = flowgraph.varNode(jimpleUtil.thisLocal(m));
                inflNode.addEdgeTo(actualViewNode);
                NVarNode formalMenuNode = flowgraph.varNode(jimpleUtil.localForNthParameter(m, 1));
                NContextMenuNode contextMenu = flowgraph.findOrCreateContextMenuNode(m, formalMenuNode, actualViewNode);

                // Add to reachingContextMenus and propagate to
                // reachingReceiverViews
                Set<NOpNode> opNodes = contextMenuReachability(contextMenu);
                for (NOpNode opNode : opNodes) {
                    MultiMapUtil.addKeyAndHashSetElement(solutionReceivers, opNode, contextMenu);
                }
            }
        }
    }

    void inflNodeToReceiverAndParameter(NNode inflNode) {
        Set<NNode> reachables = graphUtil.reachableNodes(inflNode);
        for (NNode n : reachables) {
            if (!(n instanceof NOpNode)) {
                continue;
            }
            NOpNode opNode = (NOpNode) n;
            // View as parameter
            if (opNode instanceof NAddView1OpNode
                    || (opNode instanceof NAddView2OpNode && reachables.contains(opNode.getParameter()))) {
                Set<NNode> parameterSet = solutionParameters.computeIfAbsent(opNode, k -> Sets.newHashSet());
                parameterSet.add(inflNode);
            } else if (opNode instanceof NSetListenerOpNode && reachables.contains(opNode.getParameter())) {
                MultiMapUtil.addKeyAndHashSetElement(solutionListeners, opNode, inflNode);
            }

            // View as receiver
            if (opNode instanceof NFindView1OpNode || opNode instanceof NFindView3OpNode
                    || opNode instanceof NSetIdOpNode
                    || (opNode instanceof NSetListenerOpNode && reachables.contains(opNode.getReceiver()))
                    || (opNode instanceof NAddView2OpNode && reachables.contains(opNode.getReceiver()))) {
                Set<NNode> receiverSet = solutionReceivers.computeIfAbsent(opNode, k -> Sets.newHashSet());
                receiverSet.add(inflNode);
            }
        }
    }

    AndroidView getRootForLayoutId(Integer i) {
        AndroidView res = xmlParser.findViewById(i);
        if (res != null) {
            return res;
        }
        throw new RuntimeException("Cannot find root for id - " + i);
    }

    void doMenuInflate(Integer menuId, NMenuNode optionsOrContextMenu) {
        AndroidView root = getRootForLayoutId(menuId);
        LinkedList<AndroidView> worklist = Lists.newLinkedList();
        LinkedList<NNode> nodeWorklist = Lists.newLinkedList();
        worklist.add(root);
        nodeWorklist.add(null);
        boolean topLevel = true;

        while (!worklist.isEmpty()) {
            AndroidView v = worklist.remove();
            NNode parent = nodeWorklist.remove();
            NNode vNode;
            if (topLevel) {
                vNode = optionsOrContextMenu;
                topLevel = false;
            } else {
                vNode = flowgraph.inflMenuItemNode(v.getSootClass(), v.getAttrs());
                // vNode = flowgraph.inflNode(v.getSootClass());
            }
            // FIXME(tony): we may need to "call" the constructor is custom menu
            // or
            // menuitems inflated.

            // special case: deal with callback to inflated node
            SootClass match = hier.matchForVirtualDispatch("void onFinishInflate()", v.getSootClass());
            if (match != null && match.isApplicationClass()) {
                SootMethod target = hier.virtualDispatch(match.getMethod("void onFinishInflate()"), v.getSootClass());
                NVarNode lhs = flowgraph.varNode(jimpleUtil.thisLocal(target));
                vNode.addEdgeTo(lhs);
            }

            // propagate to receiver and parameter due to the connect to <this>
            inflNodeToReceiverAndParameter(vNode);

            Integer widgetId = v.getId();

            // TO shun down an exception that happened on
            // net.tedstein.AndroSS_17.apk
            // The cause might be Tony's assumption was not right.
            if (flowgraph.allNWidgetIdNodes.containsKey(widgetId) && (vNode instanceof NInflNode)) {
                vNode.idNode = flowgraph.allNWidgetIdNodes.get(widgetId);
            } else if (widgetId != null) {
                // throw new RuntimeException();
                Logger.verb("WARNING", "at doMenuInflate widgetId not found");
                continue;
            }

            vNode.addParent(parent);
            for (Iterator<AndroidView> it = v.getChildren(); it.hasNext();) {
                worklist.add(it.next());
                nodeWorklist.add(vNode);
            }
        } // worklist not empty
    }

    NInflNode doLayoutInflate(Integer layoutId, NNode rootParent) {
        // Step 1: create nodes and edges to represent the widget hierarchy
        AndroidView root = getRootForLayoutId(layoutId);
        NInflNode rootNode = null;
        // these two go in synch
        LinkedList<AndroidView> worklist = Lists.newLinkedList();
        LinkedList<NNode> nodeWorklist = Lists.newLinkedList();
        worklist.add(root);
        nodeWorklist.add(rootParent);
        while (!worklist.isEmpty()) {
            AndroidView v = worklist.remove();
            NNode parent = nodeWorklist.remove();
            if (v.getSootClass() == null) {
                Logger.verb("DEBUG", "AndroidView " + v.getId() + " " + v.getOrigin() + " "
                        + "SootClass is" + " null");
                continue;
            }
            NInflNode vNode = flowgraph.inflNode(v.getSootClass());
            inflationImplicitEffects(vNode);

            // TODO(tony): move the following into inflationImplicitEffects
            // special case: deal with callback to inflated node
            SootClass match = hier.matchForVirtualDispatch("void onFinishInflate()", v.getSootClass());
            if (match != null && match.isApplicationClass()) {
                SootMethod target = hier.virtualDispatch(match.getMethod("void onFinishInflate()"), v.getSootClass());
                NVarNode lhs = flowgraph.varNode(jimpleUtil.thisLocal(target));
                vNode.addEdgeTo(lhs);
            }

            // propagate to receiver and parameter due to the connect to <this>
            inflNodeToReceiverAndParameter(vNode);

            if (root == v) {
                rootNode = vNode;
            }
            Integer widgetId = v.getId();
            if (flowgraph.allNWidgetIdNodes.containsKey(widgetId)) {
                vNode.idNode = flowgraph.allNWidgetIdNodes.get(widgetId);
            } else if (widgetId != null) {
                throw new RuntimeException();
            }

            vNode.addParent(parent);
            for (Iterator<AndroidView> it = v.getChildren(); it.hasNext();) {
                worklist.add(it.next());
                nodeWorklist.add(vNode);
            }
        } // worklist not empty

        return rootNode;
    }

    // Now, we are done with inflation. Let's process other NOpNodes
    void viewAndListenerPropagation() {
        while (true) {
            boolean changed = false;
            for (NOpNode findView1 : NOpNode.getNodes(NFindView1OpNode.class)) {
                if (processFindView1((NFindView1OpNode) findView1)) {
                    changed = true;
                }
            }
            for (NOpNode findView2 : NOpNode.getNodes(NFindView2OpNode.class)) {
                if (processFindView2((NFindView2OpNode) findView2)) {
                    changed = true;
                }
            }
            for (NOpNode findView3 : NOpNode.getNodes(NFindView3OpNode.class)) {
                if (processFindView3((NFindView3OpNode) findView3)) {
                    changed = true;
                }
            }
            for (NOpNode addView1 : NOpNode.getNodes(NAddView1OpNode.class)) {
                if (processAddView1((NAddView1OpNode) addView1)) {
                    changed = true;
                }
            }
            for (NOpNode addView2 : NOpNode.getNodes(NAddView2OpNode.class)) {
                if (processAddView2((NAddView2OpNode) addView2)) {
                    changed = true;
                }
            }
            for (NOpNode setId : NOpNode.getNodes(NSetIdOpNode.class)) {
                if (processSetId((NSetIdOpNode) setId)) {
                    changed = true;
                }
            }
            // SetListener: need to recompute path summary if anything changes
            for (NOpNode setListener : NOpNode.getNodes(NSetListenerOpNode.class)) {
                if (processSetListener((NSetListenerOpNode) setListener)) {
                    changed = true;
                    // recompute the paths
                    computePathsFromViewProducerToViewConsumer();
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    // FindView1: lhs = receiver.findViewById(id)
    boolean processFindView1(NFindView1OpNode node) {
        Set<NIdNode> viewIds = reachingViewIds.get(node);
        if (viewIds == null || viewIds.isEmpty()) {
            if (node.type == FindView1Type.Ordinary) {
                return false;
            }
        }
        Set<NNode> receivers = solutionReceivers.get(node);
        if (receivers == null || receivers.isEmpty()) {
            return false;
        }
        Set<NNode> solution = solutionResults.get(node);
        int oldSize = solution.size();
        for (NNode receiver : receivers) {
            // Compute descendantNodes() to walk the view hierarchy
            Set<NNode> descendants = graphUtil.descendantNodes(receiver);
            boolean found = false;
            if (viewIds != null && !viewIds.isEmpty()) {
                for (NNode lhs : descendants) {
                    NNode idNode = extractIdNode(lhs);
                    if (viewIds.contains(idNode)) {
                        found = true;
                        solution.add(lhs);
                        recordViewProducers(lhs, node);
                    }
                }
            }
            // For Menu.findItem, return all descendants when exact match cannot
            // be
            // found.
            if (!found && node.type == FindView1Type.MenuFindItem) {
                descendants.remove(receiver);
                for (NNode lhs : descendants) {
                    solution.add(lhs);
                    recordViewProducers(lhs, node);
                    // System.out.println(" * lhs-item: " + lhs);
                }
            }
        }
        if (oldSize == solution.size()) {
            return false;
        }
        // Propagate the change to affected nodes
        propagateToParametersAndReceivers(solution, node);
        return true;
    }

    // FindView2: lhs = act/dialog.findViewById(id)
    boolean processFindView2(NFindView2OpNode node) {
        Set<NIdNode> viewIds = reachingViewIds.get(node);
        if (viewIds == null || viewIds.isEmpty()) {
            return false;
        }
        Set<NWindowNode> windows = reachingWindows.get(node);
        if (windows == null || windows.isEmpty()) {
            return false;
        }
        Set<NNode> solution = solutionResults.get(node);
        int oldSize = solution.size();
        for (NWindowNode window : windows) {
            for (NIdNode id : viewIds) {
                if (id.getIdValue().equals(xmlParser.getSystemRIdValue("content"))) {
                    if (!(window instanceof NActivityNode)) {
                        System.out.println("SystemR.id.content used in " + window.toString());
                        Logger.verb("WARNING", "SystemR.id.content used for non-activity object.");
                        continue;
                    }
                    NActivityNode activity = (NActivityNode) window;
                    Set<NNode> roots = activityRoots.get(activity);
                    if (roots != null && !roots.isEmpty()) {
                        solution.addAll(roots);
                        for (NNode r : roots) {
                            recordViewProducers(r, node);
                        }
                    }
                }
            }
            for (NNode root : window.getChildren()) {
                for (NNode lhs : graphUtil.descendantNodes(root)) {
                    NNode idNode = extractIdNode(lhs);
                    if (viewIds.contains(idNode)) {
                        solution.add(lhs);
                        recordViewProducers(lhs, node);
                    }
                }
            }
        }
        if (oldSize == solution.size()) {
            return false;
        }
        // Propagate the change to affected nodes
        propagateToParametersAndReceivers(solution, node);

        return true;
    }

    // FindView3: lhs = view.m()
    boolean processFindView3(NFindView3OpNode node) {
        Set<NNode> solution = solutionResults.get(node);
        int oldSize = solution.size();
        Set<NNode> receiverSet = solutionReceivers.get(node);
        if (receiverSet == null || receiverSet.isEmpty()) {
            return false;
        }
        for (NNode receiver : receiverSet) {
            Set<NNode> descendants;
            FindView3Type type = node.type;
            if (type == FindView3Type.FindChildren) {
                descendants = receiver.getChildren();
            } else if (type == FindView3Type.FindDescendantsAndSelf) {
                descendants = graphUtil.descendantNodes(receiver);
            } else if (type == FindView3Type.FindDescendantsNoSelf) {
                descendants = graphUtil.descendantNodes(receiver);
                descendants.remove(receiver);
            } else {
                throw new RuntimeException("Unknown FindView3 type!");
            }

            for (NNode lhs : descendants) {
                if (type == FindView3Type.FindChildren && lhs.equals(receiver)) {
                    throw new RuntimeException("Unexpected " + node);
                }
                if (!isValidFlowByType(lhs, node, VarType.ReturnValue)) {
                    continue;
                }
                solution.add(lhs);
                recordViewProducers(lhs, node);
            }
        }
        if (oldSize == solution.size()) {
            return false;
        }
        // Propagate the change to affected nodes
        propagateToParametersAndReceivers(solution, node);
        return true;
    }

    // AddView1: act/dialog.setContentView(view)
    boolean processAddView1(NAddView1OpNode node) {
        Set<NWindowNode> windows = reachingWindows.get(node);
        if (windows == null || windows.isEmpty()) {
            System.out.println("[WARNING] Window unknown at " + node);
            return false;
        }
        Set<NNode> viewSet = solutionParameters.get(node);
        if (viewSet == null || viewSet.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (NWindowNode windowNode : windows) {
            for (NNode rootView : viewSet) {
                if (!windowNode.hasChild(rootView)) {
                    changed = true;
                    rootView.addParent(windowNode);

                    addViewToWindowRoot(windowNode, rootView);
                }
            }
        }
        return changed;
    }

    // AddView2: parent.addView(child)
    boolean processAddView2(NAddView2OpNode node) {
        boolean changed = false;
        Set<NNode> parentSet = solutionReceivers.get(node);
        if (parentSet == null || parentSet.isEmpty()) {
            return false;
        }

        Set<NNode> childSet = solutionParameters.get(node);
        if (childSet == null || childSet.isEmpty()) {
            return false;
        }

        for (NNode parent : parentSet) {
            for (NNode child : childSet) {
                if (parent == child) {
                    continue;
                }
                if (!parent.hasChild(child)) {
                    changed = true;
                    child.addParent(parent);
                }
            }
        }
        return changed;
    }

    // SetId: view.setId(id)
    boolean processSetId(NSetIdOpNode node) {
        Set<? extends NIdNode> viewIds = reachingViewIds.get(node);
        if (viewIds == null || viewIds.isEmpty()) {
            // For some reason, layout id can be used in setId() as well
            viewIds = reachingLayoutIds.get(node);
            if (viewIds == null || viewIds.isEmpty()) {
                return false;
            }
        }
        if (viewIds.size() > 1) {
            // TODO(tony): deal with this if necessary
            System.out.println("[WARNING] More than one view id flow to " + node);
        }
        NIdNode idNode = viewIds.iterator().next();
        boolean changed = false;
        Set<NNode> receiverSet = solutionReceivers.get(node);
        if (receiverSet == null || receiverSet.isEmpty()) {
            return false;
        }
        for (NNode receiver : receiverSet) {
            NNode oldIdNode = extractIdNode(receiver);
            if (oldIdNode != null) {
                continue;
            }
            changed = true;
            setIdNode(receiver, idNode);
        }
        return changed;
    }

    boolean processSetListener(NSetListenerOpNode node) {
        Set<NNode> viewSet = solutionReceivers.get(node);
        if (viewSet == null || viewSet.isEmpty()) {
            return false;
        }

        Set<NNode> listenerSet = solutionListeners.get(node);
        if (listenerSet == null || listenerSet.isEmpty()) {
            return false;
        }
        for (NNode view : viewSet) {
            NObjectNode viewObject = (NObjectNode) view;
            for (NNode listener : listenerSet) {
                NObjectNode listenerObject = (NObjectNode) listener;
                if (flowgraph.processSetListenerOpNode(node, viewObject, listenerObject)) {
                    return true;
                }
            }
        }
        return false;
    }

    void propagateViewToParameter(Set<NNode> solution, NOpNode node, Map<NOpNode, Set<NOpNode>> reachedMap,
                                  Map<NOpNode, Set<NNode>> solutionMap) {
        Set<NOpNode> callsWithParameter = reachedMap.get(node);
        if (callsWithParameter != null && !callsWithParameter.isEmpty()) {
            for (NOpNode call : callsWithParameter) {
                if (call.equals(node)) {
                    continue;
                }
                Set<NNode> trueSolution = Sets.newHashSet();
                for (NNode s : solution) {
                    if (isValidFlowByType(s, call, VarType.Parameter)) {
                        trueSolution.add(s);
                    }
                }
                Set<NNode> parameterSolutionSet = solutionMap.computeIfAbsent(call, k -> Sets.newHashSet());
                parameterSolutionSet.addAll(trueSolution);
            }
        }
    }

    void propagateToParametersAndReceivers(Set<NNode> solution, NOpNode node) {
        propagateViewToParameter(solution, node, reachedParameterViews, solutionParameters);
        propagateViewToParameter(solution, node, reachedListeners, solutionListeners);

        Set<NOpNode> callsWithReceiver = reachedReceiverViews.get(node);
        if (callsWithReceiver != null && !callsWithReceiver.isEmpty()) {
            for (NOpNode call : callsWithReceiver) {
                if (call.equals(node)) {
                    continue;
                }
                Set<NNode> trueSolution = Sets.newHashSet();
                for (NNode s : solution) {
                    if (isValidFlowByType(s, call, VarType.Receiver)) {
                        trueSolution.add(s);
                    }
                }
                Set<NNode> receiverSolutionSet = solutionReceivers.computeIfAbsent(call, k -> Sets.newHashSet());
                receiverSolutionSet.addAll(trueSolution);
            }
        }
    }

    NNode extractIdNode(NNode n) {
        if (n instanceof NViewAllocNode || n instanceof NInflNode) {
            return n.idNode;
        } else if (n instanceof NMenuNode) {
            // For the moment, we ignore id of top Menu node.
            return null;
        } else {
            throw new RuntimeException(n.toString());
        }
    }

    void setIdNode(NNode view, NIdNode idNode) {
        if (view instanceof NViewAllocNode || view instanceof NInflNode) {
            view.idNode = idNode;
        } else {
            throw new RuntimeException();
        }
    }

    // type-based filter
    boolean isValidFlowByType(NNode solutionNode, NOpNode call, VarType type) {
        NVarNode targetNode;
        if (type == VarType.Parameter) {
            targetNode = (NVarNode) call.getParameter();
        } else if (type == VarType.Receiver) {
            targetNode = call.getReceiver();
        } else if (type == VarType.ReturnValue) {
            targetNode = call.getLhs();
        } else {
            throw new RuntimeException();
        }
        SootClass sourceType;
        if (solutionNode instanceof NViewAllocNode) {
            sourceType = ((NViewAllocNode) solutionNode).c;
            if (sourceType == null) {
                Logger.verb("ERROR", "in NViewAllocNode branch, classtype is null");
            }
        } else if (solutionNode instanceof NInflNode) {
            sourceType = ((NInflNode) solutionNode).c;
            if (sourceType == null) {
                Logger.verb("ERROR", "in NInflNode branch, classtype is null");
            }
        } else if (solutionNode instanceof NOptionsMenuNode) {
            sourceType = Scene.v().getSootClass("android.view.Menu");
            if (sourceType == null) {
                Logger.verb("ERROR", "in NOptionsMenuNode branch, classtype is null");
            }
        } else if (solutionNode instanceof NContextMenuNode) {
            sourceType = Scene.v().getSootClass("android.view.ContextMenu");
            if (sourceType == null) {
                Logger.verb("ERROR", "in NContextMenuNode branch, classtype is null");
            }
        } else {
            throw new RuntimeException("Unknown solutionNode: " + solutionNode);
        }
        SootClass targetType = ((RefType) targetNode.l.getType()).getSootClass();
        return isCompatible(targetType, sourceType);
    }

    boolean isCompatible(SootClass high, SootClass low) {
        if (high == null && low != null) {
            Logger.verb("ERROR", "in isCompatible, high is null, low is " + low);
        } else if (low == null && high != null) {
            Logger.verb("ERROR", "in isCompatible, low is null, high is " + high);
        } else if (low == null) {
            Logger.verb("ERROR", "in isCompatible, both is null");
        } else {
            if (high.equals(low)) {
                return true;
            }
            if (low.hasSuperclass()) {
                SootClass parent = low.getSuperclass();
                if (isCompatible(high, parent)) {
                    return true;
                }
            }
            for (SootClass parent : low.getInterfaces()) {
                if (isCompatible(high, parent)) {
                    return true;
                }
            }
        }
        return false;
    }

    enum VarType {
        Parameter, Receiver, ReturnValue,
    }

    interface VarExtractor {
        NVarNode extract(NOpNode opNode);
    }

    final VarExtractor resultExtractor = NOpNode::getLhs;

}
