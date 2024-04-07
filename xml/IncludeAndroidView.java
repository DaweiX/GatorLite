/*
 * IncludeAndroidView.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

class IncludeAndroidView implements IAndroidView {
  public AndroidView parent;
  public String layoutId;
  public Integer includeId;

  public IncludeAndroidView(String layoutId, Integer includeId) {
    this.layoutId = layoutId;
    this.includeId = includeId;
  }

  @Override
  public IAndroidView deepCopy() {
      return new IncludeAndroidView(layoutId, includeId);
  }

  @Override
  public void setParent(AndroidView parent) {
    if (this.parent != null) {
      this.parent.removeChildInternal(this);
    }
    this.parent = parent;
    this.parent.addChildInternal(this);
  }
}
