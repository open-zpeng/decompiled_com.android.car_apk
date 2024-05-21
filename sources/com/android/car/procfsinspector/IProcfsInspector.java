package com.android.car.procfsinspector;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;
/* loaded from: classes3.dex */
public interface IProcfsInspector extends IInterface {
    List<ProcessInfo> readProcessTable() throws RemoteException;

    /* loaded from: classes3.dex */
    public static class Default implements IProcfsInspector {
        @Override // com.android.car.procfsinspector.IProcfsInspector
        public List<ProcessInfo> readProcessTable() throws RemoteException {
            return null;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }
    }

    /* loaded from: classes3.dex */
    public static abstract class Stub extends Binder implements IProcfsInspector {
        private static final String DESCRIPTOR = "com.android.car.procfsinspector.IProcfsInspector";
        static final int TRANSACTION_readProcessTable = 1;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IProcfsInspector asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof IProcfsInspector)) {
                return (IProcfsInspector) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != 1) {
                if (code == 1598968902) {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(DESCRIPTOR);
            List<ProcessInfo> _result = readProcessTable();
            reply.writeNoException();
            reply.writeTypedList(_result);
            return true;
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* loaded from: classes3.dex */
        public static class Proxy implements IProcfsInspector {
            public static IProcfsInspector sDefaultImpl;
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

            @Override // com.android.car.procfsinspector.IProcfsInspector
            public List<ProcessInfo> readProcessTable() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    boolean _status = this.mRemote.transact(1, _data, _reply, 0);
                    if (!_status && Stub.getDefaultImpl() != null) {
                        return Stub.getDefaultImpl().readProcessTable();
                    }
                    _reply.readException();
                    List<ProcessInfo> _result = _reply.createTypedArrayList(ProcessInfo.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public static boolean setDefaultImpl(IProcfsInspector impl) {
            if (Proxy.sDefaultImpl == null && impl != null) {
                Proxy.sDefaultImpl = impl;
                return true;
            }
            return false;
        }

        public static IProcfsInspector getDefaultImpl() {
            return Proxy.sDefaultImpl;
        }
    }
}
