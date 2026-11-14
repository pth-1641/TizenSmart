package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomVerticalGridPresenter extends VerticalGridPresenter {
    @Override
    protected void initializeGridViewHolder(ViewHolder vh) {
        super.initializeGridViewHolder(vh);

        // Plain grid behavior: DOWN from a column that has no item in the last (partial) row goes to the last item of that row
        final androidx.leanback.widget.VerticalGridView grid = vh.getGridView();
        grid.setOnKeyInterceptListener(event -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || event.getKeyCode() != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return false;
            }
            androidx.recyclerview.widget.RecyclerView.Adapter adapter = grid.getAdapter();
            int cols = getNumberOfColumns();
            int pos = grid.getSelectedPosition();
            if (adapter == null || cols <= 1 || pos < 0) {
                return false;
            }
            int count = adapter.getItemCount();
            int lastRow = (count - 1) / cols;
            if (pos / cols < lastRow && pos + cols >= count) {
                grid.setSelectedPositionSmooth(count - 1);
                return true;
            }
            return false;
        });
    }

    private final int mLayoutResId;
    private final int mRootResId;

    public CustomVerticalGridPresenter() {
        this(-1, -1);
    }

    public CustomVerticalGridPresenter(int layoutResId, int rootResId) {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setShadowEnabled(false); // no dark box behind the selected card

        mLayoutResId = layoutResId;
        mRootResId = rootResId;
    }

    /**
     * Subclass may override this to inflate a different layout.
     */
    @Override
    protected ViewHolder createGridViewHolder(ViewGroup parent) {
        if (mLayoutResId > 0 && mRootResId > 0) {
            View root = LayoutInflater.from(parent.getContext()).inflate(
                    mLayoutResId, parent, false);
            return new ViewHolder(root.findViewById(mRootResId));
        }

        ViewHolder viewHolder = super.createGridViewHolder(parent);

        // Not working!!!
        // Tweaking grid to fix broken UI layout on some devices like Shield
        //if (viewHolder.getGridView() != null) {
        //    viewHolder.getGridView().setGravity(Gravity.TOP | Gravity.START);
        //    viewHolder.getGridView().setItemViewCacheSize(0);
        //    //viewHolder.getGridView().setItemAlignmentOffsetPercent(0);
        //    //viewHolder.getGridView().setItemAlignmentOffsetWithPadding(true);
        //}

        return viewHolder;
    }
}
