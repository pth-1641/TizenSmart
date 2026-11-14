package com.liskovsoft.mediaserviceinterfaces.data;

/**
 * Channel page header: banner, avatar, handle, subscribers, description.
 */
public interface ChannelInfo {
    String getTitle();
    String getHandle();
    String getSubscriberCount();
    String getDescription();
    String getAvatarUrl();
    String getBannerUrl();
    String getMixVideoId();
    String getMixParams();
}
