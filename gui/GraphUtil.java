/*
 * GraphUtil.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import presto.android.gui.graph.NNode;
import presto.android.gui.graph.NOpNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class GraphUtil {
  public static boolean verbose;
  private static GraphUtil instance;
  private GraphUtil() {}

  public static synchronized GraphUtil v() {
    if (instance == null) {
      instance = new GraphUtil();
    }
    return instance;
  }

  public Set<NNode> reachableNodes(NNode n) {
    Set<NNode> res = Sets.newHashSet();
    findReachableNodes(n, res);
    return res;
  }

  public void findReachableNodes(NNode start, Set<NNode> reachableNodes) {
    LinkedList<NNode> workList = Lists.newLinkedList();
    workList.add(start);
    reachableNodes.add(start);
    while (!workList.isEmpty()) {
      NNode n = workList.remove();
      for (NNode s : n.getSuccessors()) {
        if (reachableNodes.contains(s)) {
          continue;
        }
        if (!(s instanceof NOpNode)) {
          workList.add(s);
        }
        reachableNodes.add(s);
      }
    }
  }

  public Set<NNode> backwardReachableNodes(NNode n) {
    Set<NNode> res = Sets.newHashSet();
    findBackwardReachableNodes(n, res);
    return res;
  }

  public void findBackwardReachableNodes(NNode start, Set<NNode> reachableNodes) {
    LinkedList<NNode> worklist = Lists.newLinkedList();
    worklist.add(start);
    reachableNodes.add(start);
    while (!worklist.isEmpty()) {
      NNode n = worklist.remove();
      for (NNode s : n.getPredecessors()) {
        if (reachableNodes.contains(s)) {
          continue;
        }
        if (s instanceof NOpNode) {
          if (!(start instanceof NOpNode)) {
            reachableNodes.add(s);
          }
        } else {
          worklist.add(s);
          reachableNodes.add(s);
        }
      }
    }
  }

  public Set<NNode> descendantNodes(NNode n) {
    Set<NNode> res = Sets.newHashSet();
    findDescendantNodes(n, res);
    return res;
  }

  public void findDescendantNodes(NNode start, Set<NNode> descendantNodes) {
    LinkedList<NNode> worklist = Lists.newLinkedList();
    worklist.add(start);
    descendantNodes.add(start);
    while (!worklist.isEmpty()) {
      NNode n = worklist.remove();
      for (NNode s : n.getChildren()) {
        if (descendantNodes.contains(s)) {
          continue;
        }
        worklist.add(s);
        descendantNodes.add(s);
      }
    }
  }

  // ///
  public Set<NNode> ancestorNodes(NNode n) {
    Set<NNode> res = Sets.newHashSet();
    findAncestorNodes(n, res);
    return res;
  }

  public void findAncestorNodes(NNode start, Set<NNode> ancestorNodes) {
    LinkedList<NNode> worklist = Lists.newLinkedList();
    worklist.add(start);
    ancestorNodes.add(start);
    while (!worklist.isEmpty()) {
      NNode n = worklist.remove();
      for (Iterator<NNode> iter = n.getParents(); iter.hasNext();) {
        NNode s = iter.next();
        if (ancestorNodes.contains(s)) {
          continue;
        }
        worklist.add(s);
        ancestorNodes.add(s);
      }
    }
  }
}
