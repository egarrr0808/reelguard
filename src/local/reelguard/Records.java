package local.reelguard;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.TimeZone;

final class Records {
    private static Records instance;
    static synchronized Records get(Context context) {
        if (instance == null) instance = new Records(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final AtomicFile file;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject data;
    private JSONObject active;
    private long startElapsed;
    private boolean pending;
    String error = "";
    private boolean storageBlocked;
    private final Runnable flush = () -> { pending = false; save(); };

    private Records(Context context) {
        this.context = context;
        file = new AtomicFile(new File(context.getFilesDir(), "reelguard.json"));
        try {
            data = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            data = new JSONObject();
            if (file.getBaseFile().exists()) {
                try {
                    java.nio.file.Files.copy(file.getBaseFile().toPath(),
                        new File(context.getFilesDir(), "reelguard-corrupt-"+System.currentTimeMillis()+".json").toPath());
                    error = "Unreadable history preserved in private storage; starting new history.";
                } catch (Exception backupError) {
                    storageBlocked = true;
                    error = "History unreadable; saving disabled to preserve original. Export current diagnostics.";
                }
            }
        }
        if (!data.has("installed_at")) put(data,"installed_at",now());
        if (!data.has("sessions")) put(data,"sessions",new JSONArray());
        if (!data.has("events")) put(data,"events",new JSONArray());
        JSONObject previous = data.optJSONObject("active_session");
        if (previous != null) {
            put(previous,"end_reason","process_interrupted");
            put(previous,"ended_at",JSONObject.NULL);
            append("sessions",previous,500);
            data.remove("active_session");
        }
    }
    static String now() { return Instant.now().toString(); }
    static void put(JSONObject obj,String key,Object value) {
        try { obj.put(key,value); } catch (org.json.JSONException ex) { throw new IllegalArgumentException(ex); }
    }
    private void append(String key,JSONObject obj,int limit) {
        JSONArray array = data.optJSONArray(key);
        if (array == null) array = new JSONArray();
        array.put(obj);
        while (array.length() > limit) array.remove(0);
        put(data,key,array);
    }
    void screenDiagnostics(org.json.JSONArray nodes, int visited, String bounds) {
        JSONObject snapshot = new JSONObject();
        put(snapshot,"observed_at",now());
        put(snapshot,"window_bounds",bounds);
        put(snapshot,"visited_nodes",visited);
        put(snapshot,"structural_nodes",nodes);
        put(data,"last_instagram_screen",snapshot);
        schedule();
    }
    void begin() {
        if (active != null) { touch(); return; }
        active = new JSONObject();
        startElapsed = SystemClock.elapsedRealtime();
        put(active,"started_at",now());
        put(active,"timezone",TimeZone.getDefault().getID());
        put(active,"account_label",context.getSharedPreferences("settings",0).getString("username",""));
        put(active,"account_label_verified",false);
        put(active,"blocked_tab_taps",0);
        put(active,"viewer_detections",0);
        touch();
        save();
    }
    void touch() {
        if (active == null) return;
        put(active,"last_observed_at",now());
        put(active,"observed_duration_ms",SystemClock.elapsedRealtime()-startElapsed);
        put(data,"active_session",active);
        schedule();
    }
    void end(String reason) {
        if (active == null) return;
        touch();
        put(active,"ended_at",now());
        put(active,"end_reason",reason);
        append("sessions",active,500);
        active = null;
        data.remove("active_session");
        save();
    }
    void event(String kind, String detail) {
        JSONObject event = new JSONObject();
        put(event,"at",now()); put(event,"kind",kind); put(event,"detail",detail);
        append("events",event,300);
        if (kind.equals("blocked_tab_taps") || kind.equals("viewer_detections") || kind.equals("blocked_swipes") || kind.equals("forwarded_taps")) {
            put(data,"total_"+kind,data.optLong("total_"+kind)+1);
            if (active != null) put(active,kind,active.optLong(kind)+1);
        }
        touch(); schedule();
    }
    private void schedule() {
        if (!pending) { pending = true; handler.postDelayed(flush,15000); }
    }
    void save() {
        handler.removeCallbacks(flush); pending = false;
        if (storageBlocked) return;
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out); error = "";
        } catch (Exception ex) {
            if (out != null) file.failWrite(out);
            error = "History write failed: " + ex.getClass().getSimpleName();
        }
    }
    String export() throws Exception {
        // Do not extend a session from an export screen; the service owns observations.
        save();
        JSONObject root = new JSONObject(data.toString());
        put(root,"schema_version",1); put(root,"exported_at",now());
        put(root,"timezone",TimeZone.getDefault().getID());
        JSONObject settings = new JSONObject();
        android.content.SharedPreferences prefs = context.getSharedPreferences("settings",0);
        put(settings,"blocking_enabled",prefs.getBoolean("enabled",true));
        put(settings,"tab_appearance","unchanged_transparent_touch_blocker");
        put(settings,"viewer_mode","allow_playback_block_swipes_forward_single_taps");
        put(settings,"account_username",prefs.getString("username",""));
        put(settings,"account_username_source","manually_entered_unverified");
        put(settings,"target_package","com.instagram.android");
        put(root,"config",settings);
        JSONObject device = new JSONObject();
        put(device,"manufacturer",Build.MANUFACTURER); put(device,"model",Build.MODEL);
        put(device,"android_release",Build.VERSION.RELEASE); put(device,"sdk",Build.VERSION.SDK_INT);
        put(device,"locale",java.util.Locale.getDefault().toLanguageTag());
        put(device,"reelguard_version","0.3.1");
        try { put(device,"instagram_version",context.getPackageManager().getPackageInfo("com.instagram.android",0).versionName); }
        catch (Exception ex) { put(device,"instagram_version",JSONObject.NULL); }
        put(root,"environment",device);
        put(root,"android_usage_stats",UsageReport.snapshot(context));
        put(root,"service_connected",BlockService.connected);
        put(root,"storage_error",error);
        put(root,"collection_limits","Only observed sessions while accessibility runs. At most 500 sessions and 300 diagnostic events. Account label is manual, not proof of identity. No complete Instagram history, messages, passwords, tokens, screen text, or follower data. Blocked taps count overlay taps only. Viewer detections count viewer entries, not watched reels. Blocked swipes count consumed drags or multitouch. Forwarded taps count successful stationary gesture dispatches. Long presses and drag seeking are unsupported. Durations use monotonic time; process interruption can lose up to 15 seconds. Current account switches are not detected.");
        return root.toString(2);
    }
}
