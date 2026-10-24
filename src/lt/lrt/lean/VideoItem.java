package lt.lrt.lean;

import java.util.ArrayList;
import java.util.List;

public class VideoItem {
    public String id;
    public String title;
    public String description;
    public String categoryId;
    public String categoryTitle;
    public String streamUrl;
    public String thumbnailUrl;
    public String dateText;
    public String durationText;
    public int durationSeconds;
    public boolean isLive;
    public String channelCode;
    public String channelTitle;
    public String broadcastTime;

    public static class FeedPage {
        public List<VideoItem> items;
        public String nextUrl;

        public FeedPage(List<VideoItem> items, String nextUrl) {
            this.items = items;
            this.nextUrl = nextUrl;
        }
    }

    public static class Section {
        public final String key;
        public final String title;
        public final String subtitle;
        public final String url;
        public final boolean isSearch;
        public final boolean isLive;
        public final boolean isProgram;
        public final boolean isFavorites;

        public Section(String key, String title, String subtitle, String url, boolean isLive, boolean isSearch) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.isLive = isLive;
            this.isSearch = isSearch;
            this.isProgram = "program".equals(key);
            this.isFavorites = "favorites".equals(key);
        }
    }

    // ================= TV PROGRAM (EPG) =================
    public static class ProgramEntry {
        public String title;
        public String description;
        public String timeStart;
        public String timeEnd;
        public long startMs;
        public long endMs;
        public int dayIndex;
        public String recordId;
        public String recordUrl;
        public String channelCode;
        public String channelTitle;
        public boolean isRadio;
        public String showKey;

        public boolean isNow(long now) {
            return now >= startMs && now < endMs;
        }

        public boolean isPast(long now) {
            return now >= endMs;
        }

        // Only LRT mediateka / radioteka records are playable; Epika links need a subscription
        public boolean hasRecording() {
            return recordUrl != null && recordUrl.startsWith("https://www.lrt.lt/") && recordUrl.contains("/irasas/");
        }
    }

    public static class ProgramChannel {
        public final String code;
        public final String title;
        public final List<ProgramEntry> entries = new ArrayList<>();

        public ProgramChannel(String code, String title) {
            this.code = code;
            this.title = title;
        }
    }

    public static class ProgramGuide {
        public final List<String> dayKeys = new ArrayList<>();
        public final List<Long> dayStarts = new ArrayList<>();
        public final List<ProgramChannel> channels = new ArrayList<>();
        public long fetchedAtMs;

        public ProgramChannel channel(String code) {
            for (ProgramChannel ch : channels) {
                if (ch.code.equals(code)) return ch;
            }
            return null;
        }

        public ProgramEntry nextAfter(String code, long now) {
            ProgramChannel ch = channel(code);
            if (ch == null) return null;
            for (ProgramEntry e : ch.entries) {
                if (e.startMs > now) return e;
            }
            return null;
        }
    }
}
