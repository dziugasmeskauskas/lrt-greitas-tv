package lt.lrt.lean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class PlayerActivity extends Activity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DESC = "desc";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_IS_LIVE = "is_live";
    public static final String EXTRA_CHANNEL_CODE = "channel_code";

    private VideoView videoView;
    private LinearLayout osdContainer;
    private TextView badgeView;
    private TextView titleView;
    private TextView descView;
    private TextView timeView;
    private TextView stateHintView;
    private ProgressBar progressBar;
    private ProgressBar loadingSpinner;

    private String itemId;
    private String itemTitle;
    private String itemDesc;
    private String itemCategory;
    private String streamUrl;
    private String channelCode;
    private boolean isLive;

    private boolean isPrepared = false;
    private int pendingSeekDeltaMs = 0;
    private int seekBasePositionMs = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private final Runnable hideOsdRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isPlaying() && pendingSeekDeltaMs == 0) {
                osdContainer.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable commitSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPrepared || isLive || pendingSeekDeltaMs == 0) return;
            int duration = videoView.getDuration();
            if (duration <= 0) {
                pendingSeekDeltaMs = 0;
                seekBasePositionMs = -1;
                return;
            }
            int base = seekBasePositionMs >= 0 ? seekBasePositionMs : videoView.getCurrentPosition();
            int target = Math.max(0, Math.min(duration - 2000, base + pendingSeekDeltaMs));
            pendingSeekDeltaMs = 0;
            seekBasePositionMs = -1;
            loadingSpinner.setVisibility(View.VISIBLE);
            videoView.seekTo(target);
            if (!videoView.isPlaying()) {
                videoView.start();
            }
            stateHintView.setText("▶ Grojama");
            scheduleHideOsd(3500);
        }
    };

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("lrt_lean_prefs", Context.MODE_PRIVATE);

        Intent intent = getIntent();
        itemId = intent.getStringExtra(EXTRA_ID);
        itemTitle = intent.getStringExtra(EXTRA_TITLE);
        itemDesc = intent.getStringExtra(EXTRA_DESC);
        itemCategory = intent.getStringExtra(EXTRA_CATEGORY);
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL);
        channelCode = intent.getStringExtra(EXTRA_CHANNEL_CODE);
        isLive = intent.getBooleanExtra(EXTRA_IS_LIVE, false);

        buildUi();
        startStreamResolution();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        FrameLayout.LayoutParams vvParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        videoView.setLayoutParams(vvParams);
        videoView.setFocusable(false);
        root.addView(videoView);

        loadingSpinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinParams = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER);
        loadingSpinner.setLayoutParams(spinParams);
        root.addView(loadingSpinner);

        osdContainer = new LinearLayout(this);
        osdContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable osdBg = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2060D1A, 0xCC0A1528, 0x000A1528}
        );
        osdContainer.setBackground(osdBg);
        osdContainer.setPadding(dp(48), dp(48), dp(48), dp(32));
        FrameLayout.LayoutParams osdParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        osdContainer.setLayoutParams(osdParams);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        badgeView = new TextView(this);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        badgeView.setTypeface(Typeface.DEFAULT_BOLD);
        badgeView.setTextColor(Color.WHITE);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(6));
        badgeBg.setColor(isLive ? 0xFFE53935 : 0xFF0077C8);
        badgeView.setBackground(badgeBg);
        badgeView.setPadding(dp(10), dp(4), dp(10), dp(4));
        badgeView.setText(itemCategory != null && !itemCategory.isEmpty() ? itemCategory.toUpperCase() : (isLive ? "TIESIOGIAI" : "LRT MEDIATEKA"));
        topRow.addView(badgeView);

        stateHintView = new TextView(this);
        stateHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        stateHintView.setTypeface(Typeface.DEFAULT_BOLD);
        stateHintView.setTextColor(0xFF00E5FF);
        stateHintView.setPadding(dp(16), 0, 0, 0);
        stateHintView.setText("Kraunama transliacija...");
        topRow.addView(stateHintView);

        osdContainer.addView(topRow);

        titleView = new TextView(this);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.WHITE);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(0, dp(8), 0, dp(4));
        titleView.setText(itemTitle != null ? itemTitle : "");
        osdContainer.addView(titleView);

        descView = new TextView(this);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        descView.setTextColor(0xFFB0C4DE);
        descView.setMaxLines(2);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        if (itemDesc != null && !itemDesc.isEmpty()) {
            descView.setText(itemDesc);
        } else {
            descView.setVisibility(View.GONE);
        }
        osdContainer.addView(descView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        pbParams.topMargin = dp(12);
        pbParams.bottomMargin = dp(8);
        progressBar.setLayoutParams(pbParams);
        if (isLive) {
            progressBar.setProgress(1000);
        }
        osdContainer.addView(progressBar);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        timeView = new TextView(this);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setTextColor(Color.WHITE);
        timeView.setText(isLive ? "🔴 TIESIOGINĖ TRANSLIACIJA" : "00:00 / --:--");
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        timeView.setLayoutParams(timeParams);
        bottomRow.addView(timeView);

        TextView controlsHint = new TextView(this);
        controlsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        controlsHint.setTextColor(0xFF90A4AE);
        controlsHint.setText(isLive
                ? "OK: Pauzė/Groti  •  ATGAL: Grįžti į sąrašą"
                : "OK: Pauzė/Groti  •  ◀/▶: ±15s  •  ▲/▼: ±1 min.  •  0: Nuo pradžių  •  ATGAL: Grįžti");
        bottomRow.addView(controlsHint);

        osdContainer.addView(bottomRow);
        root.addView(osdContainer);

        setContentView(root);
    }

    private void startStreamResolution() {
        VideoItem item = new VideoItem();
        item.id = itemId;
        item.streamUrl = streamUrl;
        item.isLive = isLive;
        item.channelCode = channelCode;

        LrtApi.resolvePlayableUrl(item, new LrtApi.Callback<String>() {
            @Override
            public void onResult(String playableUrl, Exception error) {
                if (isFinishing() || isDestroyed()) return;
                playResolvedUrl(playableUrl != null ? playableUrl : streamUrl);
            }
        });
    }

    private void playResolvedUrl(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Nepavyko gauti vaizdo srauto", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isPrepared = true;
                loadingSpinner.setVisibility(View.GONE);

                mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                            loadingSpinner.setVisibility(View.VISIBLE);
                        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                                || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            loadingSpinner.setVisibility(View.GONE);
                        }
                        return false;
                    }
                });

                mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mediaPlayer) {
                        loadingSpinner.setVisibility(View.GONE);
                        updateProgressUi();
                    }
                });

                if (!isLive && itemId != null) {
                    int savedMs = prefs.getInt("pos_" + itemId, 0);
                    int durMs = videoView.getDuration();
                    if (savedMs > 15000 && (durMs <= 0 || savedMs < durMs - 30000)) {
                        videoView.seekTo(savedMs);
                        stateHintView.setText("▶ Tęsiama nuo " + LrtApi.formatDuration(savedMs / 1000));
                    } else {
                        stateHintView.setText("▶ Grojama");
                    }
                } else {
                    stateHintView.setText("▶ Grojama tiesiogiai");
                }

                videoView.start();
                updateProgressUi();
                scheduleHideOsd(4000);
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (!isLive && itemId != null) {
                    prefs.edit().remove("pos_" + itemId).apply();
                }
                finish();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                loadingSpinner.setVisibility(View.GONE);
                stateHintView.setText("⚠️ Klaida kraunant srautą (" + what + ")");
                return true;
            }
        });

        videoView.setVideoURI(Uri.parse(url));
    }

    private int lastSavedSec = 0;

    private void updateProgressUi() {
        if (!isPrepared || videoView == null) return;
        if (isLive) return;

        int dur = videoView.getDuration();
        int pos = videoView.getCurrentPosition();
        if (pendingSeekDeltaMs != 0) {
            int base = seekBasePositionMs >= 0 ? seekBasePositionMs : pos;
            pos = Math.max(0, Math.min(dur > 0 ? dur : Integer.MAX_VALUE, base + pendingSeekDeltaMs));
        }

        if (dur > 0) {
            int progress = (int) ((pos * 1000L) / dur);
            progressBar.setProgress(Math.max(0, Math.min(1000, progress)));
            timeView.setText(LrtApi.formatDuration(pos / 1000) + " / " + LrtApi.formatDuration(dur / 1000));
        } else {
            timeView.setText(LrtApi.formatDuration(pos / 1000));
        }

        int currentSec = pos / 1000;
        if (pendingSeekDeltaMs == 0 && itemId != null && currentSec > 15 && Math.abs(currentSec - lastSavedSec) >= 5) {
            lastSavedSec = currentSec;
            prefs.edit().putInt("pos_" + itemId, pos).putInt("dur_" + itemId, dur).apply();
        }
    }

    private void showOsd() {
        osdContainer.setVisibility(View.VISIBLE);
        updateProgressUi();
        scheduleHideOsd(4000);
    }

    private void scheduleHideOsd(int delayMs) {
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, delayMs);
    }

    private void queueSeek(int deltaMs) {
        if (!isPrepared || isLive) {
            showOsd();
            return;
        }
        if (seekBasePositionMs < 0) {
            seekBasePositionMs = videoView.getCurrentPosition();
        }
        pendingSeekDeltaMs += deltaMs;
        int totalShiftSec = pendingSeekDeltaMs / 1000;
        String sign = totalShiftSec >= 0 ? "+" : "";
        stateHintView.setText((totalShiftSec >= 0 ? "⏩ " : "⏪ ") + sign + totalShiftSec + " s");
        osdContainer.setVisibility(View.VISIBLE);
        updateProgressUi();

        handler.removeCallbacks(hideOsdRunnable);
        handler.removeCallbacks(commitSeekRunnable);
        handler.postDelayed(commitSeekRunnable, 420);
    }

    private void togglePlayPause() {
        if (!isPrepared) return;
        if (videoView.isPlaying()) {
            videoView.pause();
            saveCurrentPosition();
            stateHintView.setText("⏸ Pauzė");
            osdContainer.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideOsdRunnable);
        } else {
            videoView.start();
            stateHintView.setText("▶ Grojama");
            showOsd();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlayPause();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (isPrepared && !videoView.isPlaying()) {
                    videoView.start();
                    stateHintView.setText("▶ Grojama");
                    showOsd();
                }
                return true;

            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (isPrepared && videoView.isPlaying()) {
                    videoView.pause();
                    saveCurrentPosition();
                    stateHintView.setText("⏸ Pauzė");
                    osdContainer.setVisibility(View.VISIBLE);
                    handler.removeCallbacks(hideOsdRunnable);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                queueSeek(-15000);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                queueSeek(15000);
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (osdContainer.getVisibility() == View.VISIBLE && !isLive) {
                    queueSeek(60000);
                } else {
                    showOsd();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (osdContainer.getVisibility() == View.VISIBLE && !isLive) {
                    queueSeek(-60000);
                } else {
                    showOsd();
                }
                return true;

            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (isPrepared && !isLive) {
                    pendingSeekDeltaMs = 0;
                    seekBasePositionMs = -1;
                    videoView.seekTo(0);
                    stateHintView.setText("⏮ Nuo pradžių");
                    showOsd();
                }
                return true;

            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_MENU:
                if (osdContainer.getVisibility() == View.VISIBLE) {
                    osdContainer.setVisibility(View.GONE);
                } else {
                    showOsd();
                }
                return true;

            case KeyEvent.KEYCODE_BACK:
                saveCurrentPosition();
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveCurrentPosition() {
        if (!isPrepared || isLive || itemId == null || videoView == null) return;
        int pos = videoView.getCurrentPosition();
        int dur = videoView.getDuration();
        if (pos > 15000 && (dur <= 0 || pos < dur - 30000)) {
            prefs.edit().putInt("pos_" + itemId, pos).putInt("dur_" + itemId, dur).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(progressTicker);
    }

    @Override
    protected void onPause() {
        saveCurrentPosition();
        handler.removeCallbacks(progressTicker);
        handler.removeCallbacks(hideOsdRunnable);
        handler.removeCallbacks(commitSeekRunnable);
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
