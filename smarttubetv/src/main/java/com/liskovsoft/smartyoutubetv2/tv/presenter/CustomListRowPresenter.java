package com.liskovsoft.smartyoutubetv2.tv.presenter;

import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
        setShadowEnabled(false); // no dark box behind the selected card
    }

    /**
     * TizenTube-like: the selected card stays at the row start while the row scrolls under it.
     * The selection moves on to the next card only when there is nothing left to scroll.
     */
    protected boolean isStartAligned() {
        return true;
    }

    @Override
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);

        if (isStartAligned()) {
            HorizontalGridView grid = ((ViewHolder) holder).getGridView();
            grid.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_BOTH_EDGE);
            grid.setWindowAlignmentOffset(0);
            grid.setWindowAlignmentOffsetPercent(0f);
            grid.setItemAlignmentOffset(0);
            grid.setItemAlignmentOffsetPercent(0f);

            // Leanback ignores the row's end padding when aligning to the high edge: leave room after the last card
            final int endGap = (int) (96 * grid.getResources().getDisplayMetrics().density);
            grid.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, android.view.View view,
                        androidx.recyclerview.widget.RecyclerView parent,
                        androidx.recyclerview.widget.RecyclerView.State state) {
                    androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter = parent.getAdapter();
                    int pos = parent.getChildAdapterPosition(view);
                    if (adapter != null && pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION
                            && pos == adapter.getItemCount() - 1) {
                        outRect.right = endGap;
                    }
                }
            });
        }
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        if (isStartAligned()) {
            final ViewHolder vh = (ViewHolder) holder;
            final HorizontalGridView grid = vh.getGridView();

            // The gap is applied to the last item only: when the row grows, the old last item must lose it
            if (grid.getTag(androidx.leanback.R.id.row_content) == null) {
                androidx.recyclerview.widget.RecyclerView.AdapterDataObserver observer =
                        new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                            @Override
                            public void onChanged() {
                                refresh();
                            }

                            @Override
                            public void onItemRangeInserted(int positionStart, int itemCount) {
                                refresh();
                            }

                            @Override
                            public void onItemRangeRemoved(int positionStart, int itemCount) {
                                refresh();
                            }

                            private void refresh() {
                                grid.post(grid::invalidateItemDecorations);
                            }
                        };
                vh.getBridgeAdapter().registerAdapterDataObserver(observer);
                grid.setTag(androidx.leanback.R.id.row_content, observer);
            }
        }
    }
}
