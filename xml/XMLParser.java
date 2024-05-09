/*
 * XMLParser.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import soot.Scene;
import soot.SootClass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public interface XMLParser {
  abstract class AbstractXMLParser implements XMLParser {
    Map<String, ActivityLaunchMode> activityAndLaunchModes = Maps.newHashMap();

    protected String appPkg;

    protected final ArrayList<String> activities = Lists.newArrayList();
    protected final ArrayList<String> services = Lists.newArrayList();

    protected SootClass mainActivity;

    @Override
    public SootClass getMainActivity() {
      return mainActivity;
    }

    @Override
    public Iterator<String> getActivities() {
      return activities.iterator();
    }

  }

  class Helper {
    public static String getClassName(String classNameFromXml, String appPkg) {
      if ('.' == classNameFromXml.charAt(0)) {
        classNameFromXml = appPkg + classNameFromXml;
      }
      if (!classNameFromXml.contains(".")) {
        classNameFromXml = appPkg + "." + classNameFromXml;
      }
      if (Scene.v().getSootClass(classNameFromXml).isPhantom()) {
        // WARNING: classNameFromXml is declared in manifest, but phantom
        return null;
      }
      return classNameFromXml;
    }
  }

  class Factory {
    public static XMLParser getXMLParser() {
      return DefaultXMLParser.v();
    }
  }
  // layout, id, string, menu xml files

  // R.layout.*
  Set<Integer> getApplicationLayoutIdValues();

  Set<Integer> getSystemLayoutIdValues();

  Integer getSystemRLayoutValue(String layoutName);

  String getApplicationRLayoutName(Integer value);

  String getSystemRLayoutName(Integer value);

  // R.menu.*
  Set<Integer> getApplicationMenuIdValues();

  Set<Integer> getSystemMenuIdValues();

  String getApplicationRMenuName(Integer value);

  String getSystemRMenuName(Integer value);

  // R.id.*
  Set<Integer> getApplicationRIdValues();

  Set<Integer> getSystemRIdValues();

  Integer getSystemRIdValue(String idName);

  String getApplicationRIdName(Integer value);

  String getSystemRIdName(Integer value);

  // R.string.*
  Set<Integer> getStringIdValues();

  String getRStringName(Integer value);

  // AndroidManifest.xml
  SootClass getMainActivity();

  Iterator<String> getActivities();

  enum ActivityLaunchMode {
    standard,
    singleTop,
    singleTask,
    singleInstance
  }

  // Given a view id, find static abstraction of the matched view.
  AndroidView findViewById(Integer id);
}
