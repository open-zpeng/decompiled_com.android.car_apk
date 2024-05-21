package org.junit.internal;
/* loaded from: classes3.dex */
public class Classes {
    public static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    }
}
