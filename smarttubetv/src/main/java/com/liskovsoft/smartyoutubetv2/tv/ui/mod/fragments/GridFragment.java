package com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnChildLaidOutListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * NOTE: All code was new. It's copied from:<br/>
 * https://github.com/googlearchive/leanback-showcase/blob/master/app/src/main/java/android/support/v17/leanback/supportleanbackshowcase/app/page/PageAndListRowFragment.java<br/>
 * Description: A fragment for rendering items in a vertical grids.
 */
public class GridFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final String TAG = "VerticalGridFragment";
    private static boolean DEBUG = false;

    private ObjectAdapter mAdapter;
    private VerticalGridPresenter mGridPresenter;
    private VerticalGridPresenter.ViewHolder mGridViewHolder;
    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    private Object mSceneAfterEntranceTransition;
    private int mSelectedPosition = -1;
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    GridFragment.this.setEntranceTransitionState(state);
                }
            };
    /**
     * Sets the grid presenter.
     */
    public void setGridPresenter(VerticalGridPresenter gridPresenter) {
        if (gridPresenter == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        mGridPresenter = gridPresenter;
        mGridPresenter.setOnItemViewSelectedListener(mViewSelectedListener);
        if (mOnItemViewClickedListener != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the grid presenter.
     */
    public VerticalGridPresenter getGridPresenter() {
        return mGridPresenter;
    }

    /**
     * Returns R.id.browse_grid
     */
    public VerticalGridView getBrowseGrid() {
        return mGridViewHolder != null ? mGridViewHolder.getGridView() : null;
    }

    /**
     * Sets the object adapter for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        updateAdapter();
    }

    /**
     * Returns the object adapter.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    final private OnItemViewSelectedListener mViewSelectedListener =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (mGridViewHolder == null) {
                        return;
                    }
                    int position = mGridViewHolder.getGridView().getSelectedPosition();
                    if (DEBUG) Log.v(TAG, "grid selected position " + position);
                    gridOnItemSelected(position);
                    if (mOnItemViewSelectedListener != null) {
                        mOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                                rowViewHolder, row);
                    }
                }
            };

    final private OnChildLaidOutListener mChildLaidOutListener =
            new OnChildLaidOutListener() {
                @Override
                public void onChildLaidOut(ViewGroup parent, View view, int position, long id) {
                    if (position == 0) {
                        showOrHideTitle();
                    }
                }
            };

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;

        // Don't do that. We have hook here for show/hide title view.
        //if (mGridPresenter != null) {
        //    mGridPresenter.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        //}
    }

    private void gridOnItemSelected(int position) {
        if (position != mSelectedPosition) {
            mSelectedPosition = position;
            showOrHideTitle();
        }
    }

    protected void showOrHideTitle() {
        if (mGridViewHolder.getGridView().findViewHolderForAdapterPosition(mSelectedPosition)
                == null || mMainFragmentAdapter.getFragmentHost() == null) {
            return;
        }
        if (!mGridViewHolder.getGridView().hasPreviousViewInSameRow(mSelectedPosition)) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(true);
        } else {
            mMainFragmentAdapter.getFragmentHost().showTitleView(false);
        }
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mGridPresenter != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_grid, container, false);
    }

    /** Center the cards in the visible area when the sidebar is collapsed */
    protected boolean isCenterContent() {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup gridDock = (ViewGroup) view.findViewById(R.id.browse_grid_dock);
        mGridViewHolder = mGridPresenter.onCreateViewHolder(gridDock);
        gridDock.addView(mGridViewHolder.view);

        // MOD: room between the top bar (voice/search) and the first row of cards
        if (!isCenterContent()) {
            gridDock.setPadding(gridDock.getPaddingLeft(), (int) (56 * view.getResources().getDisplayMetrics().density),
                    gridDock.getPaddingRight(), gridDock.getPaddingBottom());
        }

        // The browse container is shifted right by the sidebar margin but keeps its full width, so the centered grid
        // sticks out of the screen. Follow the overflow every frame (moves smoothly with the sidebar transition).
        final View gridView = mGridViewHolder.view;
        final float[] lastTarget = {Float.NaN};
        final android.animation.ValueAnimator[] running = {null};
        view.getViewTreeObserver().addOnPreDrawListener(() -> {
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
            int overflow = Math.max(0, loc[0] + view.getWidth() - screenWidth);
            float d = view.getResources().getDisplayMetrics().density;
            // Collapsed sidebar: center the grid in the visible area (+14dp room on the right). Expanded: leave it.
            float target = overflow > 90 * d ? 0f : -overflow / 2f - 14 * d;
            if (isCenterContent() && overflow <= 90 * d && mGridViewHolder != null) {
                // Fixed column count: center the real content in the visible area (right of the mini sidebar)
                android.view.ViewGroup rows = mGridViewHolder.getGridView();
                float minLeft = Float.MAX_VALUE, maxRight = -Float.MAX_VALUE;
                int[] cl = new int[2];
                for (int i = 0; i < rows.getChildCount(); i++) {
                    android.view.View c = rows.getChildAt(i);
                    c.getLocationOnScreen(cl);
                    minLeft = Math.min(minLeft, cl[0] - gridView.getTranslationX());
                    maxRight = Math.max(maxRight, cl[0] + c.getWidth() - gridView.getTranslationX());
                }
                if (minLeft < maxRight) {
                    float visibleCenter = (62 * d + screenWidth) / 2f;
                    target = visibleCenter - (minLeft + maxRight) / 2f;
                }
            }
            if (Float.isNaN(lastTarget[0])) {
                gridView.setTranslationX(target);
            } else if (Math.abs(target - lastTarget[0]) > 1f) {
                // Slide instead of snapping: the snap showed as a flash when the sidebar opens/closes
                if (running[0] != null) running[0].cancel();
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(gridView.getTranslationX(), target);
                anim.setDuration(170);
                anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
                anim.addUpdateListener(a -> gridView.setTranslationX((float) a.getAnimatedValue()));
                running[0] = anim;
                anim.start();
            }
            lastTarget[0] = target;
            return true;
        });

        mGridViewHolder.getGridView().setOnChildLaidOutListener(mChildLaidOutListener);

        mSceneAfterEntranceTransition = TransitionHelper.createScene(gridDock, new Runnable() {
            @Override
            public void run() {
                setEntranceTransitionState(true);
            }
        });

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
        }

        updateAdapter();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGridViewHolder = null;
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    /**
     * Sets the selected item position.
     */
    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
        if (mGridViewHolder != null && mGridViewHolder.getGridView().getAdapter() != null) {
            mGridViewHolder.getGridView().setSelectedPosition(position);
        }
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    private void updateAdapter() {
        if (mGridViewHolder != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mSelectedPosition != -1) {
                mGridViewHolder.getGridView().setSelectedPosition(mSelectedPosition);
            }
        }
    }

    void setEntranceTransitionState(boolean afterTransition) {
        mGridPresenter.setEntranceTransitionState(mGridViewHolder, afterTransition);
    }
}
