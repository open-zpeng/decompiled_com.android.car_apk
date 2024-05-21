package com.android.settingslib.drawer;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
/* loaded from: classes3.dex */
public class DashboardCategory implements Parcelable {
    public static final Parcelable.Creator<DashboardCategory> CREATOR = new Parcelable.Creator<DashboardCategory>() { // from class: com.android.settingslib.drawer.DashboardCategory.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public DashboardCategory createFromParcel(Parcel source) {
            return new DashboardCategory(source);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public DashboardCategory[] newArray(int size) {
            return new DashboardCategory[size];
        }
    };
    public final String key;
    private List<Tile> mTiles = new ArrayList();

    public DashboardCategory(String key) {
        this.key = key;
    }

    DashboardCategory(Parcel in) {
        this.key = in.readString();
        int count = in.readInt();
        for (int n = 0; n < count; n++) {
            Tile tile = Tile.CREATOR.createFromParcel(in);
            this.mTiles.add(tile);
        }
    }

    public synchronized List<Tile> getTiles() {
        List<Tile> result;
        result = new ArrayList<>(this.mTiles.size());
        for (Tile tile : this.mTiles) {
            result.add(tile);
        }
        return result;
    }

    public synchronized void addTile(Tile tile) {
        this.mTiles.add(tile);
    }

    public synchronized void removeTile(int n) {
        this.mTiles.remove(n);
    }

    public int getTilesCount() {
        return this.mTiles.size();
    }

    public Tile getTile(int n) {
        return this.mTiles.get(n);
    }

    public void sortTiles() {
        Collections.sort(this.mTiles, Tile.TILE_COMPARATOR);
    }

    public synchronized void sortTiles(final String skipPackageName) {
        Collections.sort(this.mTiles, new Comparator() { // from class: com.android.settingslib.drawer.-$$Lambda$DashboardCategory$hMIMtvkEGTs2t-7RyY7SqwVmOgI
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return DashboardCategory.lambda$sortTiles$0(skipPackageName, (Tile) obj, (Tile) obj2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ int lambda$sortTiles$0(String skipPackageName, Tile tile1, Tile tile2) {
        int orderCompare = tile2.getOrder() - tile1.getOrder();
        if (orderCompare != 0) {
            return orderCompare;
        }
        String package1 = tile1.getPackageName();
        String package2 = tile2.getPackageName();
        int packageCompare = String.CASE_INSENSITIVE_ORDER.compare(package1, package2);
        if (packageCompare != 0) {
            if (TextUtils.equals(package1, skipPackageName)) {
                return -1;
            }
            if (TextUtils.equals(package2, skipPackageName)) {
                return 1;
            }
        }
        return packageCompare;
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.key);
        int count = this.mTiles.size();
        dest.writeInt(count);
        for (int n = 0; n < count; n++) {
            Tile tile = this.mTiles.get(n);
            tile.writeToParcel(dest, flags);
        }
    }
}
