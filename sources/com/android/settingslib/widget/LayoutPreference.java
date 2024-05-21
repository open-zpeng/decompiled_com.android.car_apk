package com.android.settingslib.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
/* loaded from: classes3.dex */
public class LayoutPreference extends Preference {
    private boolean mAllowDividerAbove;
    private boolean mAllowDividerBelow;
    private final View.OnClickListener mClickListener;
    private View mRootView;

    public /* synthetic */ void lambda$new$0$LayoutPreference(View v) {
        performClick(v);
    }

    public LayoutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mClickListener = new View.OnClickListener() { // from class: com.android.settingslib.widget.-$$Lambda$LayoutPreference$A_OWgARxS1B51rTsCCoDBOGYAP0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LayoutPreference.this.lambda$new$0$LayoutPreference(view);
            }
        };
        init(context, attrs, 0);
    }

    public LayoutPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mClickListener = new View.OnClickListener() { // from class: com.android.settingslib.widget.-$$Lambda$LayoutPreference$A_OWgARxS1B51rTsCCoDBOGYAP0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LayoutPreference.this.lambda$new$0$LayoutPreference(view);
            }
        };
        init(context, attrs, defStyleAttr);
    }

    public LayoutPreference(Context context, int resource) {
        this(context, LayoutInflater.from(context).inflate(resource, (ViewGroup) null, false));
    }

    public LayoutPreference(Context context, View view) {
        super(context);
        this.mClickListener = new View.OnClickListener() { // from class: com.android.settingslib.widget.-$$Lambda$LayoutPreference$A_OWgARxS1B51rTsCCoDBOGYAP0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                LayoutPreference.this.lambda$new$0$LayoutPreference(view2);
            }
        };
        setView(view);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Preference);
        this.mAllowDividerAbove = TypedArrayUtils.getBoolean(a, R.styleable.Preference_allowDividerAbove, R.styleable.Preference_allowDividerAbove, false);
        this.mAllowDividerBelow = TypedArrayUtils.getBoolean(a, R.styleable.Preference_allowDividerBelow, R.styleable.Preference_allowDividerBelow, false);
        a.recycle();
        TypedArray a2 = context.obtainStyledAttributes(attrs, R.styleable.Preference, defStyleAttr, 0);
        int layoutResource = a2.getResourceId(R.styleable.Preference_android_layout, 0);
        if (layoutResource == 0) {
            throw new IllegalArgumentException("LayoutPreference requires a layout to be defined");
        }
        a2.recycle();
        View view = LayoutInflater.from(getContext()).inflate(layoutResource, (ViewGroup) null, false);
        setView(view);
    }

    private void setView(View view) {
        setLayoutResource(R.layout.layout_preference_frame);
        this.mRootView = view;
        setShouldDisableView(false);
    }

    @Override // androidx.preference.Preference
    public void onBindViewHolder(PreferenceViewHolder holder) {
        holder.itemView.setOnClickListener(this.mClickListener);
        boolean selectable = isSelectable();
        holder.itemView.setFocusable(selectable);
        holder.itemView.setClickable(selectable);
        holder.setDividerAllowedAbove(this.mAllowDividerAbove);
        holder.setDividerAllowedBelow(this.mAllowDividerBelow);
        FrameLayout layout = (FrameLayout) holder.itemView;
        layout.removeAllViews();
        ViewGroup parent = (ViewGroup) this.mRootView.getParent();
        if (parent != null) {
            parent.removeView(this.mRootView);
        }
        layout.addView(this.mRootView);
    }

    public <T extends View> T findViewById(int id) {
        return (T) this.mRootView.findViewById(id);
    }

    public void setAllowDividerBelow(boolean allowed) {
        this.mAllowDividerBelow = allowed;
    }

    public boolean isAllowDividerBelow() {
        return this.mAllowDividerBelow;
    }
}
