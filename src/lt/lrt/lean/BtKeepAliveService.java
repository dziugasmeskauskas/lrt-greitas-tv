package lt.lrt.lean;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Keeps the Bluetooth A2DP stream in AVDTP_START (Active Mode) while the TV screen is ON,
 * preventing the Edifier R1700BT speaker's 30.0-second idle timer from triggering Bluetooth
 * Sniff Mode (which Philips/MediaTek's DTV01561912 patch in libbluetooth.so fails to exit).
 */
public class BtKeepAliveService extends Service {

    private static final String CHANNEL_ID = "bt_keepalive_chan";
    private static final int NOTIF_ID = 1001;
    private static final int SAMPLE_RATE = 44100;

    private volatile boolean running = false;
    private volatile boolean screenOn = true;
    private Thread audioThread;
    private BroadcastReceiver stateReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundWithMinimalNotification();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            screenOn = pm.isInteractive();
        }

        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOn = true;
                    ensureAudioThread();
                    ensureSpeakerConnected(false);
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenOn = false;
                } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (state == BluetoothProfile.STATE_CONNECTED && screenOn) {
                        ensureAudioThread();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(stateReceiver, filter);

        ensureAudioThread();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithMinimalNotification();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            screenOn = pm.isInteractive();
        }
        ensureAudioThread();
        boolean bounce = intent != null && intent.getBooleanExtra("bounce_bt", false);
        ensureSpeakerConnected(bounce);
        return START_STICKY;
    }

    private void startForegroundWithMinimalNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID,
                            "Bluetooth Audio KeepAlive",
                            NotificationManager.IMPORTANCE_MIN
                    );
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
                Notification notif = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("LRT Greitas BT Audio Guard")
                        .setContentText("Bluetooth A2DP Active Mode Keep-Alive")
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .build();
                startForeground(NOTIF_ID, notif);
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void ensureAudioThread() {
        if (running && audioThread != null && audioThread.isAlive()) {
            return;
        }
        running = true;
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runKeepAliveLoop();
            }
        }, "BtA2dpKeepAlive");
        audioThread.setPriority(Thread.NORM_PRIORITY);
        audioThread.start();
    }

    private void runKeepAliveLoop() {
        int minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufBytes = Math.max(minBuf, 8820 * 4); // ~200ms buffer

        // 100ms chunk of stereo 16-bit PCM (4410 frames = 8820 shorts)
        // Uses +/- 1 LSB (-90.3 dBFS, 100% inaudible) so HAL/DSP never gates it as digital zero
        short[] pcm = new short[8820];
        for (int i = 0; i < pcm.length; i += 4) {
            pcm[i] = 1;
            pcm[i + 1] = -1;
            pcm[i + 2] = -1;
            pcm[i + 3] = 1;
        }

        AudioTrack track = null;
        while (running) {
            try {
                if (!screenOn) {
                    if (track != null) {
                        try {
                            track.pause();
                            track.flush();
                            track.release();
                        } catch (Exception ignored) {
                        }
                        track = null;
                    }
                    Thread.sleep(1000);
                    continue;
                }

                if (track == null) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    AudioFormat fmt = new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build();
                    track = new AudioTrack(
                            attrs,
                            fmt,
                            bufBytes,
                            AudioTrack.MODE_STREAM,
                            0
                    );
                    track.setVolume(0.01f);
                    track.play();
                }

                int written = track.write(pcm, 0, pcm.length);
                if (written < 0) {
                    try {
                        track.release();
                    } catch (Exception ignored) {
                    }
                    track = null;
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                try {
                    if (track != null) track.release();
                } catch (Exception ignored) {
                }
                track = null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureSpeakerConnected(final boolean bounceIfConnected) {
        try {
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return;
            final Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return;

            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, final BluetoothProfile proxy) {
                    try {
                        List<BluetoothDevice> connected = proxy.getConnectedDevices();
                        final Method connectMethod = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                        final Method disconnectMethod = proxy.getClass().getMethod("disconnect", BluetoothDevice.class);

                        if (connected != null && !connected.isEmpty()) {
                            if (bounceIfConnected) {
                                for (final BluetoothDevice dev : connected) {
                                    disconnectMethod.invoke(proxy, dev);
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                connectMethod.invoke(proxy, dev);
                                            } catch (Exception ignored) {
                                            } finally {
                                                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy);
                                            }
                                        }
                                    }, 2000);
                                }
                                return;
                            }
                        } else {
                            for (BluetoothDevice dev : bonded) {
                                connectMethod.invoke(proxy, dev);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy);
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (stateReceiver != null) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
