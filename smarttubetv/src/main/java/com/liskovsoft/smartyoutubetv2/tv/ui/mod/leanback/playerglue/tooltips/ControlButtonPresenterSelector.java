/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.R;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PaddingAction;

/**
 * Displays primary and secondary controls for a {@link PlaybackControlsRow}.
 *
 * Binds to items of type {@link Action}.
 */
public class ControlButtonPresenterSelector extends PresenterSelector {
    private final ControlButtonPresenter mPrimaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_primary);
    private final ControlButtonPresenter mSecondaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_secondary);
    private final Presenter[] mPresenters = new Presenter[]{mPrimaryPresenter};

    public ControlButtonPresenterSelector(boolean tooltipsEnabled) {
        mPrimaryPresenter.tooltipsEnabled = mSecondaryPresenter.tooltipsEnabled = tooltipsEnabled;
    }

    /**
     * Returns the presenter for primary controls.
     */
    public Presenter getPrimaryPresenter() {
        return mPrimaryPresenter;
    }

    /**
     * Returns the presenter for secondary controls.
     */
    public Presenter getSecondaryPresenter() {
        return mSecondaryPresenter;
    }

    /**
     * Always returns the presenter for primary controls.
     */
    @Override
    public Presenter getPresenter(Object item) {
        return mPrimaryPresenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters;
    }

    static class ActionViewHolder extends Presenter.ViewHolder {
        ImageView mIcon;
        TextView mLabel;
        View mFocusableView;

        public ActionViewHolder(View view) {
            super(view);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mLabel = (TextView) view.findViewById(R.id.label);
            mFocusableView = view.findViewById(R.id.button);
        }
    }

    public static class ControlButtonPresenter extends Presenter {
        private int mLayoutResourceId;
        private boolean tooltipsEnabled;

        ControlButtonPresenter(int layoutResourceId) {
            mLayoutResourceId = layoutResourceId;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(mLayoutResourceId, parent, false);
            return new ActionViewHolder(v);
        }

        // Used inside: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.ViewHolder.bindControlToAction()
        // Restore focus: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBar.onRequestFocusInDescendants()
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Action action = (Action) item;
            ActionViewHolder vh = (ActionViewHolder) viewHolder;

            ViewGroup.LayoutParams blp = vh.mFocusableView.getLayoutParams();
            boolean left = mLayoutResourceId == R.layout.lb_control_button_secondary
                    && (action.getId() == com.liskovsoft.smartyoutubetv2.tv.R.id.action_channel
                    || action.getId() == com.liskovsoft.smartyoutubetv2.tv.R.id.action_info
                    || action.getId() == com.liskovsoft.smartyoutubetv2.tv.R.id.action_subscribe);
            vh.view.setTag(com.liskovsoft.smartyoutubetv2.tv.R.id.header_fade, left);
            float d = vh.view.getResources().getDisplayMetrics().density;
            boolean play = action.getId() == R.id.lb_control_play_pause;
            boolean avatar = action.getId() == com.liskovsoft.smartyoutubetv2.tv.R.id.action_channel;
            ImageView button = (ImageView) vh.mFocusableView;
            if (play) {
                // MOD: bigger play button
                int size = (int) (68 * d);
                if (blp.width != size) {
                    blp.width = size;
                    blp.height = size;
                    button.setLayoutParams(blp);
                }
            }
            if (avatar) {
                // photo: white ring when focused, the picture keeps its colors
                button.setScaleType(ImageView.ScaleType.FIT_XY);
                button.setImageDrawable(new IconButtonDrawable(action.getIcon(), (int) (32 * d)).setTintOnFocus(false));
                vh.mIcon.setImageDrawable(null);
            } else {
                // MOD: TizenTube-like: the button draws its own circle and icon (white circle + dark icon on focus)
                int iconSize = (int) ((play ? 36 : 24) * d);
                int padding = action instanceof PaddingAction ? ((PaddingAction) action).getPadding() : 0;
                if (padding > 0) iconSize = Math.max((int) (8 * d), iconSize - (int) (padding * 0.75f));
                button.setScaleType(ImageView.ScaleType.FIT_XY);
                button.setImageDrawable(new IconButtonDrawable(action.getIcon(), iconSize));
                vh.mIcon.setImageDrawable(null);
            }
            if (action instanceof PaddingAction) {
                int padding = ((PaddingAction) action).getPadding();
                if (padding > 0 && avatar) {
                    vh.mIcon.setPadding(padding, padding, padding, padding);
                }
            }
            if (vh.mLabel != null) {
                if (action.getIcon() == null) {
                    vh.mLabel.setText(action.getLabel1());
                } else {
                    vh.mLabel.setText(null);
                }
            }
            CharSequence contentDescription = TextUtils.isEmpty(action.getLabel2())
                    ? action.getLabel1() : action.getLabel2();
            if (!TextUtils.equals(vh.mFocusableView.getContentDescription(), contentDescription)) {
                vh.mFocusableView.setContentDescription(contentDescription);
                vh.mFocusableView.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);

                // MOD: enable control tooltips
                if (tooltipsEnabled) {
                    TooltipCompatHandler.setTooltipText(vh.mFocusableView, action.getLabel1());
                }
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mIcon.setImageDrawable(null);
            if (vh.mLabel != null) {
                vh.mLabel.setText(null);
            }
            vh.mFocusableView.setContentDescription(null);
        }

        @Override
        public void setOnClickListener(ViewHolder viewHolder,
                                       View.OnClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnClickListener(listener);
        }

        public void setOnLongClickListener(ViewHolder viewHolder,
                                       View.OnLongClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnLongClickListener(listener);
        }
    }
}
