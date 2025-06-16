package com.flowframe.util;

public class StewFlagTracker {
    private static final ThreadLocal<Boolean> justAteSuspiciousStew = ThreadLocal.withInitial(() -> false);

    public static void setJustAteSuspiciousStew() {
        justAteSuspiciousStew.set(true);
    }

    public static boolean justAteSuspiciousStew() {
        boolean val = justAteSuspiciousStew.get();
        justAteSuspiciousStew.set(false);
        return val;
    }
}
