package com.android.internal.car;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
/* loaded from: classes3.dex */
public interface ICarServiceHelper extends IInterface {
    int forceSuspend(int i) throws RemoteException;

    void setAutoSuspendEnable(boolean z) throws RemoteException;

    /* loaded from: classes3.dex */
    public static class Default implements ICarServiceHelper {
        @Override // com.android.internal.car.ICarServiceHelper
        public int forceSuspend(int timeoutMs) throws RemoteException {
            return 0;
        }

        @Override // com.android.internal.car.ICarServiceHelper
        public void setAutoSuspendEnable(boolean enable) throws RemoteException {
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    /* loaded from: classes3.dex */
    public static abstract class Stub extends Binder implements ICarServiceHelper {
        private static final String DESCRIPTOR = "com.android.internal.car.ICarServiceHelper";
        static final int TRANSACTION_forceSuspend = 1;
        static final int TRANSACTION_setAutoSuspendEnable = 2;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICarServiceHelper asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof ICarServiceHelper)) {
                return (ICarServiceHelper) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == 1) {
                data.enforceInterface(DESCRIPTOR);
                int _arg0 = data.readInt();
                int _result = forceSuspend(_arg0);
                reply.writeNoException();
                reply.writeInt(_result);
                return true;
            } else if (code != 2) {
                if (code == 1598968902) {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            } else {
                data.enforceInterface(DESCRIPTOR);
                boolean _arg02 = data.readInt() != 0;
                setAutoSuspendEnable(_arg02);
                return true;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public static class Proxy implements ICarServiceHelper {
            public static ICarServiceHelper sDefaultImpl;
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // com.android.internal.car.ICarServiceHelper
            public int forceSuspend(int timeoutMs) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(timeoutMs);
                    boolean _status = this.mRemote.transact(1, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().forceSuspend(timeoutMs);
                    }
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.car.ICarServiceHelper
            public void setAutoSuspendEnable(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    boolean _status = this.mRemote.transact(2, _data, null, 1);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        Stub.getDefaultImpl().setAutoSuspendEnable(enable);
                    }
                } finally {
                    _data.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(ICarServiceHelper impl) {
            if (Proxy.sDefaultImpl == null && impl != null) {
                Proxy.sDefaultImpl = impl;
                return true;
            }
            return false;
        }

        public static ICarServiceHelper getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
