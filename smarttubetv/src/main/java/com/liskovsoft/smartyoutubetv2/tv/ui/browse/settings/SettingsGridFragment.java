package com.liskovsoft.smartyoutubetv2.tv.ui.browse.settings;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.SettingsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.SettingsSection;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.UriBackgroundManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingsGridFragment extends GridFragment implements SettingsSection {
    private static final String TAG = SettingsGridFragment.class.getSimpleName();
    private ArrayObjectAdapter mSettingsAdapter;
    private BrowsePresenter mMainPresenter;
    private UriBackgroundManager mBackgroundManager;
    private final List<SettingsGroup> mPendingUpdates = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainPresenter = BrowsePresenter.instance(getContext());
        mBackgroundManager = ((LeanbackActivity) getActivity()).getBackgroundManager();

        setupAdapter();
        setupEventListeners();
        applyPendingUpdates();

        if (getMainFragmentAdapter().getFragmentHost() != null) {
            getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());
        }
    }

    @Override
    public void onViewCreated(android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Rows scrolled up must not slide under the clock/logo in the top right corner: clip the grid at its top padding
        final android.view.View grid = view.findViewById(androidx.leanback.R.id.browse_grid);
        if (grid != null) {
            grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                    v.setClipBounds(new android.graphics.Rect(0, v.getPaddingTop(), r - l, b - t)));
        }
    }

    @Override
    protected boolean isCenterContent() {
        return true;
    }

    @Override
    protected void showOrHideTitle() {
        // NOP. Always show Browse fragment title
    }

    private void applyPendingUpdates() {
        for (SettingsGroup group : mPendingUpdates) {
            update(group);
        }

        mPendingUpdates.clear();
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        presenter.enableChildRoundedCorners(getMainUIData().isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        presenter.setNumberOfColumns(getFittingColumnsNum());
        setGridPresenter(presenter);

        if (mSettingsAdapter == null) {
            SettingsCardPresenter gridPresenter = new SettingsCardPresenter();
            mSettingsAdapter = new ArrayObjectAdapter(gridPresenter);
            setAdapter(mSettingsAdapter);
        }
    }

    /** Only whole tiles: the screen width minus the sidebar strip, the content gap and some end room */
    private int getFittingColumnsNum() {
        android.content.res.Resources res = getResources();
        float density = res.getDisplayMetrics().density;
        float screenDp = res.getDisplayMetrics().widthPixels / density;
        float startDp = res.getDimension(R.dimen.lb_browse_headers_mini_width) / density
                + res.getDimension(R.dimen.lb_browse_mini_content_gap) / density;
        float tileDp = res.getDimension(R.dimen.settings_card_width) / density + 12 + res.getDimension(R.dimen.grid_item_horizontal_spacing) / density;
        int cols = (int) ((screenDp - startDp - 24) / tileDp);
        return Math.max(1, Math.min(4, cols)); // about 4 tiles per row, so that titles are not cut
    }

    @Override
    public void clear() {
        if (mSettingsAdapter != null) {
            mSettingsAdapter.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        if (mSettingsAdapter == null) {
            return mPendingUpdates.isEmpty();
        }

        return mSettingsAdapter.size() == 0;
    }

    @Override
    public void update(SettingsGroup group) {
        if (mSettingsAdapter == null) {
            mPendingUpdates.add(group);
            return;
        }

        // Always clear (continuation not supported)
        clear();

        if (group != null) {
            for (SettingsItem item : group.getItems()) {
                mSettingsAdapter.add(item);
            }
        }
    }

    private MainUIData getMainUIData() {
        return MainUIData.instance(getContext());
    }

    private GeneralData getGeneralData() {
        return GeneralData.instance(getContext());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof SettingsItem) {
                String password = getGeneralData().getSettingsPassword();

                if (password == null) {
                    ((SettingsItem) item).onClick.run();
                } else {
                    SimpleEditDialog.showPassword(
                            getContext(),
                            getContext().getString(R.string.enter_settings_password),
                            null,
                            newValue -> {
                                if (Utils.passwordMatch(password, newValue)) {
                                    ((SettingsItem) item).onClick.run();
                                    return true;
                                }
                                return false;
                            });
                }

                // Close PIP inside Settings section
                //if (PlaybackPresenter.instance(getContext()).isInPipMode()) {
                //    PlaybackPresenter.instance(getContext()).forceFinish();
                //}
            } else {
                Toast.makeText(getContext(), item.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
