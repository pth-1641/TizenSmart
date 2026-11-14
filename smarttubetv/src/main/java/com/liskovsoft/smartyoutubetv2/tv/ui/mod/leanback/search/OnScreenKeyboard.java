package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact on-screen keyboard (YouTube TV / TizenTube style).
 * Typing here updates the search field, so suggestions appear while typing.
 * Handles DPAD navigation itself, so focus never jumps unexpectedly.
 */
public class OnScreenKeyboard extends LinearLayout {
    public interface Listener {
        void onChar(String ch);
        void onBackspace();
        void onClear();
        void onSearch();
        void onSystemKeyboard();
        /** Focus tried to leave the keyboard */
        void onEdge(int direction, int row);
    }

    public static final int EDGE_UP = 0;
    public static final int EDGE_DOWN = 1;
    public static final int EDGE_LEFT = 2;

    private static final String[] ABC = {"ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ-'"};
    private static final String[] SYM = {"123&#()", "456@!?:", "789.-_\"", "0/$%+[]"};
    private static final String BACKSPACE = "⌫";
    private static final String GLOBE = "\u2328";

    private final float mDensity;
    private Listener mListener;
    private boolean mSymbols;
    private final List<List<TextView>> mGrid = new ArrayList<>();
    private int mFirstKeyId = View.NO_ID;

    public OnScreenKeyboard(Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        build();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Id of the first key (for nextFocus wiring from the search bar) */
    public int getFirstKeyId() {
        return mFirstKeyId;
    }

    /** Focus the key in the given row that is the closest to the left edge */
    public void focusRow(int row) {
        if (mGrid.isEmpty()) return;
        row = Math.max(0, Math.min(row, mGrid.size() - 1));
        mGrid.get(row).get(0).requestFocus();
    }

    /** Focus the CLEAR key (last row, middle) */
    public void focusClear() {
        if (mGrid.isEmpty()) return;
        List<TextView> last = mGrid.get(mGrid.size() - 1);
        last.get(Math.min(1, last.size() - 1)).requestFocus();
    }

    private int dp(float v) {
        return (int) (v * mDensity);
    }

    private void build() {
        removeAllViews();
        mGrid.clear();

        String[] rows = mSymbols ? SYM : ABC;
        for (int r = 0; r < rows.length; r++) {
            final int row = r;
            List<TextView> keys = new ArrayList<>();
            LinearLayout line = newRow();
            for (char c : rows[r].toCharArray()) {
                final String ch = String.valueOf(c);
                TextView key = newKey(ch, 38, false, v -> {
                    if (mListener != null) mListener.onChar(ch.toLowerCase());
                });
                keys.add(key);
                line.addView(key);
            }
            TextView extra = null;
            if (r == 0) {
                extra = newKey(BACKSPACE, 38, false, v -> {
                    if (mListener != null) mListener.onBackspace();
                });
            } else if (r == 1) {
                extra = newKey(mSymbols ? "ABC" : "&123", 38, false, v -> {
                    mSymbols = !mSymbols;
                    build();
                    focusRow(1);
                    // focus the toggle key (last in row 1)
                    List<TextView> row1 = mGrid.get(1);
                    row1.get(row1.size() - 1).requestFocus();
                });
            } else if (r == 2 && !mSymbols) {
                extra = newKey(GLOBE, 38, false, v -> {
                    if (mListener != null) mListener.onSystemKeyboard();
                });
            }
            if (extra != null) {
                keys.add(extra);
                line.addView(extra);
            }
            mGrid.add(keys);
            addView(line);
        }

        List<TextView> actions = new ArrayList<>();
        LinearLayout line = newRow();
        line.setPadding(0, dp(4), 0, 0);
        actions.add(newKey("SPACE", 78, true, v -> {
            if (mListener != null) mListener.onChar(" ");
        }));
        actions.add(newKey("CLEAR", 66, true, v -> {
            if (mListener != null) mListener.onClear();
        }));
        actions.add(newKey("SEARCH", 78, true, v -> {
            if (mListener != null) mListener.onSearch();
        }));
        for (TextView k : actions) line.addView(k);
        mGrid.add(actions);
        addView(line);

        TextView first = mGrid.get(0).get(0);
        if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
        mFirstKeyId = first.getId();

        // wire key listeners (after grid is complete)
        for (int r = 0; r < mGrid.size(); r++) {
            final int row = r;
            for (int c = 0; c < mGrid.get(r).size(); c++) {
                final int col = c;
                mGrid.get(r).get(c).setOnKeyListener((v, keyCode, event) -> onKey(row, col, keyCode, event));
            }
        }
    }

    private boolean onKey(int row, int col, int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                && keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true; // consume key up too
        }

        List<TextView> current = mGrid.get(row);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (col > 0) {
                    current.get(col - 1).requestFocus();
                } else if (mListener != null) {
                    mListener.onEdge(EDGE_LEFT, row);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (col < current.size() - 1) {
                    current.get(col + 1).requestFocus();
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) {
                    closestKey(row - 1, current.get(col)).requestFocus();
                } else if (mListener != null) {
                    mListener.onEdge(EDGE_UP, row);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < mGrid.size() - 1) {
                    closestKey(row + 1, current.get(col)).requestFocus();
                } else if (mListener != null) {
                    mListener.onEdge(EDGE_DOWN, row);
                }
                break;
        }
        return true;
    }

    /** Key in the target row whose horizontal center is the closest to the source key */
    private TextView closestKey(int targetRow, TextView from) {
        int[] loc = new int[2];
        from.getLocationOnScreen(loc);
        float fromCenter = loc[0] + from.getWidth() / 2f;

        TextView best = mGrid.get(targetRow).get(0);
        float bestDist = Float.MAX_VALUE;
        for (TextView key : mGrid.get(targetRow)) {
            key.getLocationOnScreen(loc);
            float dist = Math.abs(loc[0] + key.getWidth() / 2f - fromCenter);
            if (dist < bestDist) {
                bestDist = dist;
                best = key;
            }
        }
        return best;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setClipChildren(false);
        row.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView newKey(String label, int widthDp, boolean pill, OnClickListener click) {
        final TextView key = new TextView(getContext());
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextSize(pill ? 12 : 15);
        key.setTypeface(pill ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        key.setFocusable(true);
        key.setClickable(true);
        key.setTag(pill);
        key.setOnClickListener(click);
        LayoutParams lp = new LayoutParams(dp(widthDp), dp(pill ? 26 : 30));
        lp.setMargins(dp(pill ? 4 : 1), dp(1), dp(pill ? 4 : 1), dp(1));
        key.setLayoutParams(lp);
        key.setId(View.generateViewId());
        applyState(key, false);
        key.setOnFocusChangeListener((v, focused) -> applyState((TextView) v, focused));
        return key;
    }

    private void applyState(TextView key, boolean focused) {
        boolean pill = Boolean.TRUE.equals(key.getTag());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(pill ? 6 : 8));
        if (focused) {
            bg.setColor(Color.parseColor("#F1F1F1"));
        } else {
            bg.setColor(pill ? Color.parseColor("#272727") : Color.TRANSPARENT);
        }
        key.setBackground(bg);
        key.setTextColor(focused ? Color.parseColor("#0F0F0F") : Color.parseColor("#CCCCCC"));
    }
}
