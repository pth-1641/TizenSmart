package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.TitleHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.headers.ExtendedHeadersSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class BrowseFragment extends BrowseSupportFragment implements BrowseView {
    private static final String TAG = BrowseFragment.class.getSimpleName();
    private ArrayObjectAdapter mSectionRowAdapter;
    private BrowsePresenter mBrowsePresenter;
    private Map<Integer, BrowseSection> mSections;
    private BrowseSectionFragmentFactory mSectionFragmentFactory;
    private Handler mHandler;
    private ProgressBarManager mProgressBarManager;
    private boolean mIsFragmentCreated;
    private boolean mFocusOnContent;
    private CrashRestorer mCrashRestorer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        if (getContext() == null) {
            return;
        }
        
        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;

        mSections = new HashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.setView(this);
        mProgressBarManager = new ProgressBarManager();

        setupAdapter();
        setupFragmentFactory();
        setupUi();

        enableMainFragmentScaling(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        mCrashRestorer.persistHeaderIndex(outState, getSelectedPosition());
        mCrashRestorer.persistVideo(outState, mBrowsePresenter.getCurrentVideo());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupEventListeners();
        setupSearchOverlapWatcher();
        setupAccountItem();

        prepareEntranceTransition();

        mBrowsePresenter.onViewInitialized();

        // Restore state after crash
        mCrashRestorer.restoreHeader((idx, video) -> {
            selectSection(idx, true);
            selectSectionItem(video);
        });
        mCrashRestorer.restorePlayback();
    }

    @Override
    public HeadersSupportFragment onCreateHeadersSupportFragment() {
        return new ExtendedHeadersSupportFragment();
    }

    private void setupEventListeners() {
        // Collapsed sidebar = icons only
        setBrowseTransitionListener(new BrowseTransitionListener() {
            @Override
            public void onHeadersTransitionStart(boolean withHeaders) {
                IconHeaderItemPresenter.setCollapsed(!withHeaders, getHeadersSupportFragment().getVerticalGridView(), getSelectedPosition());
                updateTitleOrbs(withHeaders);
            }
        });


        getHeadersSupportFragment().setOnHeaderClickedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();
                    int newPosition = indexOf(headerId);

                    if (getHeadersSupportFragment().getSelectedPosition() != newPosition) {
                        // touch screen support
                        getHeadersSupportFragment().setSelectedPosition(newPosition);
                    } else {
                        // update section when clicked or pressed
                        mBrowsePresenter.onSectionFocused((int) headerId);
                        startHeadersTransitionSafe(false);
                    }
                }
        );

        ((ExtendedHeadersSupportFragment) getHeadersSupportFragment()).setOnHeaderLongPressedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();

                    mBrowsePresenter.onSectionLongPressed((int) headerId);
                }
        );

        setOnSearchClickedListener(view -> SearchPresenter.instance(getContext()).startSearch(null));
    }

    private void setupFragmentFactory() {
        mSectionFragmentFactory = new BrowseSectionFragmentFactory(
                (row) -> {
                    focusOnContentIfNeeded();
                    mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
                }
        );

        getMainFragmentRegistry().registerFragment(PageRow.class, mSectionFragmentFactory);
    }

    private int indexOf(long headerId) {
        for (int i = 0; i < mSectionRowAdapter.size(); i++) {
            PageRow row = (PageRow) mSectionRowAdapter.get(i);
            HeaderItem header = row.getHeaderItem();
            if (header.getId() == headerId) {
                return i;
            }
        }

        return 0;
    }

    private void setupAdapter() {
        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mSectionRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mSectionRowAdapter);
    }

    private void setupUi() {
        if (getContext() == null) {
            return;
        }

        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        int brandColorRes = Helpers.getThemeAttr(getContext(), R.attr.brandColor);
        int brandAccentColorRes = Helpers.getThemeAttr(getContext(), R.attr.brandAccentColor);

        updateBadge();

        // This title replaces badge in case one is null
        //setTitle(getString(R.string.browse_title));

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getContext(), brandColorRes));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getContext(), brandAccentColorRes));

        setHeaderPresenterSelector(new PresenterSelector() {
            private final Map<Integer, Presenter> mPresenterMap = new HashMap<>();

            @Override
            public Presenter getPresenter(Object o) {
                Presenter presenter = mPresenterMap.get(o.hashCode());

                if (presenter == null) {
                    presenter = new IconHeaderItemPresenter(getHeaderResId(o), getIconUrl(o));
                    mPresenterMap.put(o.hashCode(), presenter);
                }

                return presenter;
            }

            private int getHeaderResId(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
                }

                return -1;
            }

            private String getIconUrl(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getIconUrl();
                }

                return null;
            }
        });
    }

    private View mAccountItem;

    /** TizenTube-like: the current user on top of the sidebar (avatar + name, click = switch account / sign in) */
    private void setupAccountItem() {
        View root = getView();
        ViewGroup dock = root != null ? root.findViewById(androidx.leanback.R.id.browse_headers_dock) : null;

        if (dock == null || getContext() == null || mAccountItem != null) {
            return;
        }

        float d = getResources().getDisplayMetrics().density;
        mAccountItem = getLayoutInflater().inflate(R.layout.icon_header_item, dock, false);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (34 * d), android.view.Gravity.TOP | android.view.Gravity.START);
        lp.leftMargin = (int) (24 * d);
        lp.topMargin = 0;
        mAccountItem.setLayoutParams(lp);
        mAccountItem.setClickable(true);
        // The sidebar items sit on the sidebar background: keep the item above the list
        dock.addView(mAccountItem);
        mAccountItem.bringToFront();
        mAccountItem.setTranslationZ(50 * d); // above the sidebar list (it has its own elevation)

        mAccountItem.setOnClickListener(v -> AccountSelectionPresenter.instance(getContext()).nextAccountOrDialog());
        mAccountItem.setOnLongClickListener(v -> {
            AccountSettingsPresenter.instance(getContext()).show();
            return true;
        });

        IconHeaderItemPresenter.attachExtraItem(mAccountItem);

        // The sections list scrolls with the selection: keep the account right above the first section
        dock.getViewTreeObserver().addOnPreDrawListener(() -> {
            androidx.leanback.widget.VerticalGridView grid = getHeadersSupportFragment() != null ?
                    getHeadersSupportFragment().getVerticalGridView() : null;

            if (grid != null && mAccountItem != null) {
                View first = grid.getChildCount() > 0 ? grid.getChildAt(0) : null;
                boolean firstVisible = first != null && grid.getChildAdapterPosition(first) == 0;
                int[] dockLoc = new int[2];
                int[] firstLoc = new int[2];
                dock.getLocationInWindow(dockLoc);

                if (firstVisible) {
                    first.getLocationInWindow(firstLoc);
                    float top = firstLoc[1] - dockLoc[1] - 38 * d;
                    mAccountItem.setTranslationY(top);
                } else {
                    mAccountItem.setTranslationY(-200 * d); // scrolled out together with the list
                }
            }

            return true;
        });

        // Focus: Up from the first section goes to the account, Down from the account goes back to the sections
        mHandler.post(() -> {
            androidx.leanback.widget.VerticalGridView grid = getHeadersSupportFragment() != null ?
                    getHeadersSupportFragment().getVerticalGridView() : null;

            if (grid != null) {
                grid.setOnKeyInterceptListener(e -> {
                    if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_UP
                            && grid.getSelectedPosition() == 0) {
                        mAccountItem.requestFocus();
                        return true;
                    }
                    return false;
                });
            }
        });
        mAccountItem.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                androidx.leanback.widget.VerticalGridView grid = getHeadersSupportFragment().getVerticalGridView();
                if (grid != null) {
                    grid.requestFocus();
                }
                return true;
            }
            return false;
        });
        updateAccountItem();
        MediaServiceManager.instance().addAccountListener(account -> {
            updateAccountItem();
            mHandler.postDelayed(this::updateAccountItem, 1_000); // the avatar url may arrive later
        });
        mHandler.postDelayed(this::updateAccountItem, 1_000);
    }

    private void updateAccountItem() {
        if (mAccountItem == null || getContext() == null || !isAdded()) {
            return;
        }

        android.widget.ImageView icon = mAccountItem.findViewById(R.id.header_icon);
        android.widget.TextView label = mAccountItem.findViewById(R.id.header_label);
        com.liskovsoft.mediaserviceinterfaces.oauth.Account account = MediaServiceManager.instance().getSelectedAccount();
        float d = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams ilp = icon.getLayoutParams();
        ilp.width = ilp.height = (int) (24 * d);
        icon.setLayoutParams(ilp);

        if (account != null && account.getAvatarImageUrl() != null) {
            String name = account.getName() != null ? account.getName() : account.getEmail();
            label.setText(name != null ? name : getString(R.string.dialog_add_account));
            icon.setTag(R.id.header_icon, Boolean.TRUE);
            icon.clearColorFilter();
            com.bumptech.glide.Glide.with(this).load(account.getAvatarImageUrl()).circleCrop().into(icon);
        } else {
            label.setText(account != null && (account.getName() != null || account.getEmail() != null) ?
                    (account.getName() != null ? account.getName() : account.getEmail()) : getString(R.string.dialog_add_account));
            icon.setTag(R.id.header_icon, null);
            icon.setImageResource(R.drawable.browse_title_account);
            icon.setColorFilter(mAccountItem.hasFocus() && !mSidebarExpanded ? 0xFF0F0F0F : 0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private boolean mSidebarExpanded;
    private boolean mSearchOverlapped;
    private boolean mSearchShown = true;

    /** Search/account buttons belong to the expanded sidebar */
    private void updateTitleOrbs(boolean visible) {
        if (getView() == null) {
            return;
        }

        mSidebarExpanded = visible;

        View orbs = getView().findViewById(R.id.title_orbs_container);
        if (orbs != null) {
            fadeTitleGroup(orbs, visible);
        }

        updateSearchVisibility();
    }

    /** Search/voice are shown only while the sidebar is collapsed and the first row doesn't go under them */
    private void updateSearchVisibility() {
        if (getView() == null) {
            return;
        }

        boolean show = !mSidebarExpanded && !mSearchOverlapped;

        if (show == mSearchShown) {
            return;
        }

        mSearchShown = show;

        View search = getView().findViewById(R.id.title_search_container);
        if (search != null) {
            fadeTitleGroup(search, show);
        }
    }

    private void setupSearchOverlapWatcher() {
        if (getView() == null) {
            return;
        }

        getView().getViewTreeObserver().addOnPreDrawListener(() -> {
            View search = getView() != null ? getView().findViewById(R.id.title_search_container) : null;
            Fragment main = getMainFragment();

            if (search != null && main instanceof RowsSupportFragment && ((RowsSupportFragment) main).getVerticalGridView() != null) {
                VerticalGridView grid = ((RowsSupportFragment) main).getVerticalGridView();
                View first = grid.getChildCount() > 0 ? grid.getChildAt(0) : null;
                boolean overlapped = false;

                if (first != null) {
                    int[] firstLoc = new int[2];
                    int[] searchLoc = new int[2];
                    first.getLocationOnScreen(firstLoc);
                    search.getLocationOnScreen(searchLoc);
                    int searchBottom = searchLoc[1] + search.getHeight();
                    overlapped = grid.getChildAdapterPosition(first) > 0 || firstLoc[1] < searchBottom - 8;
                }

                if (overlapped != mSearchOverlapped) {
                    mSearchOverlapped = overlapped;
                    updateSearchVisibility();
                }
            }

            return true;
        });
    }

    /** Invisible views can't take focus: don't leave hidden buttons focusable */
    private void fadeTitleGroup(View group, boolean show) {
        group.animate().cancel();

        if (show) {
            group.setVisibility(View.VISIBLE);
            group.animate().alpha(1f).setDuration(150).start();
        } else {
            group.animate().alpha(0f).setDuration(150).withEndAction(() -> group.setVisibility(View.INVISIBLE)).start();
        }
    }

    private int getSelectedHeaderId() {
        if (getSelectedPosition() >= mSectionRowAdapter.size()) {
            return -1;
        }

        return (int) ((PageRow) mSectionRowAdapter.get(getSelectedPosition())).getHeaderItem().getId();
    }
    
    public void updateErrorIfEmpty(ErrorFragmentData data) {
        mHandler.postDelayed(() -> showErrorIfEmpty(data), 500); // need delay because header may be not updated
    }

    @Override
    public void showError(ErrorFragmentData data) {
        replaceMainFragment(new ErrorDialogFragment(data));
    }

    private void showErrorIfEmpty(ErrorFragmentData data) {
        if (isEmpty()) {
            replaceMainFragment(new ErrorDialogFragment(data));
        }
    }

    private void replaceMainFragment(Fragment fragment) {
        //Object mainFragment = Helpers.getField(this,"mMainFragment");
        Fragment mainFragment = getMainFragment();

        if (mainFragment != null && fragment != null && mainFragment != fragment) {
            Helpers.setField(this, "mMainFragment", fragment);

            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            ft.replace(R.id.scale_frame, fragment);
            //mFocusOnContent = !isShowingHeaders(); // Fix focus lost when error fragment shown and sidebar is hidden
            mFocusOnContent = hasFocus(); // Maintain focus
            ft.runOnCommit(this::focusOnContentIfNeeded);
            ft.commitAllowingStateLoss(); // FIX: "Can not perform this action after onSaveInstanceState"
        }
    }

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        if (mSections.get(section.getId()) != null && (index == -1 || indexOf(section.getId()) == index)) {
            return;
        }

        removeSection(section);

        mSections.put(section.getId(), section);
        createHeader(index, section);
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        mSections.remove(section.getId());
        removeHeader(section);
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        mSectionRowAdapter.clear();
    }

    @Override
    public void updateSection(VideoGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);

        fixInvisibleSearchOrb();
    }

    @Override
    public void updateSection(SettingsGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index >= 0 && mSectionRowAdapter.size() > 0) {
            mFocusOnContent = focusOnContent; // focus after header transition

            // Fix refresh current section
            if (getSelectedPosition() == index) {
                // update section manually
                // headers transition event not fired on the same index
                focusOnContentIfNeeded();
                mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
            }

            // Need select again if current header is removed previously (can't check for it right now)
            // Fallback to the last section if index above size
            setSelectedPosition(index < mSectionRowAdapter.size() ? index : mSectionRowAdapter.size() - 1, false);
        }
    }

    @Override
    public void focusOnContent() {
        startHeadersTransitionSafe(false);
        if (getMainFragment() != null && getMainFragment().getView() != null) {
            getMainFragment().getView().requestFocus();
        }
    }

    /**
     * Usually called after header transition or fragment transaction
     */
    private void focusOnContentIfNeeded() {
        if (mFocusOnContent) {
            focusOnContent();
            mFocusOnContent = false;
        }
    }

    private boolean hasFocus() {
        if (getMainFragment() == null || getMainFragment().getView() == null) {
            return false;
        }

        return getMainFragment().getView().hasFocus();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index >= 0) {
            mSectionFragmentFactory.setCurrentFragmentItemIndex(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item != null) {
            mSectionFragmentFactory.selectCurrentFragmentItem(item);
        }
    }

    /**
     * Fix: IllegalStateException: "Can not perform this action after onSaveInstanceState"
     */
    private void startHeadersTransitionSafe(boolean withHeaders) {
        // Fix: IllegalStateException: "Can not perform this action after onSaveInstanceState"
        if (!Utils.checkActivity(getActivity())) {
            return;
        }

        try {
            startHeadersTransition(withHeaders);
        } catch (IllegalStateException e) {
            // NOP
        }
    }

    /**
     * Restore after the error fragment
     */
    private void restoreMainFragment() {
        Fragment currentFragment = mSectionFragmentFactory.getCurrentFragment();

        if (currentFragment != null) {
            replaceMainFragment(currentFragment);
        }
    }

    private void createHeader(int index, BrowseSection header) {
        HeaderItem headerItem = new SectionHeaderItem(header);

        PageRow pageRow = new PageRow(headerItem);
        if (index == -1 || mSectionRowAdapter.size() < index) {
            mSectionRowAdapter.add(pageRow); // add to the end
        } else {
            mSectionRowAdapter.add(index, pageRow);
        }
    }

    private void removeHeader(BrowseSection header) {
        Object foundHeader = null;

        for (Object item : mSectionRowAdapter.unmodifiableList()) {
            if (((PageRow) item).getHeaderItem().getId() == header.getId()) {
                foundHeader = item;
                break;
            }
        }

        if (foundHeader != null) {
            mSectionRowAdapter.remove(foundHeader);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        mSectionFragmentFactory.clearCurrentFragment();
    }

    @Override
    public void onDestroyView() {
        mSectionFragmentFactory.cleanup();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    /**
     * Fix suddenly invisible search orb<br/>
     * Could happen on topmost category when the page partially scrolled<br/>
     * More info: {@link TitleHelper}
     */
    private void fixInvisibleSearchOrb() {
        if (isShowingTitle() && getTitleView() != null && getTitleView().getVisibility() != View.VISIBLE) {
            getTitleView().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        Runnable callback;

        if (show) {
            callback = mProgressBarManager::show;
        } else {
            callback = mProgressBarManager::hide;
        }

        // Essential. Need to run on the main thread.
        new Handler(Looper.getMainLooper()).post(callback);
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBarManager.isShowing();
    }

    @Override
    public boolean isEmpty() {
        return mSectionFragmentFactory == null || mSectionFragmentFactory.isEmpty();
    }

    @Override
    public void updateBadge() {
        if (getContext() == null) {
            return;
        }

        SplashPresenter splashPresenter = SplashPresenter.instance(getContext());

        if (splashPresenter == null) {
            return;
        }

        int appLogoRes = Helpers.getThemeAttr(getContext(), R.attr.appLogo);

        Drawable bridgeIcon = Utils.getDrawable(getContext(), splashPresenter.getBridgePackageName(), "app_icon");

        // Top right corner logo
        setBadgeDrawable(bridgeIcon != null ? bridgeIcon : appLogoRes > 0 ? ContextCompat.getDrawable(getContext(), appLogoRes) : null);
    }
}
