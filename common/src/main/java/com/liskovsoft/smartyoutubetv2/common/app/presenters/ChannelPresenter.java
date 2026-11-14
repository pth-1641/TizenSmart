package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.VideoGroupPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.misc.BrowseProcessorManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class ChannelPresenter extends BasePresenter<ChannelView> implements VideoGroupPresenter {
    private static final String TAG = ChannelPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ChannelPresenter sInstance;
    private final BrowseProcessorManager mBrowseProcessor;
    private String mChannelId;
    private final List<List<MediaGroup>> mPendingGroups = new ArrayList<>();
    private Disposable mUpdateAction;
    private Disposable mScrollAction;
    private int mSortIdx;
    private Video mChannel;
    private boolean mSubscribed;
    private com.liskovsoft.mediaserviceinterfaces.data.ChannelInfo mChannelInfo;
    private String mInfoChannelId;
    private Disposable mInfoAction;

    private interface OnChannelId {
        void onChannelId(String channelId);
    }

    public interface OnUploadsRow {
        void onUploadsRow(Observable<MediaGroup> row);
    }

    public ChannelPresenter(Context context) {
        super(context);
        mBrowseProcessor = new BrowseProcessorManager(getContext(), this::syncItem);
    }

    public static ChannelPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();

        if (mChannelId != null) {
            getView().clear();
            loadChannelInfo(mChannelId);
            updateRows(obtainChannelObservable(mChannelId));
        } else if (!mPendingGroups.isEmpty()) {
            getView().clear();
            for (List<MediaGroup> group : mPendingGroups) {
                updateRows(group);
            }
            mPendingGroups.clear();
        }
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        disposeActions();
    }

    @Override
    public void onFinish() {
        super.onFinish();

        // Destroy the cache only (!) when user pressed back (e.g. wants to explicitly kill the activity)
        // Otherwise keep the cache to easily restore in case activity is killed by the system.
        mChannelId = null;
        mChannelInfo = null;
        mInfoChannelId = null;
        mPendingGroups.clear();
        disposeActions();
    }

    @Override
    public void onVideoItemSelected(Video item) {
        // NOP
    }

    @Override
    public void onVideoItemClicked(Video item) {
        VideoActionPresenter.instance(getContext()).apply(item);
    }

    @Override
    public void onVideoItemLongClicked(Video item) {
        VideoMenuPresenter.instance(getContext()).showMenu(item);
    }

    @Override
    public void onScrollEnd(Video item) {
        if (item == null) {
            Log.e(TAG, "Can't scroll. Video is null.");
            return;
        }

        if (item.getGroup() == null) {
            Log.e(TAG, "Can't scroll. Video group is null.");
            return;
        }

        VideoGroup group = item.getGroup();

        Log.d(TAG, "onScrollEnd: Group title: " + group.getTitle());

        continueGroup(group);
    }

    @Override
    public boolean hasPendingActions() {
        return RxHelper.isAnyActionRunning(mScrollAction, mUpdateAction);
    }

    public static boolean canOpenChannel(Video item) {
        if (item == null) {
            return false;
        }

        return item.videoId != null || item.channelId != null || item.belongsToChannelUploads();
    }

    public void openChannel(Video item) {
        mChannel = item;
        mSubscribed = item != null && item.isSubscribed;
        extractChannelId(item, this::openChannel);
    }

    public void openChannel(String channelId) {
        if (channelId == null) {
            return;
        }

        disposeActions();

        mChannelId = channelId;

        if (getView() != null) {
            getView().clear();
            loadChannelInfo(channelId);
            updateRows(obtainChannelObservable(channelId));
            // Fix double results. Prevent from doing the same in onViewInitialized()
            //mChannelId = null;
        }

        getViewManager().startView(ChannelView.class);
    }

    public String getChannelId() {
        return mChannel != null && mChannel.channelId != null ? mChannel.channelId : mChannelId;
    }

    public void setChannelId(String channelId) {
        mChannelId = channelId;
    }

    public Video getChannel() {
        return mChannel;
    }

    public void setChannel(Video channel) {
        mChannel = channel;
        mSubscribed = channel != null && channel.isSubscribed;
    }

    public com.liskovsoft.mediaserviceinterfaces.data.ChannelInfo getChannelInfo() {
        return mChannelInfo;
    }

    private void loadChannelInfo(String channelId) {
        if (channelId == null || channelId.equals(mInfoChannelId)) {
            return;
        }

        mInfoChannelId = channelId;
        mChannelInfo = null;
        RxHelper.disposeActions(mInfoAction);

        mInfoAction = getContentService().getChannelInfoObserve(channelId)
                .subscribe(
                        info -> {
                            mChannelInfo = info;
                            if (getView() != null) {
                                getView().updateHeader();
                            }
                        },
                        error -> Log.e(TAG, "loadChannelInfo error: %s", error.getMessage())
                );
    }

    public boolean hasChannelMix() {
        return mChannelInfo != null && mChannelInfo.getMixVideoId() != null;
    }

    public void playChannelMix() {
        if (!hasChannelMix()) {
            return;
        }

        Video video = new Video();
        video.videoId = mChannelInfo.getMixVideoId();
        video.playlistParams = mChannelInfo.getMixParams();

        PlaybackPresenter.instance(getContext()).openVideo(video);
    }

    public boolean isChannelSubscribed() {
        return mSubscribed;
    }

    /**
     * Header button: subscribe/unsubscribe.
     */
    public void toggleChannelSubscribe() {
        String channelId = getChannelId();

        if (channelId == null) {
            return;
        }

        com.liskovsoft.mediaserviceinterfaces.MediaItemService service =
                com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getMediaItemService();

        RxHelper.execute(mSubscribed ? service.unsubscribeObserve(channelId) : service.subscribeObserve(channelId));

        mSubscribed = !mSubscribed;
        if (mChannel != null) {
            mChannel.isSubscribed = mSubscribed;
        }

        MessageHelpers.showMessage(getContext(), mSubscribed ? R.string.subscribed_to_channel : R.string.unsubscribed_from_channel);
    }

    private Video createPinnedChannel() {
        String channelId = getChannelId();

        if (channelId == null) {
            return null;
        }

        Video section = mChannel != null ? Video.from(mChannel) : new Video();
        section.channelId = channelId;
        section.videoId = section.playlistId = section.playlistParams = null;
        // The opened item could be a video: the section must be titled by the channel name
        String name = mChannelInfo != null ? mChannelInfo.getTitle() : null;
        if (name == null && mChannel != null) {
            name = mChannel.isChannel() ? mChannel.getTitle() : mChannel.getAuthor();
        }
        section.title = name;
        section.author = null;
        section.cardImageUrl = mChannelInfo != null && mChannelInfo.getAvatarUrl() != null ?
                mChannelInfo.getAvatarUrl() : (mChannel != null && mChannel.isChannel() ? mChannel.cardImageUrl : null);

        return section;
    }

    public boolean isChannelPinned() {
        Video section = createPinnedChannel();

        return section != null && BrowsePresenter.instance(getContext()).isItemPinned(section);
    }

    /**
     * Header button: add/remove the channel to/from the sidebar.
     */
    public void toggleChannelPin() {
        Video section = createPinnedChannel();

        if (section == null) {
            return;
        }

        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (presenter.isItemPinned(section)) {
            presenter.unpinItem(section);
            MessageHelpers.showMessage(getContext(), R.string.unpinned_from_sidebar);
        } else {
            presenter.pinItem(section);
            MessageHelpers.showMessage(getContext(), R.string.pinned_to_sidebar);
        }
    }

    private void disposeActions() {
        RxHelper.disposeActions(mUpdateAction, mScrollAction);
        getServiceManager().disposeActions();
        mSortIdx = 0;
        mBrowseProcessor.dispose();
    }

    private void updateRows(Observable<List<MediaGroup>> group) {
        Log.d(TAG, "updateRows: Start loading...");

        disposeActions();

        getView().showProgressBar(true);

        mUpdateAction = group
                .subscribe(
                        this::updateRows,
                        error -> {
                            Log.e(TAG, "updateRows error: %s", error.getMessage());
                            getView().showProgressBar(false);
                        }
                 );
    }

    public Observable<List<MediaGroup>> obtainChannelObservable(String channelId) {
        return getContentService().getChannelObserve(channelId);
    }

    public void updateRows(List<MediaGroup> mediaGroups) {
        if (getView() == null) { // starting from outside (e.g. MediaServiceManager)
            mChannelId = null;
            mPendingGroups.add(mediaGroups);
            getViewManager().startView(ChannelView.class);
            return;
        }

        // The view could be running in the background
        getViewManager().startView(ChannelView.class);

        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup.getMediaItems() == null) {
                Log.e(TAG, "updateRowsHeader: MediaGroup is empty. Group Name: " + mediaGroup.getTitle());
                continue;
            }

            VideoGroup group = VideoGroup.from(mediaGroup);
            getView().update(group);
            mBrowseProcessor.process(group);
        }

        getView().showProgressBar(false);
    }

    private void continueGroup(VideoGroup group) {
        boolean scrollInProgress = mScrollAction != null && !mScrollAction.isDisposed();

        if (scrollInProgress) {
            return;
        }

        if (getView() == null) {
            Log.e(TAG, "Can't continue group. The view is null.");
            return;
        }

        if (group == null) {
            Log.e(TAG, "Can't continue group. The group is null.");
            return;
        }

        Log.d(TAG, "continueGroup: start continue group: " + group.getTitle());

        getView().showProgressBar(true);

        MediaGroup mediaGroup = group.getMediaGroup();

        mScrollAction = getContentService().continueGroupObserve(mediaGroup)
                .subscribe(
                        continueMediaGroup -> {
                            VideoGroup newGroup = VideoGroup.from(group, continueMediaGroup);
                            getView().update(newGroup);
                            mBrowseProcessor.process(newGroup);
                        },
                        error -> {
                            Log.e(TAG, "continueGroup error: %s", error.getMessage());
                            if (getView() != null) {
                                getView().showProgressBar(false);
                            }
                        },
                        () -> getView().showProgressBar(false)
                );
    }

    /**
     * Sort channel content: move Uploads on top.
     */
    private void moveToTopIfNeeded(List<MediaGroup> mediaGroups) {
        moveToTop(mediaGroups, R.string.playlists_row_name);
        moveToTop(mediaGroups, R.string.popular_uploads_row_name);
        moveToTop(mediaGroups, R.string.uploads_row_name);
        moveToTop(mediaGroups, R.string.live_now_row_name);
    }

    private void moveToTop(List<MediaGroup> mediaGroups, int rowNameResId) {
        if (rowNameResId <= 0) {
            return;
        }

        String rowName = getContext().getString(rowNameResId);

        List<MediaGroup> group = Helpers.removeIf(mediaGroups, value -> rowName.equals(value.getTitle()));

        if (group != null) {
            mediaGroups.addAll(0, group);
        }
    }

    public void clear() {
        if (getView() != null) {
            getView().clear();
        }
        mChannel = null;
        mChannelId = null;
        mChannelInfo = null;
        mInfoChannelId = null;
    }

    private void extractChannelId(Video item, OnChannelId callback) {
        if (item != null) {
            if (item.channelId != null) {
                callback.onChannelId(item.channelId);
            } else if (item.videoId != null) {
                LoadingManager.showLoading(getContext(), true);
                getServiceManager().loadMetadata(item, metadata -> {
                    LoadingManager.showLoading(getContext(), false);
                    callback.onChannelId(metadata.getChannelId());
                    item.channelId = metadata.getChannelId();
                },
                e -> LoadingManager.showLoading(getContext(), false),
                () -> LoadingManager.showLoading(getContext(), false));
            } else if (item.belongsToChannelUploads()) {
                LoadingManager.showLoading(getContext(), true);
                // Maybe this is subscribed items view
                ChannelUploadsPresenter.instance(getContext())
                        .obtainGroup(item, group -> {
                            LoadingManager.showLoading(getContext(), false);
                            // Some uploads groups doesn't contain channel button.
                            // Use data from first item instead.
                            if (group.getChannelId() == null) {
                                List<MediaItem> mediaItems = group.getMediaItems();

                                // Filter collaborative items
                                MediaItem first = Helpers.findFirst(mediaItems, mediaItem -> Helpers.startsWith(mediaItem.getAuthor(), item.getAuthor()));

                                if (first == null && mediaItems != null && !mediaItems.isEmpty()) {
                                    first = mediaItems.get(0);
                                }

                                if (first != null) {
                                    extractChannelId(Video.from(first), callback);
                                }

                                return;
                            }

                            callback.onChannelId(group.getChannelId());
                            item.channelId = group.getChannelId();
                        },
                        e -> LoadingManager.showLoading(getContext(), false),
                        () -> LoadingManager.showLoading(getContext(), false));
            }
        }
    }

    public void onSearchSettingsClicked() {
        Observable<List<MediaGroup>> sorting = getContentService().getChannelSortingOptionsObserve(getChannelId());
        Disposable result = sorting.subscribe(
                items -> {
                    AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                    List<OptionItem> options = new ArrayList<>();
                    int idx = 0;
                    for (MediaGroup group : items) {
                        final int tempIdx = idx;
                        options.add(UiOptionItem.from(group.getTitle(), item -> {
                            //dialogPresenter.closeDialog();
                            Observable<MediaGroup> continuation = getContentService().continueGroupObserve(group);
                            Disposable result2 = continuation.subscribe(mediaGroup -> {
                                if (getView() == null) {
                                    return;
                                }

                                VideoGroup replace = VideoGroup.from(mediaGroup);
                                replace.setId(144);
                                replace.setPosition(0);
                                replace.setAction(VideoGroup.ACTION_REPLACE);
                                getView().update(replace);
                                //getView().setPosition(1);
                                mSortIdx = tempIdx;
                            });
                        }, mSortIdx == idx));
                        idx++;
                    }
                    dialogPresenter.appendRadioCategory(getContext().getString(R.string.search_sorting), options);
                    dialogPresenter.showDialog();
                },
                error -> Log.e(TAG, "onSearchSettingsClicked error: %s", error.getMessage())
        );
    }

    public boolean onSearchSubmit(String query) {
        Observable<MediaGroup> search = getContentService().getChannelSearchObserve(getChannelId(), query);
        Disposable result = search.subscribe(
                items -> {
                    if (getView() == null) {
                        return;
                    }

                    VideoGroup update = VideoGroup.from(items);

                    if (update.isEmpty()) {
                        MessageHelpers.showMessage(getContext(), R.string.nothing_found);
                        return;
                    }

                    update.setId(112);
                    update.setPosition(0);
                    update.setAction(VideoGroup.ACTION_REPLACE);
                    getView().update(update);
                    getView().setPosition(1);
                },
                error -> Log.e(TAG, "onSearchSubmit error: %s", error.getMessage())
        );

        return true;
    }
}
