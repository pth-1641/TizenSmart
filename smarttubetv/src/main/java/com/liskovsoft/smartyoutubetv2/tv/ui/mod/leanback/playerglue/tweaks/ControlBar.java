/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

class ControlBar extends LinearLayout {

    public interface OnChildFocusedListener {

        public void onChildFocusedListener(View child, View focused);
    }

    private int mChildMarginFromCenter;
    private OnChildFocusedListener mOnChildFocusedListener;
    // Can't set to static. Because we have two control bars.
    private int mLastFocusIndex = -1;
    // MOD: Sync focus index between rows
    private static int sSharedFocusIndex = -1;
    private boolean mIsSharedFocusEnabled = true;
    private boolean mDefaultFocusToMiddle = true;
    private boolean mFocusRecovery = true;

    public ControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        resetFocus(); // MOD: reset global focus index
    }

    public ControlBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        resetFocus(); // MOD: reset global focus index
    }

    void setDefaultFocusToMiddle(boolean defaultFocusToMiddle) {
        mDefaultFocusToMiddle = defaultFocusToMiddle;
    }

    /**
     * MOD: enable/disable focus restoration
     */
    void setFocusRecovery(boolean focusRecovery) {
        mFocusRecovery = focusRecovery;
    }

    /**
     * MOD: synced navigation between rows
     */
    void setSharedFocusEnabled(boolean enabled) {
        mIsSharedFocusEnabled = enabled;
    }

    void resetFocus() {
        setFocusIndex(-1);
    }

    int getDefaultFocusIndex() {
        return mDefaultFocusToMiddle ? getChildCount() / 2 : 0;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (getChildCount() > 0) {
            // MOD: fix situation when rows have different length
            if (getFocusIndex() >= getChildCount()) {
                setFocusIndex(getChildCount() - 1);
            }

            int index = getFocusIndex() >= 0 && getFocusIndex() < getChildCount()
                    ? getFocusIndex() : getDefaultFocusIndex();

            if (getChildAt(index).requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if ((direction == ViewGroup.FOCUS_UP || direction == ViewGroup.FOCUS_DOWN)) {
            if (getFocusIndex() >= 0 && getFocusIndex() < getChildCount()) {
                views.add(getChildAt(getFocusIndex()));
            } else if (getChildCount() > 0) {
                views.add(getChildAt(getDefaultFocusIndex()));
            }
        } else {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    public void setOnChildFocusedListener(OnChildFocusedListener listener) {
        mOnChildFocusedListener = listener;
    }

    public void setChildMarginFromCenter(int marginFromCenter) {
        mChildMarginFromCenter = marginFromCenter;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (mFocusRecovery) {
            setFocusIndex(indexOfChild(child));
        }
        if (mOnChildFocusedListener != null) {
            mOnChildFocusedListener.onChildFocusedListener(child, focused);
        }
    }

    // MOD: TizenTube-like two groups layout: "left" children at the start, the rest at the end.
    // If the right group doesn't fit (many buttons enabled in settings) the overflow goes to the second line.
    private boolean mTwoGroups;
    private int[] mGaps = new int[0];
    private int mRowHeight;
    private int mRowSpacing;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mTwoGroups = false;
        mFirstFit = -1;
        if (mChildMarginFromCenter <= 0) {
            return;
        }

        int rightStart = -1;
        for (int i = 0; i < getChildCount(); i++) {
            if (Boolean.TRUE.equals(getChildAt(i).getTag(com.liskovsoft.smartyoutubetv2.tv.R.id.header_fade))) {
                mTwoGroups = true;
            } else if (mTwoGroups && rightStart < 0) {
                rightStart = i;
            }
        }
        float density = getResources().getDisplayMetrics().density;
        int tighten = mTwoGroups ? (int) (6 * density) : 0;
        int iconWidth = getResources().getDimensionPixelSize(androidx.leanback.R.dimen.lb_control_button_secondary_diameter);

        // MOD: constant gap even if children differ in width
        int count = getChildCount();
        mGaps = new int[count];
        int total = getPaddingLeft() + getPaddingRight();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (i > 0) {
                mGaps[i] = mChildMarginFromCenter - Math.min(Math.min(getChildAt(i - 1).getMeasuredWidth(), child.getMeasuredWidth()), iconWidth) - tighten;
            }
            total += mGaps[i] + child.getMeasuredWidth();
        }

        boolean exact = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED;
        if (mTwoGroups && rightStart > 0 && exact) {
            int avail = MeasureSpec.getSize(widthMeasureSpec);
            // the centered transport buttons (previous / play / next) occupy the middle: keep the right group clear of them
            int rightBound = avail / 2 + (int) (122 * density);
            int capacity = avail - getPaddingRight() - rightBound;
            int used = 0;
            int firstFit = count; // children [rightStart, firstFit) fill the first line, the rest goes to the second line
            for (int i = rightStart; i < count; i++) {
                used += getChildAt(i).getMeasuredWidth() + (i > rightStart ? mGaps[i] : 0);
                if (used > capacity && i > rightStart) {
                    firstFit = i;
                    break;
                }
            }
            mFirstFit = firstFit;
            mRowSpacing = (int) (14 * density);
            mRowHeight = 0;
            for (int i = 0; i < count; i++) {
                mRowHeight = Math.max(mRowHeight, getChildAt(i).getMeasuredHeight());
            }
            setMeasuredDimension(avail, mFirstFit < count ? mRowHeight * 2 + mRowSpacing : mRowHeight);
            return;
        }

        // classic single group (primary row)
        for (int i = 1; i < count; i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.getMarginStart() != mGaps[i]) {
                lp.setMarginStart(mGaps[i]);
                child.setLayoutParams(lp);
            }
        }
        setMeasuredDimension(total, getMeasuredHeight());
    }

    private int mFirstFit = -1;

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!mTwoGroups || mFirstFit < 0 || mGaps.length != getChildCount() || !isTwoGroupMeasured()) {
            super.onLayout(changed, l, t, r, b);
            return;
        }
        int width = r - l;
        int count = getChildCount();
        int rightStart = 0;
        for (int i = 0; i < count; i++) {
            if (!Boolean.TRUE.equals(getChildAt(i).getTag(com.liskovsoft.smartyoutubetv2.tv.R.id.header_fade))) {
                rightStart = i;
                break;
            }
        }
        // left group
        int x = getPaddingLeft();
        for (int i = 0; i < rightStart; i++) {
            View c = getChildAt(i);
            if (i > 0) x += mGaps[i];
            int top = (mRowHeight - c.getMeasuredHeight()) / 2;
            c.layout(x, top, x + c.getMeasuredWidth(), top + c.getMeasuredHeight());
            x += c.getMeasuredWidth();
        }
        // right group: first line [firstFit, count) right-aligned, overflow [rightStart, firstFit) on the second line
        layoutRightAligned(rightStart, Math.min(mFirstFit, count), width, 0);
        if (mFirstFit < count) {
            layoutRightAligned(mFirstFit, count, width, mRowHeight + mRowSpacing);
        }
    }

    private boolean isTwoGroupMeasured() {
        return mFirstFit >= 0;
    }

    private void layoutRightAligned(int from, int to, int width, int y) {
        int x = width - getPaddingRight();
        for (int i = to - 1; i >= from; i--) {
            View c = getChildAt(i);
            int left = x - c.getMeasuredWidth();
            int top = y + (mRowHeight - c.getMeasuredHeight()) / 2;
            c.layout(left, top, x, top + c.getMeasuredHeight());
            x = left - (i > from ? mGaps[i] : 0);
        }
    }

    private void setFocusIndex(int index) {
        mLastFocusIndex = index;
        if (index != -1) // don't reset global focus between player launches
            sSharedFocusIndex = index;
    }

    private int getFocusIndex() {
        return mIsSharedFocusEnabled ? sSharedFocusIndex : mLastFocusIndex;
    }
}
