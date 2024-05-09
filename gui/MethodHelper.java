package presto.android.gui;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.toolkits.scalar.Pair;

import java.util.HashSet;
import java.util.Set;

public class MethodHelper {
    private PatchingChain<Unit> units;

    public MethodHelper(SootMethod sootMethod) {
        Body body = sootMethod.getActiveBody();
        if (body != null) {
            units = body.getUnits();
        }
    }

    public boolean isEmpty() {
        return units == null;
    }

    public String getUIIdFromSetListenerStmt(Stmt s) {
        // first, we check whether the ui is specified
        // by a class field, if so, return the field name
        if (!(s instanceof InstanceInvokeExpr)) return null;
        InstanceInvokeExpr invokeExpr = (InstanceInvokeExpr) s.getInvokeExpr();
        Value value = invokeExpr.getBaseBox().getValue();
        if (value instanceof FieldRef) {
            return value.toString();
        }
        // if not, search in local vars
        Unit last = units.getPredOf(s);
        while (last != null) {
            s = (Stmt) last;
            if (s instanceof AssignStmt) {
                AssignStmt assignStmt = (AssignStmt) s;
                if (assignStmt.getLeftOp() == value) {
                    Value rightOp = assignStmt.getRightOp();
                    if (rightOp instanceof CastExpr) {
                        value = ((CastExpr) rightOp).getOp();
                    } else if (s.toString().contains("findViewById"))
                    //noinspection SpellCheckingInspection
                    {
                        // $r1 = virtualinvoke $r2.<android.app.Dialog: android.view.View findViewById(int)>(xxx);
                        return s.getInvokeExpr().getArg(0).toString();
                    }
                }
            }
            last = units.getPredOf(last);
        }
        return null;
    }

    public Set<Pair<String, String>> getClassField() {
        Set<Pair<String, String>> classIdName = new HashSet<>();
        Value currentVarName = null;
        String idDeg = null;
        for (Unit unit : units) {
            Stmt s = (Stmt) unit;
            if (s.containsInvokeExpr()) {
                InvokeExpr invokeExpr = s.getInvokeExpr();
                String methodName = invokeExpr.getMethod().getName();

                // find invoke (the first ui hit)
                if (methodName.equals("findViewById")) {
                    idDeg = invokeExpr.getArg(0).toString();
                    if (!s.getDefBoxes().isEmpty()) {
                        currentVarName = s.getDefBoxes().get(0).getValue();
                    }
                }
            } else {
                if (currentVarName != null) {
                    if (!(s instanceof JAssignStmt)) {
                        continue;
                    }
                    // access ui via class fields
                    if (s.containsFieldRef()) {
                        FieldRef field = s.getFieldRef();
                        String refName = field.getFieldRef().name();
                        try {
                            classIdName.add(new Pair<>(idDeg, refName));
                        } catch (NumberFormatException ignored) {}
                        currentVarName = null;
                    } else {
                        // update var name
                        currentVarName = ((JAssignStmt) s).getLeftOpBox().getValue();
                    }
                }
            }
        }
        return classIdName;
    }
}