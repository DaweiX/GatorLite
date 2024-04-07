/*
 * AnalysisEntrypoint.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android;

import presto.android.gui.GUIAnalysis;
import soot.Scene;
import soot.SootClass;

import java.util.Date;

public class AnalysisEntrypoint {
    private static AnalysisEntrypoint theInstance;

    private AnalysisEntrypoint() {
    }

    public static synchronized AnalysisEntrypoint v() {
        if (theInstance == null) {
            theInstance = new AnalysisEntrypoint();
        }
        return theInstance;
    }

    public void run() {
        System.out.println("[Stat] #Classes: " + Scene.v().getClasses().size() +
                ", #AppClasses: " + Scene.v().getApplicationClasses().size());

        if (Configs.libraryPackages == null || Configs.libraryPackages.isEmpty()) {
            Configs.addLibraryPackage("android.support.*");
            Configs.addLibraryPackage("com.google.android.gms.*");
        }

        for (SootClass c: Scene.v().getClasses()) {
            if (Configs.isLibraryClass(c.getName())) {
                if ((!c.isPhantomClass()) && c.isApplicationClass()) {
                    c.setLibraryClass();
                }
            }
        }

        // Analysis
        GUIAnalysis guiAnalysis = GUIAnalysis.v();
        guiAnalysis.run();
        Date endTime = new Date();
        System.out.println("Soot stopped on " + endTime);
        System.exit(0);
    }
}
