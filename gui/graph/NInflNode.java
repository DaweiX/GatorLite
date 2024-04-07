/*
 * NInflNode.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui.graph;

import soot.SootClass;

public class NInflNode extends NObjectNode {
  public SootClass c; // the subclass of android.view.View

  @Override
  public SootClass getClassType() {
    return c;
  }

  @Override
  public String toString() {
    StringBuilder p = new StringBuilder();
    if (parents == null) {
      p = new StringBuilder("*]");
    } else if (parents.size() == 1) {
      p = new StringBuilder(parents.iterator().next().id + "]");
    } else {
      for (NNode n : parents) {
        p.append(n.id).append(";");
      }
      p.append("]");
    }
    return "INFL[" + c + "," + (idNode == null ? "*" : idNode) + "," + p + id;
  }
}
