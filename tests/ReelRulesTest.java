import local.reelguard.ReelRules;
public class ReelRulesTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    static boolean tab(String id, String label, boolean clickable, int l, int t, int r, int b) {
        return ReelRules.isTab(id,label,clickable,l,t,r,b,0,0,1080,2400);
    }
    public static void main(String[] args) {
        check(tab("com.instagram.android:id/clips_tab", "", true,432,2200,648,2340));
        check(tab("", "Reels", true,432,2200,648,2340));
        check(tab("reels_tab", "動画", true,432,2200,648,2340));
        check(!tab("home_tab", "Home", true,0,2200,216,2340));
        check(!tab("clips_tab", "Reels", false,432,2200,648,2340));
        check(!tab("clips_tab", "Reels", true,432,500,648,640));
        check(!tab("clips_tab", "Reels", true,432,2200,432,2340));
        check(!tab("clips_tab", "Reels", true,-1,2200,648,2340));
        check(!tab("clips_tab", "Reels", true,0,2200,1080,2340));
        check(!tab("clips_tab", "Reels", true,432,2200,648,2500));
        check(ReelRules.isTab("clips_tab",null,true,532,2300,748,2440,100,100,1180,2500));
        check(!ReelRules.isTab("clips_tab",null,true,532,2300,748,2440,100,100,1180,100));
        check(ReelRules.isViewerId("com.instagram.android:id/clips_viewer_view_pager"));
        check(ReelRules.isViewerId("clips_viewer_video_layout"));
        check(!ReelRules.isViewerId("video_container"));
        check(!ReelRules.isViewerId("Reels"));
        check(!ReelRules.isViewerId(null));
        check(!tab(null,null,true,432,2200,648,2340));
        check(ReelRules.isFeedViewer(true,true,true));
        check(!ReelRules.isFeedViewer(true,false,false));
        check(!ReelRules.isFeedViewer(true,false,true));
        check(!ReelRules.isFeedViewer(false,true,true));
        check(!ReelRules.isFeedViewer(true,true,false));
        check(ReelRules.isHomeTabId("com.instagram.android:id/feed_tab"));
        check(!ReelRules.isHomeTabId("com.instagram.android:id/direct_tab"));
        System.out.println("PASS " + checks + " detection checks");
    }
}
