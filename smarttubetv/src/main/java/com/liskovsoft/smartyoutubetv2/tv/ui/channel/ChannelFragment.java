package com.liskovsoft.smartyoutubetv2.tv.ui.channel;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.View;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ChannelHeaderPresenter.ChannelHeaderCallback;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.MultipleRowsFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public class ChannelFragment extends MultipleRowsFragment implements ChannelView {
    private static final String TAG = ChannelFragment.class.getSimpleName();
    private ChannelPresenter mChannelPresenter;
    private ProgressBarManager mProgressBarManager;
    private boolean mIsFragmentCreated;
    private CrashRestorer mCrashRestorer;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        if (getContext() == null) {
            return;
        }

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mChannelPresenter = ChannelPresenter.instance(getContext());
        mChannelPresenter.setView(this);

        mProgressBarManager = new ProgressBarManager();
        if (MainUIData.instance(getContext()).isChannelSearchBarEnabled()) {
            addHeader(new ChannelHeaderCallback() {
                @Override
                public void onSearchSettingsClicked() {
                    mChannelPresenter.onSearchSettingsClicked();
                }

                @Override
                public boolean onSearchSubmit(String query) {
                    return mChannelPresenter.onSearchSubmit(query);
                }

                @Override
                public String getChannelTitle() {
                    ChannelInfo i = mChannelPresenter.getChannelInfo();
                    if (mChannelPresenter.getChannel() == null && i != null && i.getTitle() != null) {
                        return i.getTitle();
                    }

                    if (mChannelPresenter.getChannel() == null) {
                        return Helpers.startsWith(mChannelPresenter.getChannelId(), "@") ? mChannelPresenter.getChannelId() : null;
                    }

                    ChannelInfo info = mChannelPresenter.getChannelInfo();
                    String author = mChannelPresenter.getChannel().getAuthor();
                    String title = mChannelPresenter.getChannel().getTitle();

                    return Helpers.firstNonNull(info != null ? info.getTitle() : null, author, title);
                }

                @Override
                public String getChannelSubtitle() {
                    ChannelInfo info = mChannelPresenter.getChannelInfo();
                    Video channel = mChannelPresenter.getChannel();
                    String subs = Helpers.firstNonNull(info != null ? info.getSubscriberCount() : null, channel != null ? channel.subscriberCount : null);
                    String handle = info != null ? info.getHandle() : null;

                    if (handle != null && subs != null) {
                        return handle + " • " + subs;
                    }

                    return Helpers.firstNonNull(handle, subs);
                }

                @Override
                public String getChannelDescription() {
                    ChannelInfo info = mChannelPresenter.getChannelInfo();
                    return info != null ? info.getDescription() : null;
                }

                @Override
                public String getChannelAvatarUrl() {
                    ChannelInfo info = mChannelPresenter.getChannelInfo();
                    return info != null ? info.getAvatarUrl() : null;
                }

                @Override
                public boolean isSubscribed() {
                    return mChannelPresenter.isChannelSubscribed();
                }

                @Override
                public boolean isPinned() {
                    return mChannelPresenter.isChannelPinned();
                }

                @Override
                public boolean hasMix() {
                    return mChannelPresenter.hasChannelMix();
                }

                @Override
                public void onPlayMixClicked() {
                    mChannelPresenter.playChannelMix();
                }

                @Override
                public void onSubscribeClicked() {
                    mChannelPresenter.toggleChannelSubscribe();
                }

                @Override
                public void onPinClicked() {
                    mChannelPresenter.toggleChannelPin();
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        mCrashRestorer.persistHeaderIndex(outState, getPosition());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getActivity() == null) {
            return;
        }

        // Don't move to onCreateView
        mProgressBarManager.setRootView((ViewGroup) getActivity().findViewById(android.R.id.content).getRootView());

        setupBanner();

        mChannelPresenter.onViewInitialized();
        
        // Restore state after crash
        mCrashRestorer.restoreHeader((idx, video) -> {
            setPosition(idx);
        });
        mCrashRestorer.restorePlayback();
    }

    private void setupBanner() {
        View banner = getActivity().findViewById(R.id.channel_banner);
        View scrim = getActivity().findViewById(R.id.channel_banner_scrim);

        if (banner == null || getVerticalGridView() == null) {
            return;
        }

        // The banner scrolls away together with the header row
        getVerticalGridView().addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                View first = rv.getChildCount() > 0 ? rv.getChildAt(0) : null;
                boolean headerVisible = first != null && rv.getChildAdapterPosition(first) == 0;
                float offset = headerVisible ? first.getTop() : -banner.getHeight();
                banner.setTranslationY(Math.min(0, offset));
                scrim.setTranslationY(Math.min(0, offset));
            }
        });
    }

    @Override
    public void updateHeader() {
        refreshHeader();

        ChannelInfo info = mChannelPresenter.getChannelInfo();

        if (getActivity() == null || info == null) {
            return;
        }

        ImageView banner = getActivity().findViewById(R.id.channel_banner);
        View scrim = getActivity().findViewById(R.id.channel_banner_scrim);

        if (banner != null && scrim != null && info.getBannerUrl() != null) {
            banner.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
            Glide.with(getActivity()).load(info.getBannerUrl()).into(banner);
        }
    }

    @Override
    protected VideoGroupPresenter getMainPresenter() {
        return ChannelPresenter.instance(getContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mChannelPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelPresenter.onFinish();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mChannelPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void showProgressBar(boolean show) {
        if (show) {
            mProgressBarManager.show();
        } else {
            mProgressBarManager.hide();
        }
    }
}
