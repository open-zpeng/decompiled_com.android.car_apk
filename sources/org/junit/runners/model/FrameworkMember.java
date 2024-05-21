package org.junit.runners.model;

import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.runners.model.FrameworkMember;
/* loaded from: classes3.dex */
public abstract class FrameworkMember<T extends FrameworkMember<T>> implements Annotatable {
    public abstract Class<?> getDeclaringClass();

    protected abstract int getModifiers();

    public abstract String getName();

    public abstract Class<?> getType();

    abstract boolean isShadowedBy(T t);

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean isShadowedBy(List<T> members) {
        for (T each : members) {
            if (isShadowedBy((FrameworkMember<T>) each)) {
                return true;
            }
        }
        return false;
    }

    public boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    public boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }
}
