package local.reelguard;

public final class ReelRules {
    private ReelRules() {}
    private static String suffix(String id) {
        if (id == null) return "";
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }
    public static boolean isHomeTabId(String id) { return suffix(id).equals("feed_tab"); }
    public static boolean isFeedViewer(boolean viewerPresent, boolean reelsTabSelected, boolean homeTabPresent) {
        return viewerPresent && reelsTabSelected && homeTabPresent;
    }
    public static boolean isViewerId(String id) {
        String s = suffix(id);
        return s.equals("clips_viewer_view_pager") || s.equals("clips_viewer_video_layout")
            || s.equals("clips_viewer_root");
    }
    public static boolean isTab(String id, String description, boolean clickable,
            int l, int t, int r, int b, int wl, int wt, int wr, int wb) {
        if (!clickable || wr <= wl || wb <= wt || r <= l || b <= t
                || l < wl || t < wt || r > wr || b > wb) return false;
        long width = (long) wr - wl, height = (long) wb - wt;
        if ((long) (r - l) * 3 > width || (long) (b - t) * 4 > height
                || t < wt + height * 3 / 4) return false;
        String s = suffix(id);
        return s.equals("clips_tab") || s.equals("reels_tab")
            || "Reels".equalsIgnoreCase(description == null ? "" : description.trim());
    }
}
