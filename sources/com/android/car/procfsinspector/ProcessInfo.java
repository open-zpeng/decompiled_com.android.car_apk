package com.android.car.procfsinspector;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;
/* loaded from: classes3.dex */
public class ProcessInfo implements Parcelable {
    public static final Parcelable.Creator<ProcessInfo> CREATOR = new Parcelable.Creator<ProcessInfo>() { // from class: com.android.car.procfsinspector.ProcessInfo.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public ProcessInfo createFromParcel(Parcel in) {
            return new ProcessInfo(in);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public ProcessInfo[] newArray(int size) {
            return new ProcessInfo[size];
        }
    };
    public final int pid;
    public final int uid;

    public ProcessInfo(int pid, int uid) {
        this.pid = pid;
        this.uid = uid;
    }

    public ProcessInfo(Parcel in) {
        this.pid = in.readInt();
        this.uid = in.readInt();
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pid);
        dest.writeInt(this.uid);
    }

    public boolean equals(Object other) {
        if (other instanceof ProcessInfo) {
            ProcessInfo processInfo = (ProcessInfo) other;
            return processInfo.pid == this.pid && processInfo.uid == this.uid;
        }
        return false;
    }

    public int hashCode() {
        return Objects.hash(Integer.valueOf(this.pid), Integer.valueOf(this.uid));
    }

    public String toString() {
        return String.format("pid = %d, uid = %d", Integer.valueOf(this.pid), Integer.valueOf(this.uid));
    }
}
