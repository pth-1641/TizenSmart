package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list of search suggestions (YouTube TV / TizenTube style).
 * History items get a clock icon, new suggestions get a magnifier icon.
 */
public class SuggestionList extends LinearLayout {
    public interface Listener {
        void onSuggestionClicked(Object item);
        void onSuggestionLongClicked(Object item);
        /** Focus tried to leave the list: direction is OnScreenKeyboard.EDGE_* or EDGE_RIGHT */
        void onEdge(int direction, int index);
    }

    public static final int EDGE_RIGHT = 3;

    private static final int MAX_ITEMS = 6;
    private final float mDensity;
    private final List<TextView> mItems = new ArrayList<>();
    private Listener mListener;
    private List<String> mLabels = new ArrayList<>();
    private int mLastIndex;

    public SuggestionList(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public int size() {
        return mItems.size();
    }

    /** Focus the item that was selected last time (or the first one after the suggestions changed) */
    public void focusLast() {
        focusItem(mLastIndex);
    }

    public void focusItem(int index) {
        if (mItems.isEmpty()) return;
        mItems.get(Math.max(0, Math.min(index, mItems.size() - 1))).requestFocus();
    }

    public void setItems(List<String> labels, List<?> payloads, List<Boolean> history) {
        removeAllViews();
        mItems.clear();

        // Text was edited => suggestions changed => start again from the first one
        List<String> newLabels = labels != null ? new ArrayList<>(labels) : new ArrayList<>();
        if (!newLabels.equals(mLabels)) {
            mLastIndex = 0;
        }
        mLabels = newLabels;

        if (labels == null || payloads == null) {
            return;
        }
        int count = Math.min(Math.min(labels.size(), payloads.size()), MAX_ITEMS);
        for (int i = 0; i < count; i++) {
            boolean isHistory = history != null && i < history.size() && history.get(i);
            TextView item = newItem(i, labels.get(i), payloads.get(i), isHistory);
            mItems.add(item);
            addView(item);
        }
    }

    private int dp(float v) {
        return (int) (v * mDensity);
    }

    private TextView newItem(final int index, String label, final Object payload, boolean history) {
        TextView item = new TextView(getContext());
        item.setText(label);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setTextSize(14);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(dp(10), 0, dp(10), 0);
        item.setCompoundDrawablePadding(dp(10));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(30));
        lp.setMargins(0, dp(1), 0, dp(1));
        item.setLayoutParams(lp);
        item.setTag(history);
        applyState(item, false);
        item.setOnFocusChangeListener((v, focused) -> {
            if (focused) mLastIndex = index;
            applyState((TextView) v, focused);
        });
        item.setOnClickListener(v -> {
            if (mListener != null) mListener.onSuggestionClicked(payload);
        });
        item.setOnLongClickListener(v -> {
            if (mListener != null) mListener.onSuggestionLongClicked(payload);
            return true;
        });
        item.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                    && keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
                return false;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (index > 0) mItems.get(index - 1).requestFocus();
                    else if (mListener != null) mListener.onEdge(OnScreenKeyboard.EDGE_UP, index);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (index < mItems.size() - 1) mItems.get(index + 1).requestFocus();
                    else if (mListener != null) mListener.onEdge(OnScreenKeyboard.EDGE_DOWN, index);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (mListener != null) mListener.onEdge(EDGE_RIGHT, index);
                    break;
                default:
                    break; // LEFT: nothing there
            }
            return true;
        });
        return item;
    }

    private void applyState(TextView item, boolean focused) {
        boolean history = Boolean.TRUE.equals(item.getTag());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(focused ? Color.parseColor("#F1F1F1") : Color.TRANSPARENT);
        item.setBackground(bg);

        int textColor = focused ? Color.parseColor("#0F0F0F") : Color.WHITE;
        item.setTextColor(textColor);

        Drawable icon = ContextCompat.getDrawable(getContext(),
                history ? R.drawable.ic_search_history : R.drawable.ic_search_suggest);
        if (icon != null) {
            icon = icon.mutate();
            icon.setBounds(0, 0, dp(18), dp(18));
            // history: dimmer icon, like "already searched"
            icon.setTint(focused ? Color.parseColor("#0F0F0F") : (history ? Color.parseColor("#AAAAAA") : Color.WHITE));
            item.setCompoundDrawablesRelative(icon, null, null, null);
        }
        item.setTypeface(null, history ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
    }
}
