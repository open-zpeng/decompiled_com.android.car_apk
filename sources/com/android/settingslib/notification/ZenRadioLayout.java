package com.android.settingslib.notification;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
/* loaded from: classes3.dex */
public class ZenRadioLayout extends LinearLayout {
    public ZenRadioLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override // android.widget.LinearLayout, android.view.View
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ViewGroup radioGroup = (ViewGroup) getChildAt(0);
        ViewGroup radioContent = (ViewGroup) getChildAt(1);
        int size = radioGroup.getChildCount();
        if (size != radioContent.getChildCount()) {
            throw new IllegalStateException("Expected matching children");
        }
        boolean hasChanges = false;
        View lastView = null;
        for (int i = 0; i < size; i++) {
            View radio = radioGroup.getChildAt(i);
            View content = radioContent.getChildAt(i);
            if (lastView != null) {
                radio.setAccessibilityTraversalAfter(lastView.getId());
            }
            View contentClick = findFirstClickable(content);
            if (contentClick != null) {
                contentClick.setAccessibilityTraversalAfter(radio.getId());
            }
            lastView = findLastClickable(content);
            if (radio.getLayoutParams().height != content.getMeasuredHeight()) {
                hasChanges = true;
                radio.getLayoutParams().height = content.getMeasuredHeight();
            }
        }
        if (hasChanges) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private View findFirstClickable(View content) {
        if (content.isClickable()) {
            return content;
        }
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            for (int i = 0; i < group.getChildCount(); i++) {
                View v = findFirstClickable(group.getChildAt(i));
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
        return null;
    }

    private View findLastClickable(View content) {
        if (content.isClickable()) {
            return content;
        }
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View v = findLastClickable(group.getChildAt(i));
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
        return null;
    }
}
