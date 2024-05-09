/*
 * AndroidView.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import presto.android.Configs;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AndroidView implements IAndroidView {
    private AndroidView parent;
    private final ArrayList<IAndroidView> children;

    private SootClass klass;
    private Integer id;

    // Absolute path or full class name where this view is declared.
    private String origin;

    public AndroidView() {
        this.children = Lists.newArrayList();
    }

    @Override
    public IAndroidView deepCopy() {
        AndroidView res = new AndroidView();
        // <src, tgt>
        LinkedList<Pair<AndroidView, AndroidView>> work = Lists.newLinkedList();
        work.add(new Pair<>(this, res));

        while (!work.isEmpty()) {
            Pair<AndroidView, AndroidView> p = work.remove();
            AndroidView src = p.getO1();
            AndroidView tgt = p.getO2();
            tgt.klass = src.klass;
            tgt.id = src.id;
            tgt.origin = src.origin;

            int sz = src.getNumberOfChildren();
            for (int i = 0; i < sz; ++i) {
                IAndroidView newSrc = src.getChildInternal(i);
                if (newSrc instanceof IncludeAndroidView) {
                    IAndroidView newTgt = newSrc.deepCopy();
                    newTgt.setParent(tgt);
                } else {
                    AndroidView newTgt = new AndroidView();
                    newTgt.setParent(tgt);
                    work.add(new Pair<>((AndroidView) newSrc, newTgt));
                }
            }
        }

        return res;
    }

    @Override
    public void setParent(AndroidView parent) {
        setParent(parent, -1);
    }

    public void setParent(AndroidView parent, int i) {
        if (this.parent != null) {
            this.parent.removeChildInternal(this);
        }
        this.parent = parent;
        if (i == -1) {
            parent.addChildInternal(this);
        } else {
            parent.setChildInternal(i, this);
        }
    }

    public static void resolveGUINameSTR(String guiName) {
        if ("view".equals(guiName)) {
            throw new RuntimeException("It shouldn't happen!!!");
        }
        // TODO: read about mechanism of these tags,
        // and get the real thing in.
        if ("merge".equals(guiName) || "fragment".equals(guiName)) {
            guiName = "LinearLayout";
        }

        else if (guiName.equals("View")) {
            guiName = "android.view.View";
        }

        else if (guiName.equals("WebView")) {
            guiName = "android.webkit.WebView";
        }

        else if (guiName.equals("greendroid.widget.ActionBar")) {
            guiName = "greendroid.widget.GDActionBar";
        }

        // there's in fact a com.facebook.android.LoginButton, but
        // it requires build of some other code, which we may not
        // care. FIXME: change this if later we find it necessary.
        else if (guiName.equals("com.facebook.android.LoginButton")) {
            guiName = "com.facebook.widget.LoginButton";
        }

        // this class is marked @hidden in the platform, so we use its super
        // class instead
        else if (guiName.equals("android.widget.NumberPicker$CustomEditText")) {
            guiName = "android.widget.EditText";
        }
        // DONE with special handling
        if (!guiName.contains(".")) {

            String cls = Configs.widgetMap.get(guiName);
            if (cls == null) {
                System.out.println("[RESOLVE] GUI Widget not in the map: " + guiName);
            } else {
                Configs.onDemandClassSet.add(cls);
            }
        } else {
            Configs.onDemandClassSet.add(guiName);
        }

        // this seems safe, but we really need SootClass.BODIES (TODO)
        // Scene.v().tryLoadClass(guiName, SootClass.HIERARCHY);
    }

    public static SootClass resolveGUIName(String guiName) {
        if ("view".equals(guiName)) {
            throw new RuntimeException("It shouldn't happen!!!");
        }
        // TODO: read about mechanism of these tags,
        // and get the real thing in.
        if ("merge".equals(guiName) || "fragment".equals(guiName)) {
            guiName = "LinearLayout";
        }

        else if (guiName.equals("View")) {
            guiName = "android.view.View";
        }

        else if (guiName.equals("WebView")) {
            guiName = "android.webkit.WebView";
        }

        else if (guiName.equals("greendroid.widget.ActionBar")) {
            guiName = "greendroid.widget.GDActionBar";
        }

        // there's in fact a com.facebook.android.LoginButton, but
        // it requires build of some other code, which we may not
        // care. FIXME: change this if later we find it necessary.
        else if (guiName.equals("com.facebook.android.LoginButton")) {
            guiName = "com.facebook.widget.LoginButton";
        }

        // this class is marked @hidden in the platform, so we use its super
        // class instead
        else if (guiName.equals("android.widget.NumberPicker$CustomEditText")) {
            guiName = "android.widget.EditText";
        }

        // DONE with special handling

        SootClass res;

        if (!guiName.contains(".")) {
            String cls = Configs.widgetMap.get(guiName);
            if (cls == null) {
                cls = "android.widget." + guiName;
            }
            if (!classExists(cls)) {
                cls = "android.view." + guiName;
            }
            res = Scene.v().loadClassAndSupport(cls);
        } else {
            res = Scene.v().loadClassAndSupport(guiName);
        }

        // this seems safe, but we really need SootClass.BODIES (TODO)
        // Scene.v().tryLoadClass(guiName, SootClass.HIERARCHY);
        return res;
    }

    static boolean classExists(String className) {
        return SourceLocator.v().getClassSource(className) != null;
    }

    public void save(int guiId, String guiName) {
        Integer i = null;
        if (guiId != -1) {
            i = guiId;
        }
        this.id = i;

        if (!Configs.preRun) {
            try {
                klass = resolveGUIName(guiName);
            } catch (Exception ex) {
                System.err.println("Exception in expanding " + guiName + " in " + guiId);
            }
        } else {
            //Pre-run mode
            resolveGUINameSTR(guiName);
        }
    }

    public AndroidView getParent() {
        return parent;
    }

    public void addChildInternal(IAndroidView node) {
        children.add(node);
    }

    public void removeChildInternal(IAndroidView node) {
        children.remove(node);
    }

    public void setChildInternal(int i, AndroidView child) {
        children.set(i, child);
    }

    public IAndroidView getChildInternal(int i) {
        return children.get(i);
    }

    ArrayList<AndroidView> childrenAfterResolve;

    public Iterator<AndroidView> getChildren() {
        if (childrenAfterResolve == null) {
            childrenAfterResolve = Lists.newArrayList();
            for (IAndroidView v : children) {
                if (!(v instanceof AndroidView)) {
                    throw new RuntimeException("Include not fully resolved.");
                }
                childrenAfterResolve.add((AndroidView) v);
            }
        }
        return childrenAfterResolve.iterator();
    }

    public int getNumberOfChildren() {
        return children.size();
    }

    public SootClass getSootClass() {
        return klass;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    private final HashMap<String, String> attributes = Maps.newHashMap();

    public void addAttr(String attr, String value) {
        attributes.put(attr, value);
    }

    public HashMap<String, String> getAttrs() {
        return attributes;
    }
}
