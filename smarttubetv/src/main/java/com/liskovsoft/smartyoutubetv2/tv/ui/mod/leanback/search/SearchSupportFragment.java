package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.search;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.leanback.R;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.ObjectAdapter.DataObserver;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter.ViewHolder;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SearchBar;
import androidx.leanback.widget.SearchEditText;
import androidx.leanback.widget.SearchOrbView;
import androidx.leanback.widget.SpeechOrbView;
import androidx.leanback.widget.SpeechRecognitionCallback;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;

import java.util.ArrayList;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * A fragment to handle searches. An application will supply an implementation
 * of the {@link SearchResultProvider} interface to handle the search and return
 * an {@link ObjectAdapter} containing the results. The results are rendered
 * into a {@link RowsSupportFragment}, in the same way that they are in a {@link
 * BrowseSupportFragment}.
 *
 * <p>A SpeechRecognizer object will be created for which your application will need to declare
 * android.permission.RECORD_AUDIO in AndroidManifest file. If app's target version is >= 23 and
 * the device version is >= 23, a permission dialog will show first time using speech recognition.
 * 0 will be used as requestCode in requestPermissions() call.
 * {@link #setSpeechRecognitionCallback(SpeechRecognitionCallback)} is deprecated.
 * </p>
 * <p>
 * Speech recognition is automatically started when fragment is created, but
 * not when fragment is restored from an instance state.  Activity may manually
 * call {@link #startRecognition()}, typically in onNewIntent().
 * </p>
 */
public class SearchSupportFragment extends Fragment {
    static final String TAG = SearchSupportFragment.class.getSimpleName();
    static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String EXTRA_LEANBACK_BADGE_PRESENT = "LEANBACK_BADGE_PRESENT";
    private static final String ARG_PREFIX = SearchSupportFragment.class.getCanonicalName();
    private static final String ARG_QUERY =  ARG_PREFIX + ".query";
    private static final String ARG_TITLE = ARG_PREFIX  + ".title";

    static final long SPEECH_RECOGNITION_DELAY_MS = 300;

    static final int RESULTS_CHANGED = 0x1;
    static final int QUERY_COMPLETE = 0x2;

    static final int AUDIO_PERMISSION_REQUEST_CODE = 0;

    /**
     * Search API to be provided by the application.
     */
    public static interface SearchResultProvider {
        /**
         * <p>Method invoked some time prior to the first call to onQueryTextChange to retrieve
         * an ObjectAdapter that will contain the results to future updates of the search query.</p>
         *
         * <p>As results are retrieved, the application should use the data set notification methods
         * on the ObjectAdapter to instruct the SearchSupportFragment to update the results.</p>
         *
         * @return ObjectAdapter The result object adapter.
         */
        public ObjectAdapter getResultsAdapter();

        /**
         * <p>Method invoked when the search query is updated.</p>
         *
         * <p>This is called as soon as the query changes; it is up to the application to add a
         * delay before actually executing the queries if needed.
         *
         * <p>This method might not always be called before onQueryTextSubmit gets called, in
         * particular for voice input.
         *
         * @param newQuery The current search query.
         * @return whether the results changed as a result of the new query.
         */
        public boolean onQueryTextChange(String newQuery);

        /**
         * Method invoked when the search query is submitted, either by dismissing the keyboard,
         * pressing search or next on the keyboard or when voice has detected the end of the query.
         *
         * @param query The query entered.
         * @return whether the results changed as a result of the query.
         */
        public boolean onQueryTextSubmit(String query);
    }

    final DataObserver mAdapterObserver = new DataObserver() {
        @Override
        public void onChanged() {
            // onChanged() may be called multiple times e.g. the provider add
            // rows to ArrayObjectAdapter one by one.
            mHandler.removeCallbacks(mResultsChangedCallback);
            mHandler.post(mResultsChangedCallback);
        }
    };

    final Handler mHandler = new Handler();

    final Runnable mResultsChangedCallback = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.v(TAG, "results changed, new size " + mResultAdapter.size());
            if (mRowsSupportFragment != null
                    && mRowsSupportFragment.getAdapter() != mResultAdapter) {
                if (!(mRowsSupportFragment.getAdapter() == null && mResultAdapter.size() == 0)) {
                    mRowsSupportFragment.setAdapter(mResultAdapter);
                    mRowsSupportFragment.setSelectedPosition(0);
                }
            }
            updateSearchBarVisibility();
            mStatus |= RESULTS_CHANGED;
            if ((mStatus & QUERY_COMPLETE) != 0) {
                updateFocus();
            }
            updateSearchBarNextFocusId();
        }
    };

    /**
     * Runs when a new provider is set AND when the fragment view is created.
     */
    private final Runnable mSetSearchResultProvider = new Runnable() {
        @Override
        public void run() {
            if (mRowsSupportFragment == null) {
                // We'll retry once we have a rows fragment
                return;
            }
            // Retrieve the result adapter
            ObjectAdapter adapter = mProvider.getResultsAdapter();
            if (DEBUG) Log.v(TAG, "Got results adapter " + adapter);
            if (adapter != mResultAdapter) {
                boolean firstTime = mResultAdapter == null;
                releaseAdapter();
                mResultAdapter = adapter;
                if (mResultAdapter != null) {
                    mResultAdapter.registerObserver(mAdapterObserver);
                }
                if (DEBUG) {
                    Log.v(TAG, "mResultAdapter " + mResultAdapter + " size "
                            + (mResultAdapter == null ? 0 : mResultAdapter.size()));
                }
                // delay the first time to avoid setting a empty result adapter
                // until we got first onChange() from the provider
                if (!(firstTime && (mResultAdapter == null || mResultAdapter.size() == 0))) {
                    mRowsSupportFragment.setAdapter(mResultAdapter);
                }
                executePendingQuery();
            }
            updateSearchBarNextFocusId();

            if (DEBUG) {
                Log.v(TAG, "mAutoStartRecognition " + mAutoStartRecognition
                        + " mResultAdapter " + mResultAdapter
                        + " adapter " + mRowsSupportFragment.getAdapter());
            }
            if (mAutoStartRecognition) {
                mHandler.removeCallbacks(mStartRecognitionRunnable);
                mHandler.postDelayed(mStartRecognitionRunnable, SPEECH_RECOGNITION_DELAY_MS);
            } else {
                updateFocus();
            }
        }
    };

    final Runnable mStartRecognitionRunnable = new Runnable() {
        @Override
        public void run() {
            mAutoStartRecognition = false;
            startRecognitionInt();
        }
    };

    RowsSupportFragment mRowsSupportFragment;
    SearchBar mSearchBar;
    SearchEditText mSearchTextEditor;
    SpeechOrbView mSpeechOrbView;
    SearchResultProvider mProvider;
    String mPendingQuery = null;
    SearchOrbView mSearchOrbView;
    SearchOrbView mSearchSettingsOrbView;

    OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    ObjectAdapter mResultAdapter;
    private SpeechRecognitionCallback mSpeechRecognitionCallback;

    private String mTitle;
    private Drawable mBadgeDrawable;
    private ExternalQuery mExternalQuery;
    private boolean mIsKeyboardAutoShowEnabled;
    private boolean mIsKeyboardFixEnabled;
    private boolean mIsTypingCorrectionDisabled;

    private SpeechRecognizer mSpeechRecognizer;

    int mStatus;
    boolean mAutoStartRecognition = false; // MOD: don't start search immediately
    
    private boolean mPendingStartRecognition;
    private SearchBar.SearchBarPermissionListener mPermissionListener =
            new SearchBar.SearchBarPermissionListener() {
        @Override
        public void requestAudioPermission() {
            try {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                        AUDIO_PERMISSION_REQUEST_CODE);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                MessageHelpers.showMessage(getContext(), e.getMessage());
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            if (permissions[0].equals(Manifest.permission.RECORD_AUDIO)
                    && grantResults[0] == PERMISSION_GRANTED) {
                startRecognition();
            } else {
                MessageHelpers.showMessage(getContext(), com.liskovsoft.smartyoutubetv2.common.R.string.not_supported_by_device);
            }
        }
    }

    /**
     * @param args Bundle to use for the arguments, if null a new Bundle will be created.
     */
    public static Bundle createArgs(Bundle args, String query) {
        return createArgs(args, query, null);
    }

    public static Bundle createArgs(Bundle args, String query, String title)  {
        if (args == null) {
            args = new Bundle();
        }
        args.putString(ARG_QUERY, query);
        args.putString(ARG_TITLE, title);
        return args;
    }

    /**
     * Creates a search fragment with a given search query.
     *
     * <p>You should only use this if you need to start the search fragment with a
     * pre-filled query.
     *
     * @param query The search query to begin with.
     * @return A new SearchSupportFragment.
     */
    public static SearchSupportFragment newInstance(String query) {
        SearchSupportFragment fragment = new SearchSupportFragment();
        Bundle args = createArgs(null, query);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (mAutoStartRecognition) {
            mAutoStartRecognition = savedInstanceState == null;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.lb_search_fragment, container, false);

        FrameLayout searchFrame = (FrameLayout) root.findViewById(R.id.lb_search_frame);
        // Breathing room from the screen edges
        int sideGap = (int) (40 * getResources().getDisplayMetrics().density);
        searchFrame.setClipToPadding(false); // the back button sits in the padding area
        searchFrame.setPadding(sideGap, searchFrame.getPaddingTop(), sideGap, searchFrame.getPaddingBottom());
        mSearchBar = (SearchBar) searchFrame.findViewById(R.id.lb_search_bar);
        mSearchBar.setOnFocusChangeListener((v, focused) -> {
            Log.d(TAG, "search bar focused");
        });
        mSearchBar.setSearchBarListener(new SearchBar.SearchBarListener() {
            @Override
            public void onSearchQueryChange(String query) {
                if (DEBUG) Log.v(TAG, String.format("onSearchQueryChange %s %s", query,
                        null == mProvider ? "(null)" : mProvider));
                if (null != mProvider) {
                    retrieveResults(query);
                } else {
                    mPendingQuery = query;
                }
            }

            @Override
            public void onSearchQuerySubmit(String query) {
                if (DEBUG) Log.v(TAG, String.format("onSearchQuerySubmit %s", query));
                submitQuery(query);
            }

            @Override
            public void onKeyboardDismiss(String query) {
                if (DEBUG) Log.v(TAG, String.format("onKeyboardDismiss %s", query));
                // MOD: don't focus on results row after hiding keyboard
                //queryComplete();
            }
        });
        mSearchBar.setSpeechRecognitionCallback(mSpeechRecognitionCallback);
        mSearchBar.setPermissionListener(mPermissionListener);
        applyExternalQuery();

        // MOD: inner search bar views for improved focus handling

        mSearchTextEditor = mSearchBar.findViewById(R.id.lb_search_text_editor);
        mSearchTextEditor.setSelectAllOnFocus(true); // Select all on focus (easy clear previous search)
        mSearchTextEditor.setOnFocusChangeListener((v, focused) -> {
            Log.d(TAG, "on search field focused");

            // User clicked on tag and tries to edit search query
            if (focused && !TextUtils.isEmpty(getSearchBarText())) {
                SearchPresenter.instance(v.getContext()).disposeActions();
            }

            if (mIsKeyboardAutoShowEnabled && focused) {
                Helpers.showKeyboardAlt(v.getContext(), v);
            }
        });
        mSearchTextEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // NOP
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // NOP
            }

            @Override
            public void afterTextChanged(Editable s) {
                Utils.enableScreensaver(getActivity(), true);
            }
        });
        if (mIsKeyboardFixEnabled) {
            KeyHelpers.fixEnterKey(mSearchTextEditor);
        }
        if (mIsTypingCorrectionDisabled) {
            mSearchTextEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }
        // BUGFIX: focus lost with keyboard???
        //mSearchTextEditor.setOnKeyboardDismissListener(this::focusOnSearchField);

        // MOD: commit search button
        mSearchOrbView = mSearchBar.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.lb_search_bar_search_orb);
        mSearchOrbView.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                Helpers.hideKeyboard(getContext(), v);
            }
        });
        mSearchOrbView.setOnOrbClickedListener(v -> submitQuery(getSearchBarText()));

        // MOD: search settings button
        mSearchSettingsOrbView = mSearchBar.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.search_settings_orb);
        mSearchSettingsOrbView.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                Helpers.hideKeyboard(getContext(), v);
            }
        });
        mSearchSettingsOrbView.setOnOrbClickedListener(v -> onSearchSettingsClicked());

        mSpeechOrbView = mSearchBar.findViewById(R.id.lb_search_bar_speech_orb);
        // Mic as tall as the input pill (TizenTube proportions)
        View orbBg = mSpeechOrbView.findViewById(R.id.search_orb);
        if (orbBg != null) {
            int size = (int) (42 * getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams orbLp = orbBg.getLayoutParams();
            orbLp.width = size;
            orbLp.height = size;
            orbBg.setLayoutParams(orbLp);
        }
        OnFocusChangeListener previousListener = mSpeechOrbView.getOnFocusChangeListener();
        mSpeechOrbView.setOnFocusChangeListener((v, focused) -> {
            if (!focused) {
                stopSpeechService();
            }

            // Fix: Enable edit field dynamic style: white/grey, listening/non listening
            if (previousListener != null) {
                previousListener.onFocusChange(v, focused);
            }

            // TizenTube-like: white background and dark icon when focused (same as the keys)
            SearchOrbView.Colors micColors = focused ?
                    new SearchOrbView.Colors(0xFFF1F1F1, 0xFFF1F1F1, 0xFF0F0F0F) :
                    new SearchOrbView.Colors(0xFF282828, 0xFF282828, 0xFFFFFFFF);
            mSpeechOrbView.setNotListeningOrbColors(micColors);
            mSpeechOrbView.setOrbColors(micColors);
        });

        // MOD: TizenTube-like on-screen keyboard
        setupOnScreenKeyboard(searchFrame);

        // End MOD

        readArguments(getArguments());
        if (null != mBadgeDrawable) {
            setBadgeDrawable(mBadgeDrawable);
        }
        if (null != mTitle) {
            setTitle(mTitle);
        }

        // Inject the RowsSupportFragment in the results container
        if (getChildFragmentManager().findFragmentById(R.id.lb_results_frame) == null) {
            mRowsSupportFragment = new RowsSupportFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.lb_results_frame, mRowsSupportFragment).commitAllowingStateLoss();
        } else {
            mRowsSupportFragment = (RowsSupportFragment) getChildFragmentManager()
                    .findFragmentById(R.id.lb_results_frame);
        }
        mRowsSupportFragment.setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(ViewHolder itemViewHolder, Object item,
                                       RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (DEBUG) {
                    int position = mRowsSupportFragment.getSelectedPosition();
                    Log.v(TAG, String.format("onItemSelected %d", position));
                }
                updateSearchBarVisibility();
                if (null != mOnItemViewSelectedListener) {
                    mOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                            rowViewHolder, row);
                }
            }
        });
        mRowsSupportFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);
        mRowsSupportFragment.setExpand(true);
        if (null != mProvider) {
            onSetSearchResultProvider();
        }
        return root;
    }

    private OnScreenKeyboard mOnScreenKeyboard;
    private View mBackButton;
    private int mFocusRetries;
    private final Runnable mRetryFocusOnResults = this::focusOnResults;
    private boolean mResultsMode;
    private boolean mFromSuggestion;
    private SuggestionList mSuggestionList;
    private SuggestionList.Listener mSuggestionListener;

    protected void setSuggestionListener(SuggestionList.Listener listener) {
        mSuggestionListener = listener;
    }

    protected void showSuggestions(List<String> labels, List<?> payloads, List<Boolean> history) {
        if (mSuggestionList != null) {
            mSuggestionList.setItems(labels, payloads, history);
        }
    }

    private void focusResultsGrid() {
        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null
                && mResultAdapter != null && mResultAdapter.size() > 0) {
            mRowsSupportFragment.getVerticalGridView().requestFocus();
        }
    }

    private void setupOnScreenKeyboard(FrameLayout searchFrame) {
        final Context context = searchFrame.getContext();
        final float density = context.getResources().getDisplayMetrics().density;

        // Never pop up the system keyboard (the \u2328 key of the on-screen keyboard still opens it on demand)
        mSearchTextEditor.setShowSoftInputOnFocus(false);

        // The field only displays what was typed: no focus (the system keyboard can still grab it on demand, see below)
        mSearchTextEditor.setFocusable(false);
        mSearchTextEditor.setFocusableInTouchMode(false);
        View.OnFocusChangeListener prevEditorFocus = mSearchTextEditor.getOnFocusChangeListener();
        mSearchTextEditor.setOnFocusChangeListener((v, focused) -> {
            if (prevEditorFocus != null) prevEditorFocus.onFocusChange(v, focused);
            if (!focused) {
                v.setFocusable(false);
                v.setFocusableInTouchMode(false);
            }
        });

        mOnScreenKeyboard = new OnScreenKeyboard(context);
        mOnScreenKeyboard.setListener(new OnScreenKeyboard.Listener() {
            @Override
            public void onChar(String ch) {
                mFromSuggestion = false;
                String text = mSearchTextEditor.getText() != null ? mSearchTextEditor.getText().toString() : "";
                mSearchTextEditor.setText(text + ch);
                mSearchTextEditor.setSelection(mSearchTextEditor.length());
            }

            @Override
            public void onBackspace() {
                mFromSuggestion = false;
                String text = mSearchTextEditor.getText() != null ? mSearchTextEditor.getText().toString() : "";
                if (!text.isEmpty()) {
                    mSearchTextEditor.setText(text.substring(0, text.length() - 1));
                    mSearchTextEditor.setSelection(mSearchTextEditor.length());
                }
            }

            @Override
            public void onClear() {
                mFromSuggestion = false;
                mSearchTextEditor.setText("");
            }

            @Override
            public void onSearch() {
                mFromSuggestion = false;
                submitQuery(getSearchBarText());
            }

            @Override
            public void onSystemKeyboard() {
                mSearchTextEditor.setFocusable(true);
                mSearchTextEditor.setFocusableInTouchMode(true);
                mSearchTextEditor.requestFocus();
                Helpers.showKeyboardAlt(context, mSearchTextEditor);
            }

            @Override
            public void onEdge(int direction, int row) {
                switch (direction) {
                    case OnScreenKeyboard.EDGE_UP:
                        mSpeechOrbView.requestFocus();
                        break;
                    case OnScreenKeyboard.EDGE_DOWN:
                        focusResultsGrid();
                        break;
                    case OnScreenKeyboard.EDGE_LEFT:
                        // back to the previously selected suggestion (first one if the text was edited)
                        if (mSuggestionList != null) mSuggestionList.focusLast();
                        break;
                }
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = (int) (400 * density * 0.75f);
        lp.topMargin = (int) (88 * density);
        searchFrame.addView(mOnScreenKeyboard, lp);

        mSuggestionList = new SuggestionList(context);
        mSuggestionList.setListener(new SuggestionList.Listener() {
            @Override
            public void onSuggestionClicked(Object item) {
                mFromSuggestion = true;
                if (mSuggestionListener != null) mSuggestionListener.onSuggestionClicked(item);
            }

            @Override
            public void onSuggestionLongClicked(Object item) {
                if (mSuggestionListener != null) mSuggestionListener.onSuggestionLongClicked(item);
            }

            @Override
            public void onEdge(int direction, int index) {
                switch (direction) {
                    case OnScreenKeyboard.EDGE_UP:
                        mSpeechOrbView.requestFocus();
                        break;
                    case OnScreenKeyboard.EDGE_DOWN:
                        focusResultsGrid();
                        break;
                    case SuggestionList.EDGE_RIGHT:
                        mOnScreenKeyboard.focusRow(index);
                        break;
                }
            }
        });

        // Search/settings buttons are not needed: the keyboard has its own SEARCH key
        mSearchOrbView.setVisibility(View.GONE);
        mSearchSettingsOrbView.setVisibility(View.GONE);

        // From the search bar go down to the keyboard (not to the results)
        int firstKey = mOnScreenKeyboard.getFirstKeyId();
        mSearchTextEditor.setNextFocusDownId(firstKey);
        mSearchOrbView.setNextFocusDownId(firstKey);
        mSearchSettingsOrbView.setNextFocusDownId(firstKey);
        mSpeechOrbView.setNextFocusDownId(firstKey);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                (int) (230 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.TOP | Gravity.START;
        slp.leftMargin = (int) (-11 * density); // icon lines up with the left edge of the results
        slp.topMargin = (int) (88 * density);
        searchFrame.addView(mSuggestionList, slp);

        watchResultsFocus(searchFrame);
    }

    /** TizenTube-like: while the results are focused only the search bar stays; this button goes back to typing */
    private void setupBackButton(FrameLayout searchFrame, float density) {
        android.widget.ImageView back = new android.widget.ImageView(searchFrame.getContext());
        back.setImageResource(com.liskovsoft.smartyoutubetv2.tv.R.drawable.ic_search_back);
        int pad = (int) (6 * density);
        back.setPadding(pad, pad, pad, pad);
        back.setFocusable(true);
        back.setClickable(true);
        back.setVisibility(View.GONE);
        back.setElevation(40 * density); // leanback cards are raised: stay above them
        back.setBackground(circle(0xFF272727));
        back.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(circle(focused ? 0xFFF1F1F1 : 0xFF272727));
            ((android.widget.ImageView) v).setColorFilter(focused ? 0xFF0F0F0F : 0xFFFFFFFF);
        });
        back.setOnClickListener(v -> {
            setResultsMode(false); // show the typing UI first, it cannot take focus while hidden
            focusOnSearchField();
        });
        // Pressing UP from the back button or LEFT from the results is handled by the default focus search
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (30 * density), (int) (30 * density));
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = (int) (-33 * density); // left of the first card, not over it
        lp.topMargin = (int) (226 * density);  // centered on the first thumbnail
        searchFrame.addView(back, lp);
        mBackButton = back;
    }

    private static android.graphics.drawable.Drawable circle(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private void installUpHandler() {
        // UP from the first result row goes back to typing: the suggestion that was used, otherwise CLEAR (never the mic)
        mRowsSupportFragment.getVerticalGridView().setOnKeyInterceptListener(event -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                    && mRowsSupportFragment.getSelectedPosition() <= 0) {
                setResultsMode(false);
                if (mFromSuggestion && mSuggestionList != null && mSuggestionList.size() > 0) {
                    mSuggestionList.focusLast();
                } else {
                    mOnScreenKeyboard.focusClear();
                }
                return true;
            }
            return false;
        });
    }

    private void watchResultsFocus(final FrameLayout searchFrame) {
        searchFrame.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            View results = mRowsSupportFragment != null ? mRowsSupportFragment.getView() : null;
            boolean inResults = false;
            for (View v = newFocus; v != null && results != null; ) {
                if (v == results) {
                    inResults = true;
                    break;
                }
                android.view.ViewParent parent = v.getParent();
                v = parent instanceof View ? (View) parent : null;
            }
            setResultsMode(inResults || newFocus == mBackButton);
        });
    }

    private void setResultsMode(boolean on) {
        mResultsMode = on;

        // Always re-apply: typing UI and back button must never be visible together
        if (mBackButton != null) {
            mBackButton.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        updateSearchBarVisibility();

        // Search bar lines up with the left edge of the results
        if (mSearchBar != null) {
        }

        // Results move up into the room that the keyboard has left
        if (mRowsSupportFragment != null && mRowsSupportFragment.getVerticalGridView() != null) {
            float density = getResources().getDisplayMetrics().density;
            int top = on ? (int) (150 * density)
                    : getResources().getDimensionPixelSize(R.dimen.lb_search_browse_rows_align_top);
            mRowsSupportFragment.getVerticalGridView().setWindowAlignmentOffset(top);
        }
    }

    private void resultsAvailable() {
        if ((mStatus & QUERY_COMPLETE) != 0) {
            focusOnResults();
        }
        updateSearchBarNextFocusId();
    }

    @Override
    public void onStart() {
        super.onStart();

        VerticalGridView list = mRowsSupportFragment.getVerticalGridView();
        int mContainerListAlignTop =
                getResources().getDimensionPixelSize(R.dimen.lb_search_browse_rows_align_top);
        installUpHandler();
        list.setItemAlignmentOffset(0);
        list.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
        list.setWindowAlignmentOffset(mContainerListAlignTop);
        list.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        list.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        // VerticalGridView should not be focusable (see b/26894680 for details).
        list.setFocusable(false);
        list.setFocusableInTouchMode(false);
        // MOD: disable scroll
        //list.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ITEM);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSpeechRecognitionCallback == null && null == mSpeechRecognizer) {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(
                    getContext());
            mSearchBar.setSpeechRecognizer(mSpeechRecognizer);
        }
        if (mPendingStartRecognition) {
            mPendingStartRecognition = false;
            // MOD: remove focus from other fields when doing voice search
            mSpeechOrbView.requestFocus();
            startRecognitionInt();
        } else {
            // Ensure search bar state consistency when using external recognizer
            mSearchBar.stopRecognition();
        }
    }

    @Override
    public void onPause() {
        releaseRecognizer();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        releaseAdapter();
        super.onDestroy();
    }

    /**
     * Returns RowsSupportFragment that shows result rows. RowsSupportFragment is initialized after
     * SearchSupportFragment.onCreateView().
     *
     * @return RowsSupportFragment that shows result rows.
     */
    public RowsSupportFragment getRowsSupportFragment() {
        return mRowsSupportFragment;
    }

    /**
     * NOTE: fix Service not registered: android.speech.SpeechRecognizer$Connection<br/>
     * Leanback version 1.0.0
     */
    private void releaseRecognizer() {
        try {
            if (null != mSpeechRecognizer) {
                mSearchBar.setSpeechRecognizer(null);
                mSpeechRecognizer.destroy();
                mSpeechRecognizer = null;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * NOTE: Activity should be resumed in order to recognize the voice<br/>
     * Because SpeechRecognizer added to SearchBar after resume. See: {@link SearchSupportFragment#onResume()}<br/>
     * Starts speech recognition.  Typical use case is that
     * activity receives onNewIntent() call when user clicks a MIC button.
     * Note that SearchSupportFragment automatically starts speech recognition
     * at first time created, there is no need to call startRecognition()
     * when fragment is created.
     */
    public void startRecognition() {
        if (isResumed()) {
            startRecognitionInt();
        } else {
            mPendingStartRecognition = true;
        }
    }

    private void startRecognitionInt() {
        try {
            mSearchBar.startRecognition();
        } catch (SecurityException e) {
            // Not allowed to bind to service Intent
            e.printStackTrace();
        }
    }

    /**
     * Sets the search provider that is responsible for returning results for the
     * search query.
     */
    public void setSearchResultProvider(SearchResultProvider searchResultProvider) {
        if (mProvider != searchResultProvider) {
            mProvider = searchResultProvider;
            onSetSearchResultProvider();
        }
    }

    /**
     * Sets an item selection listener for the results.
     *
     * @param listener The item selection listener to be invoked when an item in
     *        the search results is selected.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
    }

    /**
     * Sets an item clicked listener for the results.
     *
     * @param listener The item clicked listener to be invoked when an item in
     *        the search results is clicked.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        if (listener != mOnItemViewClickedListener) {
            mOnItemViewClickedListener = listener;
            if (mRowsSupportFragment != null) {
                mRowsSupportFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);
            }
        }
    }

    /**
     * Sets the title string to be be shown in an empty search bar. The title
     * may be placed in a call-to-action, such as "Search <i>title</i>" or
     * "Speak to search <i>title</i>".
     */
    public void setTitle(String title) {
        mTitle = title;
        if (null != mSearchBar) {
            mSearchBar.setTitle(title);
        }
    }

    /**
     * Returns the title set in the search bar.
     */
    public String getTitle() {
        if (null != mSearchBar) {
            return mSearchBar.getTitle();
        }
        return null;
    }

    /**
     * Sets the badge drawable that will be shown inside the search bar next to
     * the title.
     */
    public void setBadgeDrawable(Drawable drawable) {
        mBadgeDrawable = drawable;
        if (null != mSearchBar) {
            mSearchBar.setBadgeDrawable(drawable);
        }
    }

    /**
     * Returns the badge drawable in the search bar.
     */
    public Drawable getBadgeDrawable() {
        if (null != mSearchBar) {
            return mSearchBar.getBadgeDrawable();
        }
        return null;
    }

    /**
     * Sets background color of not-listening state search orb.
     *
     * @param colors SearchOrbView.Colors.
     */
    public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
        if (mSearchBar != null) {
            mSearchBar.setSearchAffordanceColors(colors);
        }
    }

    /**
     * Sets background color of listening state search orb.
     *
     * @param colors SearchOrbView.Colors.
     */
    public void setSearchAffordanceColorsInListening(SearchOrbView.Colors colors) {
        if (mSearchBar != null) {
            mSearchBar.setSearchAffordanceColorsInListening(colors);
        }
    }

    /**
     * Displays the completions shown by the IME. An application may provide
     * a list of query completions that the system will show in the IME.
     *
     * @param completions A list of completions to show in the IME. Setting to
     *        null or empty will clear the list.
     */
    public void displayCompletions(List<String> completions) {
        mSearchBar.displayCompletions(completions);
    }

    /**
     * Displays the completions shown by the IME. An application may provide
     * a list of query completions that the system will show in the IME.
     *
     * @param completions A list of completions to show in the IME. Setting to
     *        null or empty will clear the list.
     */
    public void displayCompletions(CompletionInfo[] completions) {
        mSearchBar.displayCompletions(completions);
    }

    /**
     * Sets this callback to have the fragment pass speech recognition requests
     * to the activity rather than using a SpeechRecognizer object.
     * @deprecated Launching voice recognition activity is no longer supported. App should declare
     *             android.permission.RECORD_AUDIO in AndroidManifest file.
     */
    @Deprecated
    public void setSpeechRecognitionCallback(SpeechRecognitionCallback callback) {
        mSpeechRecognitionCallback = callback;
        if (mSearchBar != null) {
            mSearchBar.setSpeechRecognitionCallback(mSpeechRecognitionCallback);
        }
        if (callback != null) {
            releaseRecognizer();
        }
    }

    /**
     * Sets the text of the search query and optionally submits the query. Either
     * {@link SearchResultProvider#onQueryTextChange onQueryTextChange} or
     * {@link SearchResultProvider#onQueryTextSubmit onQueryTextSubmit} will be
     * called on the provider if it is set.
     *
     * @param query The search query to set.
     * @param submit Whether to submit the query.
     */
    public void setSearchQuery(String query, boolean submit) {
        if (DEBUG) Log.v(TAG, "setSearchQuery " + query + " submit " + submit);
        if (query == null) {
            return;
        }
        mExternalQuery = new ExternalQuery(query, submit);
        applyExternalQuery();
        if (mAutoStartRecognition) {
            mAutoStartRecognition = false;
            mHandler.removeCallbacks(mStartRecognitionRunnable);
        }
    }

    /**
     * Sets the text of the search query based on the {@link RecognizerIntent#EXTRA_RESULTS} in
     * the given intent, and optionally submit the query.  If more than one result is present
     * in the results list, the first will be used.
     *
     * @param intent Intent received from a speech recognition service.
     * @param submit Whether to submit the query.
     */
    public void setSearchQuery(Intent intent, boolean submit) {
        ArrayList<String> matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches != null && matches.size() > 0) {
            setSearchQuery(matches.get(0), submit);
        }
    }

    /**
     * Returns an intent that can be used to request speech recognition.
     * Built from the base {@link RecognizerIntent#ACTION_RECOGNIZE_SPEECH} plus
     * extras:
     *
     * <ul>
     * <li>{@link RecognizerIntent#EXTRA_LANGUAGE_MODEL} set to
     * {@link RecognizerIntent#LANGUAGE_MODEL_FREE_FORM}</li>
     * <li>{@link RecognizerIntent#EXTRA_PARTIAL_RESULTS} set to true</li>
     * <li>{@link RecognizerIntent#EXTRA_PROMPT} set to the search bar hint text</li>
     * </ul>
     *
     * For handling the intent returned from the service, see
     * {@link #setSearchQuery(Intent, boolean)}.
     */
    public Intent getRecognizerIntent() {
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (mSearchBar != null && mSearchBar.getHint() != null) {
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, mSearchBar.getHint());
        }
        recognizerIntent.putExtra(EXTRA_LEANBACK_BADGE_PRESENT, mBadgeDrawable != null);
        return recognizerIntent;
    }

    public void onSearchSettingsClicked() {
        // NOP
    }

    public void focusOnSearchField() {
        // MOD: the field is display only, so start typing from the on-screen keyboard
        if (mOnScreenKeyboard != null) {
            mOnScreenKeyboard.focusRow(0);
        } else {
            mSearchTextEditor.requestFocus();
        }
    }

    protected String getSearchBarText() {
        return mSearchTextEditor.getText().toString();
    }

    /**
     * Select rows container after query submit<br/>
     * May helps with "hide kbd on back properly"
     */
    protected void focusOnResults() {
        if (mRowsSupportFragment == null || mRowsSupportFragment.getVerticalGridView() == null
                || mResultAdapter == null || mResultAdapter.size() == 0) {
            return;
        }

        // MOD: hide kbd on back properly
        boolean ok = mRowsSupportFragment.getVerticalGridView().requestFocus();
        if (ok) {
            mStatus &= ~RESULTS_CHANGED;
            mFocusRetries = 0;
        } else if (mFocusRetries < 20) {
            // Rows are not attached yet (fresh results): try again shortly, otherwise the focus stays on the mic
            mFocusRetries++;
            mHandler.removeCallbacks(mRetryFocusOnResults);
            mHandler.postDelayed(mRetryFocusOnResults, 150);
        }
    }

    protected void selectAllText() {
        if (mSearchTextEditor != null) {
            mSearchTextEditor.selectAll();
        }
    }

    protected void setKeyboardAutoShowEnabled(boolean enable) {
        // Show/Hide kbd on activity first launch
        if (getActivity() != null) {
            // NOTE: You should set 'unspecified' instead of 'visible' to fix 'keyboard popup in the player' bug.
            getActivity().getWindow().setSoftInputMode(enable ? WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        mIsKeyboardAutoShowEnabled = enable;
    }

    protected void setKeyboardFixEnabled(boolean enable) {
        mIsKeyboardFixEnabled = enable;
    }

    protected void setTypingCorrectionDisabled(boolean disable) {
        mIsTypingCorrectionDisabled = disable;
    }

    protected void showListening() {
        if (mSpeechOrbView != null) {
            mSpeechOrbView.showListening();
        }
    }

    protected void showNotListening() {
        if (mSpeechOrbView != null) {
            mSpeechOrbView.showNotListening();
        }

        //if (mSearchTextEditor != null) {
        //    // Hide "Speak to search" when not listening
        //    mSearchTextEditor.setHint("");
        //}
    }

    private void onSetSearchResultProvider() {
        mHandler.removeCallbacks(mSetSearchResultProvider);
        mHandler.post(mSetSearchResultProvider);
    }

    void retrieveResults(String searchQuery) {
        if (DEBUG) Log.v(TAG, "retrieveResults " + searchQuery);
        if (mProvider.onQueryTextChange(searchQuery)) {
            mStatus &= ~QUERY_COMPLETE;
        }
    }

    protected void submitQuery(String query) {
        if (query == null) {
            return;
        }

        queryComplete();
        if (null != mProvider) {
            mProvider.onQueryTextSubmit(query);
        }
    }

    void queryComplete() {
        if (DEBUG) Log.v(TAG, "queryComplete");
        mStatus |= QUERY_COMPLETE;
        focusOnResults();
    }

    void updateSearchBarVisibility() {
        int position = mRowsSupportFragment != null ? mRowsSupportFragment.getSelectedPosition() : -1;
        try {
            int vis = position <=0 || mResultAdapter == null
                    || mResultAdapter.size() == 0 ? View.VISIBLE : View.GONE;
            mSearchBar.setVisibility(vis);
            int typingVis = mResultsMode ? View.GONE : vis;
            if (mOnScreenKeyboard != null) {
                mOnScreenKeyboard.setVisibility(typingVis);
            }
            if (mSuggestionList != null) {
                mSuggestionList.setVisibility(typingVis);
            }
            if (mBackButton != null) {
                mBackButton.setVisibility(mResultsMode ? View.VISIBLE : View.GONE);
            }
        } catch (NullPointerException e) {
            // Fatal Exception: java.lang.NullPointerException
            // Attempt to invoke interface method 'void android.view.autofill.IAutoFillManager.addClient(android.view.autofill
            // .IAutoFillManagerClient, android.content.ComponentName, int, com.android.internal.os.IResultReceiver)' on a null object reference
            e.printStackTrace();
        }
    }

    void updateSearchBarNextFocusId() {
        if (mSearchBar == null || mResultAdapter == null) {
            return;
        }
        final int viewId = (mResultAdapter.size() == 0 || mRowsSupportFragment == null
                || mRowsSupportFragment.getVerticalGridView() == null)
                        ? 0 : mRowsSupportFragment.getVerticalGridView().getId();
        
        mSearchBar.setNextFocusDownId(viewId);
    }

    void updateFocus() {
        if (mResultAdapter != null && mResultAdapter.size() > 0
                && mRowsSupportFragment != null && mRowsSupportFragment.getAdapter() == mResultAdapter) {
            focusOnResults();
        } else {
            // MOD: Comment to fix moving focus to voice button when activity started
            //mSearchBar.requestFocus();
        }
    }

    void releaseAdapter() {
        if (mResultAdapter != null) {
            mResultAdapter.unregisterObserver(mAdapterObserver);
            mResultAdapter = null;
        }
    }

    void executePendingQuery() {
        if (null != mPendingQuery && null != mResultAdapter) {
            String query = mPendingQuery;
            mPendingQuery = null;
            retrieveResults(query);
        }
    }

    private void applyExternalQuery() {
        if (mExternalQuery == null || mSearchBar == null) {
            return;
        }
        mSearchBar.setSearchQuery(mExternalQuery.mQuery);
        if (mExternalQuery.mSubmit) {
            submitQuery(mExternalQuery.mQuery);
        }
        mExternalQuery = null;
    }

    private void readArguments(Bundle args) {
        if (null == args) {
            return;
        }
        if (args.containsKey(ARG_QUERY)) {
            setSearchQuery(args.getString(ARG_QUERY));
        }

        if (args.containsKey(ARG_TITLE)) {
            setTitle(args.getString(ARG_TITLE));
        }
    }

    private void setSearchQuery(String query) {
        mSearchBar.setSearchQuery(query);
    }

    protected void stopSpeechService() {
        // NOP
    }

    static class ExternalQuery {
        String mQuery;
        boolean mSubmit;

        ExternalQuery(String query, boolean submit) {
            mQuery = query;
            mSubmit = submit;
        }
    }
}
