/*
 * GUIHierarchyPrinterClient.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android.gui;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;

import presto.android.Configs;
import presto.android.gui.rep.GUIHierarchy;
import presto.android.gui.rep.GUIHierarchy.Activity;
import presto.android.gui.rep.GUIHierarchy.Dialog;
import presto.android.gui.rep.GUIHierarchy.EventAndHandler;
import presto.android.gui.rep.GUIHierarchy.View;
import presto.android.gui.rep.StaticGUIHierarchy;
import soot.Scene;
import soot.SootMethod;

public class GUIHierarchyPrinterClient {
    GUIAnalysisOutput output;
    GUIHierarchy guiHierarchy;

    private PrintStream out;
    private int indent;

    void printf(String format, Object... args) {
        for (int i = 0; i < indent; i++) {
            out.print(' ');
        }
        out.printf(format, args);
    }

    public void run(GUIAnalysisOutput output) {
        this.output = output;
        guiHierarchy = new StaticGUIHierarchy(output);

        // Init the file io
        try {
            String path = Paths.get(
                    Configs.outFile,
                    Configs.benchmarkName.replace(".apk", ""),
                    "event.xml").toAbsolutePath().toString();
            System.out.printf("Out file: %s%n", path);
            File file = new File(path);
            boolean ignored = file.createNewFile();
            out = new PrintStream(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Start printing
        printf("<GUIHierarchy app=\"%s\">\n", guiHierarchy.app);
        printActivities();
        printDialogs();
        printf("</GUIHierarchy>\n");

        // Finish
        out.flush();
        out.close();
    }

    void printRootViewAndHierarchy(ArrayList<View> roots) {
        indent += 2;
        for (View rootView : roots) {
            printView(rootView);
        }
        indent -= 2;
    }

    void printActivities() {
        for (Activity act : guiHierarchy.activities) {
            indent += 2;
            printf("<Activity name=\"%s\">\n", act.name);

            // Roots & view hierarchy (including OptionsMenu)
            printRootViewAndHierarchy(act.views);

            printf("</Activity>\n");
            indent -= 2;
        }
    }

    void printDialogs() {
        for (Dialog dialog : guiHierarchy.dialogs) {
            indent += 2;
            printf("<Dialog name=\"%s\" allocLineNumber=\"%d\" allocStmt=\"%s\" allocMethod=\"%s\">\n",
                    dialog.name, dialog.allocLineNumber,
                    xmlSafe(dialog.allocStmt), xmlSafe(dialog.allocMethod));
            printRootViewAndHierarchy(dialog.views);
            printf("</Dialog>\n");
            indent -= 2;
        }
    }

    public String xmlSafe(String s) {
        return s
                .replaceAll("&", "&amp;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&apos;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");

    }

    // WARNING: remember to remove the node before exit. Very prone to error!!!
    void printView(View view) {
        // <View type=... id=... idName=... text=... title=...>
        //   <View ...>
        //     ...
        //   </View>
        //   <EventAndHandler event=... handler=... />
        // </View>

        String type = String.format(" type=\"%s\"", view.type);
        String id = String.format(" id=\"%d\"", view.id);
        String idName = String.format(" idName=\"%s\"", view.idName);
        String head =
                String.format("<View%s%s%s>\n", type, id, idName);
        printf(head);

        // This includes both children and context menus
        for (View child : view.views) {
            indent += 2;
            printView(child);
            indent -= 2;
        }
        // Events and handlers
        for (EventAndHandler eventAndHandler : view.eventAndHandlers) {
            indent += 2;
            String handler = eventAndHandler.handler;
            String safeRealHandler = "";
            if (handler.startsWith("<FakeName_")) {
                SootMethod fake = Scene.v().getMethod(handler);
                SootMethod real = output.getRealHandler(fake);
                safeRealHandler = String.format(
                        " realHandler=\"%s\"", xmlSafe(real.getSignature()));
            }
            printf("<EventAndHandler event=\"%s\" handler=\"%s\"%s />\n",
                    eventAndHandler.event, xmlSafe(eventAndHandler.handler), safeRealHandler);
            indent -= 2;
        }
        printf("</View>\n");
    }
}
