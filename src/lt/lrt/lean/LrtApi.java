package lt.lrt.lean;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LrtApi {
    public static final String BASE_API = "https://www.lrt.lt/servisai/applicaster/video";
    private static final ExecutorService NET_POOL = Executors.newFixedThreadPool(4);
    private static final ExecutorService IMG_POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final Map<String, VideoItem.FeedPage> FEED_CACHE = new ConcurrentHashMap<>();

    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public interface Callback<T> {
        void onResult(T result, Exception error);
    }

    public static List<VideoItem.Section> getDefaultSections() {
        List<VideoItem.Section> list = new ArrayList<>();
        list.add(new VideoItem.Section(
                "live",
                "🔴  Tiesiogiai",
                "LRT Televizija, LRT Plius, LRT Lituanica ir radijas tiesiogiai",
                BASE_API + "/live/",
                true,
                false
        ));
        list.add(new VideoItem.Section(
                "auksinis_protas",
                "🧠  Auksinis protas",
                "Intelektualus žaidimas su Arūnu Valinsku ir Andriumi Tapinu",
                BASE_API + "/new/?category_id=114&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "tukstantmecio_vaikai",
                "🎓  Tūkstantmečio vaikai",
                "Žinių viktorina Lietuvos moksleiviams",
                BASE_API + "/new/?category_id=304&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "search",
                "🔍  Paieška",
                "Ieškokite laidų, filmų, koncertų ir įrašų LRT mediatekoje",
                "",
                false,
                true
        ));
        list.add(new VideoItem.Section(
                "popular",
                "🔥  Populiariausi",
                "Žiūrimiausi pastarųjų 7 dienų LRT mediatekos įrašai",
                BASE_API + "/new/?count=30&order=read_count&days=7",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "latest",
                "🆕  Naujausi įrašai",
                "Naujausi LRT mediatekos vaizdo įrašai",
                BASE_API + "/new/?count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "kas_ir_kodel",
                "⭐  Kas ir kodėl?",
                "Viktorina „Kas ir kodėl?“ – greiti klausimai ir atsakymai",
                BASE_API + "/new/?category_id=234&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "panorama",
                "⭐  Panorama",
                "Pagrindinė LRT vakaro žinių laida",
                BASE_API + "/new/?category_id=10265&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "dviracio_zinios",
                "⭐  Dviračio žinios",
                "Politinės satyros ir humoro laida",
                BASE_API + "/new/?category_id=614&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "stonkus",
                "⭐  Stonkus tiesiogiai",
                "Pokalbių ir pramogų šou su Mantu Stonkumi",
                BASE_API + "/new/?category_id=28599&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "lrt_forumas",
                "⭐  LRT forumas",
                "Politinių ir visuomeninių diskusijų laida",
                BASE_API + "/new/?category_id=320&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "myliu_lietuva",
                "⭐  Myliu Lietuvą",
                "Azartiškas žaidimas apie Lietuvą",
                BASE_API + "/new/?category_id=18872&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "beatos_virtuve",
                "⭐  Beatos virtuvė",
                "Kulinarijos ir gero skonio laida",
                BASE_API + "/new/?category_id=652&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "stilius",
                "⭐  Stilius",
                "Gyvenimo būdo ir asmenybių istorijos",
                BASE_API + "/new/?category_id=479&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "gustavo",
                "⭐  Gustavo enciklopedija",
                "Pažintinė laida vaikams ir visai šeimai",
                BASE_API + "/new/?category_id=198&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "aktualijos",
                "📰  Aktualijos",
                "Aktualijų, informacinės ir diskusijų laidos",
                BASE_API + "/new/?term=/aktualijos&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "zinios",
                "🗞️  Žinios",
                "LRT žinių ir dienos temų įrašai",
                BASE_API + "/new/?term=/aktualijos/zinios&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "pramogos",
                "🎭  Pramogos",
                "Pramoginės laidos, šou ir žaidimai",
                BASE_API + "/new/?term=/pramogos&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "viktorinos",
                "🎲  Viktorinos",
                "Visi LRT žaidimai ir viktorinos",
                BASE_API + "/new/?term=/pramogos/zaidimai-viktorinos&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "sportas",
                "🏀  Sportas",
                "Sporto rungtynių įrašai ir apžvalgos",
                BASE_API + "/new/?term=/sportas&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "krepsinis",
                "🏆  Krepšinis / Eurolyga",
                "Krepšinio ir Eurolygos rungtynių įrašai",
                BASE_API + "/new/?term=/sportas/varzybu-irasai/krepsinis&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "kultura",
                "🎨  Menas ir kultūra",
                "Kultūros laidos, teatras ir spektakliai",
                BASE_API + "/new/?term=/menas-ir-kultura&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "gyvenimas",
                "🌿  Gyvenimas",
                "Kelionės, gamta, sveikata, maistas ir gyvenimo būdas",
                BASE_API + "/new/?term=/gyvenimas&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "mokslas",
                "🔬  Mokslas ir pažinimas",
                "Istorija, mokslas, filosofija ir pažinimas",
                BASE_API + "/new/?term=/mokslas-ir-pazinimas&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "muzika",
                "🎵  Muzika ir koncertai",
                "Koncertai ir muzikinės laidos",
                BASE_API + "/new/?term=/muzika&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "vaikams",
                "🧸  Vaikams",
                "Animaciniai serialai, filmai ir laidos vaikams",
                BASE_API + "/new/?term=/vaikams&count=30&order=naujausi",
                false,
                false
        ));
        list.add(new VideoItem.Section(
                "tinklalaides",
                "🎙️  Tinklalaidės",
                "LRT tinklalaidės ir pokalbiai",
                BASE_API + "/new/?term=/tinklalaides&count=30&order=naujausi",
                false,
                false
        ));
        return list;
    }

    public static String buildSearchUrl(String query) {
        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8");
            return BASE_API + "/new/?q=" + encoded + "&count=30";
        } catch (Exception e) {
            return BASE_API + "/new/?q=" + query.trim().replace(" ", "%20") + "&count=30";
        }
    }

    public static String buildCategoryUrl(String categoryId) {
        return BASE_API + "/new/?category_id=" + categoryId + "&count=30&order=naujausi";
    }

    public static void clearFeedCache(String url) {
        if (url != null) {
            FEED_CACHE.remove(url);
        }
    }

    public static void prefetchUrl(final String url) {
        if (url == null || url.isEmpty() || FEED_CACHE.containsKey(url)) return;
        NET_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoItem.FeedPage page = fetchFeedSync(url);
                    if (page != null && page.items != null && !page.items.isEmpty()) {
                        FEED_CACHE.put(url, page);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void fetchFeed(final String url, final boolean useCache, final Callback<VideoItem.FeedPage> callback) {
        if (useCache && FEED_CACHE.containsKey(url)) {
            final VideoItem.FeedPage cached = FEED_CACHE.get(url);
            MAIN_HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(cached, null);
                }
            });
            return;
        }

        NET_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final VideoItem.FeedPage page = fetchFeedSync(url);
                    if (useCache && page.items != null && !page.items.isEmpty()) {
                        FEED_CACHE.put(url, page);
                    }
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(page, null);
                        }
                    });
                } catch (final Exception e) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(null, e);
                        }
                    });
                }
            }
        });
    }

    private static VideoItem.FeedPage fetchFeedSync(String rawUrl) throws Exception {
        String safeUrl = rawUrl.replace(" ", "%20");
        String jsonStr = httpGetString(safeUrl);
        JSONObject root = new JSONObject(jsonStr);
        JSONArray entries = root.optJSONArray("entry");
        List<VideoItem> items = new ArrayList<>();
        boolean isLiveFeed = safeUrl.contains("/video/live");

        if (entries != null) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject obj = entries.optJSONObject(i);
                if (obj == null) continue;

                VideoItem item = new VideoItem();
                item.id = obj.optString("id", String.valueOf(i));
                item.title = obj.optString("title", "").trim();
                item.description = obj.optString("description", "").trim();
                item.categoryId = obj.optString("category_id", "");
                item.categoryTitle = obj.optString("category_title", "").trim();

                JSONObject content = obj.optJSONObject("content");
                if (content != null) {
                    item.streamUrl = content.optString("src", "");
                }

                JSONObject ext = obj.optJSONObject("extensions");
                if (ext != null) {
                    item.channelCode = ext.optString("channel", "");
                    item.channelTitle = ext.optString("channel_title", "");
                    item.broadcastTime = ext.optString("time", "");
                    item.dateText = ext.optString("date_msg", ext.optString("date", ""));
                    int durSec = 0;
                    try {
                        durSec = Integer.parseInt(ext.optString("duration", "0"));
                    } catch (Exception ignored) {
                    }
                    item.durationSeconds = durSec;
                    if (durSec > 0) {
                        item.durationText = formatDuration(durSec);
                    }
                    if ((item.categoryTitle == null || item.categoryTitle.isEmpty()) && !ext.optString("section", "").isEmpty()) {
                        item.categoryTitle = ext.optString("section", "");
                    }
                }

                item.isLive = isLiveFeed || (item.channelCode != null && !item.channelCode.isEmpty());
                if (item.isLive) {
                    if (item.channelTitle != null && !item.channelTitle.isEmpty()) {
                        item.categoryTitle = "TIESIOGIAI • " + item.channelTitle;
                    } else {
                        item.categoryTitle = "TIESIOGIAI";
                    }
                    if (item.broadcastTime != null && !item.broadcastTime.isEmpty()) {
                        item.durationText = item.broadcastTime;
                    }
                }

                JSONArray mediaGroups = obj.optJSONArray("media_group");
                if (mediaGroups != null) {
                    for (int m = 0; m < mediaGroups.length(); m++) {
                        JSONObject mg = mediaGroups.optJSONObject(m);
                        if (mg == null) continue;
                        JSONArray mediaItems = mg.optJSONArray("media_item");
                        if (mediaItems == null) continue;
                        for (int k = 0; k < mediaItems.length(); k++) {
                            JSONObject mi = mediaItems.optJSONObject(k);
                            if (mi == null) continue;
                            String key = mi.optString("key", "");
                            String src = mi.optString("src", "");
                            if (!src.isEmpty()) {
                                if ("thumbnail-16x9".equals(key) || item.thumbnailUrl == null || item.thumbnailUrl.isEmpty()) {
                                    item.thumbnailUrl = src;
                                }
                            }
                        }
                    }
                }

                if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                    items.add(item);
                }
            }
        }

        String nextUrl = root.optString("next", "");
        if (nextUrl != null && nextUrl.isEmpty()) {
            nextUrl = null;
        }
        return new VideoItem.FeedPage(items, nextUrl);
    }

    public static void resolvePlayableUrl(final VideoItem item, final Callback<String> callback) {
        NET_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String resolved = resolvePlayableUrlSync(item);
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(resolved, null);
                        }
                    });
                } catch (final Exception e) {
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(item.streamUrl, e);
                        }
                    });
                }
            }
        });
    }

    private static String resolvePlayableUrlSync(VideoItem item) throws Exception {
        if (item.isLive && item.channelCode != null && !item.channelCode.isEmpty()) {
            try {
                String liveApi = "https://www.lrt.lt/servisai/stream_url/live/get_live_url.php?channel=" + item.channelCode;
                String json = httpGetString(liveApi);
                JSONObject root = new JSONObject(json);
                JSONObject resp = root.optJSONObject("response");
                if (resp != null) {
                    JSONObject data = resp.optJSONObject("data");
                    if (data != null) {
                        String content = data.optString("content", "").trim();
                        if (content.startsWith("http")) {
                            return content;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String rawUrl = item.streamUrl;
        if (rawUrl == null || rawUrl.isEmpty()) {
            throw new IllegalArgumentException("Tuščias srauto adresas");
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(rawUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; PhilipsTV) AppleWebKit/537.36");
            conn.connect();
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null && loc.startsWith("http")) {
                    return loc;
                }
            }
            String finalUrl = conn.getURL().toString();
            if (finalUrl.contains(".m3u8")) {
                return finalUrl;
            }
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String firstLine = reader.readLine();
            reader.close();
            if (firstLine != null && firstLine.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(firstLine);
                JSONObject resp = obj.optJSONObject("response");
                if (resp != null && resp.optJSONObject("data") != null) {
                    String c = resp.optJSONObject("data").optString("content", "");
                    if (c.startsWith("http")) return c;
                }
            }
            return finalUrl;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void loadThumbnail(final String url, final ImageView imageView) {
        imageView.setTag(url);
        if (url == null || url.isEmpty()) {
            imageView.setImageBitmap(null);
            return;
        }
        Bitmap cached = BITMAP_CACHE.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        imageView.setImageBitmap(null);
        IMG_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = httpGetBytes(url);
                    if (bytes == null || bytes.length == 0) return;

                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

                    int sampleSize = 1;
                    int reqW = 360;
                    int reqH = 202;
                    if (bounds.outHeight > reqH || bounds.outWidth > reqW) {
                        int halfH = bounds.outHeight / 2;
                        int halfW = bounds.outWidth / 2;
                        while ((halfH / sampleSize) >= reqH && (halfW / sampleSize) >= reqW) {
                            sampleSize *= 2;
                        }
                    }

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    final Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    if (bmp != null) {
                        BITMAP_CACHE.put(url, bmp);
                        MAIN_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                if (url.equals(imageView.getTag())) {
                                    imageView.setImageBitmap(bmp);
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static String httpGetString(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "LRTGreitasTV/1.0 (Android TV)");
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16384);
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            reader.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32768);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toByteArray();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) return "";
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
