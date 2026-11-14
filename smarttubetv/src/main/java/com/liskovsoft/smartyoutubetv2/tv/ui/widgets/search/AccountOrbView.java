package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search;

import android.content.Context;
import android.util.AttributeSet;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Round account button (TizenTube style): user icon or avatar. Supports long click.
 */
public class AccountOrbView extends LongClickSearchOrbView {
    public AccountOrbView(Context context) {
        this(context, null);
    }

    public AccountOrbView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AccountOrbView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.lb_account_orb;
    }

    @Override
    protected int getIdleColor() {
        return 0xFF272727;
    }
}
