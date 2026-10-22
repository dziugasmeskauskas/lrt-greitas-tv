package lt.lrt.lean;

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

        public Section(String key, String title, String subtitle, String url, boolean isLive, boolean isSearch) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.isLive = isLive;
            this.isSearch = isSearch;
        }
    }
}
