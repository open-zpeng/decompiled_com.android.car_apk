package com.android.car;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import java.util.Collection;
import java.util.HashMap;
/* loaded from: classes3.dex */
public class BinderInterfaceContainer<T extends IInterface> {
    private final HashMap<IBinder, BinderInterface<T>> mBinders;
    private final BinderEventHandler<T> mEventHandler;

    /* loaded from: classes3.dex */
    public interface BinderEventHandler<T extends IInterface> {
        void onBinderDeath(BinderInterface<T> binderInterface);
    }

    /* loaded from: classes3.dex */
    public static class BinderInterface<T extends IInterface> implements IBinder.DeathRecipient {
        public final T binderInterface;
        private final BinderInterfaceContainer<T> mContainer;

        public BinderInterface(BinderInterfaceContainer<T> container, T binderInterface) {
            this.mContainer = container;
            this.binderInterface = binderInterface;
        }

        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            this.binderInterface.asBinder().unlinkToDeath(this, 0);
            this.mContainer.handleBinderDeath(this);
        }
    }

    public BinderInterfaceContainer(BinderEventHandler<T> eventHandler) {
        this.mBinders = new HashMap<>();
        this.mEventHandler = eventHandler;
    }

    public BinderInterfaceContainer() {
        this.mBinders = new HashMap<>();
        this.mEventHandler = null;
    }

    public void addBinder(T binderInterface) {
        IBinder binder = binderInterface.asBinder();
        synchronized (this) {
            if (this.mBinders.get(binder) != null) {
                return;
            }
            BinderInterface<T> bInterface = new BinderInterface<>(this, binderInterface);
            try {
                binder.linkToDeath(bInterface, 0);
                this.mBinders.put(binder, bInterface);
            } catch (RemoteException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public void removeBinder(T binderInterface) {
        IBinder binder = binderInterface.asBinder();
        synchronized (this) {
            BinderInterface<T> bInterface = this.mBinders.get(binder);
            if (bInterface == null) {
                return;
            }
            binder.unlinkToDeath(bInterface, 0);
            this.mBinders.remove(binder);
        }
    }

    public BinderInterface<T> getBinderInterface(T binderInterface) {
        BinderInterface<T> binderInterface2;
        IBinder binder = binderInterface.asBinder();
        synchronized (this) {
            binderInterface2 = this.mBinders.get(binder);
        }
        return binderInterface2;
    }

    public void addBinderInterface(BinderInterface<T> bInterface) {
        IBinder binder = bInterface.binderInterface.asBinder();
        synchronized (this) {
            try {
                try {
                    binder.linkToDeath(bInterface, 0);
                    this.mBinders.put(binder, bInterface);
                } catch (RemoteException e) {
                    throw new IllegalArgumentException(e);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public Collection<BinderInterface<T>> getInterfaces() {
        Collection<BinderInterface<T>> values;
        synchronized (this) {
            values = this.mBinders.values();
        }
        return values;
    }

    public synchronized int size() {
        return this.mBinders.size();
    }

    public synchronized void clear() {
        Collection<BinderInterface<T>> interfaces = getInterfaces();
        for (BinderInterface<T> bInterface : interfaces) {
            IBinder binder = bInterface.binderInterface.asBinder();
            binder.unlinkToDeath(bInterface, 0);
        }
        this.mBinders.clear();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleBinderDeath(BinderInterface<T> bInterface) {
        removeBinder(bInterface.binderInterface);
        BinderEventHandler<T> binderEventHandler = this.mEventHandler;
        if (binderEventHandler != null) {
            binderEventHandler.onBinderDeath(bInterface);
        }
    }
}
