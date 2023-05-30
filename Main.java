/*
 * Main.java - part of the GATOR project
 *
 * Copyright (c) 2014, 2015 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */
package presto.android;

import presto.android.Configs.AsyncOpStrategy;
import soot.Pack;
import soot.PackManager;
import soot.SceneTransformer;
import soot.Transform;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        parseArgs(args);
        setupAndInvokeSoot();
    }

    public static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if ("-project".equals(s)) {
                Configs.project = args[++i];
            } else if ("-benchmarkName".equals(s)) {
                Configs.benchmarkName = args[++i];
            } else if ("-sdkDir".equals(s)) {
                Configs.sdkDir = args[++i];
            } else if ("-apiLevel".equals(s)) {
                Configs.apiLevel = args[++i];
            } else if ("-android".equals(s)) {
                Configs.android = args[++i];
            } else if ("-jre".equals(s)) {
                Configs.jre = args[++i];
            } else if ("-debugCode".equals(s)) {
                Configs.debugCodes.add(args[++i]);
            } else if ("-client".equals(s)) {
                Configs.clients.add(args[++i]);
            } else if ("-withCHA".equals(s)) {
                Configs.withCHA = true;
            } else if ("-listenerSpecFile".equals(s)) {
                Configs.listenerSpecFile = args[++i];
            } else if ("-wtgSpecFile".equals(s)) {
                Configs.wtgSpecFile = args[++i];
            } else if ("-implicitIntent".equals(s)) {
                Configs.implicitIntent = true;
            } else if ("-instrument".equals(s)) {
                Configs.instrument = true;
            } else if ("-resolveContext".equals(s)) {
                Configs.resolveContext = true;
            } else if ("-trackWholeExec".equals(s)) {
                Configs.trackWholeExec = true;
            } else if ("-worker".equals(s)) {
                Configs.workerNum = Integer.parseInt(args[++i]);
                if (!(Configs.workerNum > 0)) {
                    System.out.println("[Error]: number of workers should be >= 1");
                    throw new RuntimeException();
                }
            } else if ("-mockScene".equals(s)) {
                Configs.mockScene = true;
            } else if ("-hardwareEvent".equals(s)) {
                Configs.hardwareEvent = true;
            } else if ("-detectLeak".equals(s)) {
                Configs.detectLeak = Integer.parseInt(args[++i]);
            } else if ("-succDepth".equals(s)) {
                Configs.sDepth = Integer.parseInt(args[++i]);
                if (!(Configs.sDepth > 0)) {
                    System.out.println("[Error]: number of succDepth should be >= 1");
                    throw new RuntimeException();
                }
            } else if ("-allowLoop".equals(s)) {
                Configs.allowLoop = true;
            } else if ("-epDepth".equals(s)) {
                Configs.epDepth = Integer.parseInt(args[++i]);
            } else if ("-clientParam".equals(s)) {
                Configs.clientParams.add(args[++i]);
            } else if ("-async".equals(s)) {
                Configs.asyncStrategy = AsyncOpStrategy.valueOf(args[++i]);
            } else if ("-genTestCase".equals(s)) {
                Configs.genTestCase = true;
            } else if ("-outputFile".equals(s)){
                Configs.pathoutfilename = args[++i];
            } else if ("-monitoredClass".equals(s)){
                Configs.monitoredClass = args[++i];
            } else if ("-libraryPackageListFile".equals(s)) {
                Configs.libraryPackageFile = args[++i];
            } else if ("-libraryPackageName".equals(s)) {
                Logger.verb("VERB", "append pkg " + args[i + 1]);
                Configs.addLibraryPackage(args[++i]);
            } else if ("-classListFile".equals(s)) {
                Configs.classListFile = args[++i];
            } else if ("-classFiles".equals(s)) {
                Configs.classfileLocation = args[++i];
            } else if ("-manifestFile".equals(s)) {
                Configs.manifestLocation = args[++i];
            } else if ("-resourcePath".equals(s)) {
                Configs.resourceLocation = args[++i];
            }else {
                throw new RuntimeException("Unknown option: " + s);
            }
        }
        Configs.processing();
    }

    static String computeClasspath() {
        // Compute classpath
        StringBuilder classpathBuffer =
                new StringBuilder(Configs.android + ":" + Configs.jre);
        for (String s : Configs.depJars) {
            classpathBuffer.append(":").append(s);
        }

        for (String s : Configs.extLibs) {
            classpathBuffer.append(":").append(s).append("/bin/classes");
        }

        return classpathBuffer.toString();
    }

    static void setupAndInvokeSoot() {
        String classpath = computeClasspath();
        // set up an artificial phase to call into our analysis entrypoint. We can
        // run it with or without call graph construction (CHA is chosen here).
        if (Configs.withCHA) {
            String packName = "wjtp";
            String phaseName = "wjtp.gui";
            String[] sootArgs = {
                    "-w",
                    "-p", "cg", "all-reachable:true",
                    "-p", "cg.cha", "enabled:true",
                    "-p", phaseName, "enabled:true",
                    "-f", "n",
                    "-keep-line-number",
                    "-process-multiple-dex",
                    "-allow-phantom-refs",
                    "-process-dir", Configs.bytecodes,
                    "-cp", classpath,
            };
            readWidgetMap();
            PrerunEntrypoint.v().run();
            setupAndInvokeSootHelper(packName, phaseName, sootArgs);
        } else {
            String packName = "cg";
            String phaseName = "cg.gui";
            String[] sootArgs = {
                    "-w",
                    "-p", phaseName, "enabled:true",
                    "-f", "n",
                    "-keep-line-number",
                    "-allow-phantom-refs",
                    "-process-multiple-dex",
                    "-process-dir", Configs.bytecodes,
                    "-cp", classpath,
            };
            readWidgetMap();
            PrerunEntrypoint.v().run();
            setupAndInvokeSootHelper(packName, phaseName, sootArgs);
        }
    }

    /**
     * Prepare a soot plugin that calls into our analysis entrypoint, and then
     * invoke soot with the plugin enabled.
     */
    static void setupAndInvokeSootHelper(String packName, String phaseName,
                                         String[] sootArgs) {
        // Create the phase and add it to the pack
        Pack pack = PackManager.v().getPack(packName);
        pack.add(new Transform(phaseName, new SceneTransformer() {
            @Override
            protected void internalTransform(String phaseName,
                                             Map<String, String> options) {
                AnalysisEntrypoint.v().run();
            }
        }));

        soot.Main.main(sootArgs);
    }

    static void readWidgetMap() {
        //This is an on demand implementation of signature patch
        try {
            String GatorRootPath = System.getenv("GatorRoot");
            FileReader fr = new FileReader(GatorRootPath + Configs.widgetMapFile);
            BufferedReader br = new BufferedReader(fr);
            String curLine;
            while ((curLine = br.readLine()) != null) {
                String[] curLineArr = curLine.split(",");
                if (curLineArr.length != 2) {
                    System.out.println("[MAP] Str: " + curLine + " is not a valid map");
                }
                if (Configs.widgetMap.containsKey(curLineArr[0])) {
                    System.out.println("[MAP] Str: collision at key " + curLineArr[0]);
                } else {
                    Configs.widgetMap.put(curLineArr[0], curLineArr[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
