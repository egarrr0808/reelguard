package local.reelguard.quota;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Version-pinned native Home model guard. No UI, network, account or message access. */
public final class HomeQuota {
    private static SharedPreferences prefs;
    private static DayQuota state;
    private static boolean failed;
    private static int lastRowCap = -1;
    private static Method mediaMethod;
    private static Method idMethod;
    private HomeQuota() {}

    public static synchronized void attach(Context context) {
        if (prefs != null || failed) return;
        try {
            prefs = context.getApplicationContext().getSharedPreferences("reelguard_home_quota_v1", 0);
            long day = prefs.getLong("day", today());
            Set<String> ids = prefs.getStringSet("post_hashes", Collections.<String>emptySet());
            state = new DayQuota(day, ids);
            mediaMethod = Class.forName("X.00PG").getMethod("A07");
            idMethod = Class.forName("com.instagram.feed.media.Media").getMethod("getId");
            Log.i("ReelGuardQuota", "attached limit=20 admitted=" + state.ids().size());
        } catch (Exception e) {
            failed = true;
            Log.e("ReelGuardQuota", "initialization failed; Home closed");
        }
    }
    private static long today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH);
    }
    private static boolean home(Object model) {
        return model != null && "X.06qX".equals(model.getClass().getName());
    }
    public static synchronized boolean allow(Object model, Object item) {
        if (!home(model)) return true;
        if (failed || prefs == null || state == null || item == null) return false;
        try {
            // Unknown content containers are omitted: nested recommendations cannot bypass the quota.
            Object media = mediaMethod.invoke(item);
            if (media == null) return false;
            String id = (String) idMethod.invoke(media);
            if (id == null || id.length() == 0 || id.length() > 512) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8"));
            String hashed = hex(digest);
            DayQuota candidate = state.advance(today()).admit(hashed);
            if (candidate == null) return false;
            if (candidate != state) {
                if (!prefs.edit().putInt("limit", DayQuota.LIMIT).putLong("day", candidate.day)
                        .putStringSet("post_hashes", candidate.ids()).commit()) {
                    failed = true;
                    Log.e("ReelGuardQuota", "persistence failed; Home closed");
                    return false;
                }
                state = candidate;
                Log.i("ReelGuardQuota", "admitted=" + state.ids().size() + "/20 day=" + state.day);
            }
            return true;
        } catch (Exception e) {
            Log.e("ReelGuardQuota", "unrecognized Home item denied");
            return false;
        }
    }
    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i=0;i<bytes.length;i++) {out[i*2]=alphabet[(bytes[i]&255)>>>4];out[i*2+1]=alphabet[bytes[i]&15];}
        return new String(out);
    }
    /** Final row-count boundary: refreshed native caches must not expose unadmitted media. */
    public static synchronized int capRows(Object adapter, int original) {
        if (adapter == null || !"X.06qW".equals(adapter.getClass().getName())) return original;
        if (failed || state == null || prefs == null) return 0;
        try {
            Class<?> base = Class.forName("X.07BI");
            boolean flattened = base.getField("A08").getBoolean(adapter);
            Object container = base.getField(flattened ? "A04" : "A00").get(adapter);
            if (container == null) return 0;
            List<?> rows = (List<?>) container.getClass().getField(flattened ? "A02" : "A06").get(container);
            int count = Math.min(original, rows.size());
            for (int i=0;i<count;i++) {
                Object row = rows.get(i);
                Object item = row.getClass().getField(flattened ? "A05" : "A02").get(row);
                if (item == null) continue;
                Object media = null;
                if (idMethod.getDeclaringClass().isInstance(item)) media = item;
                else if (mediaMethod.getDeclaringClass().isInstance(item)) media = mediaMethod.invoke(item);
                if (media == null) continue;
                String id = (String) idMethod.invoke(media);
                String hash = id == null ? "" : hex(MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8")));
                if (!state.contains(hash)) {
                    if (lastRowCap != i) Log.i("ReelGuardQuota", "native row cap=" + i + " total=" + original);
                    lastRowCap=i;
                    return i;
                }
            }
            return count;
        } catch (Exception e) {
            if (lastRowCap != -2) Log.e("ReelGuardQuota", "native row cache unrecognized; Home closed");
            lastRowCap=-2;
            return 0;
        }
    }
    public static synchronized List<?> filter(Object model, List<?> items) {
        if (!home(model) || items == null || items.isEmpty()) return items;
        ArrayList<Object> result = new ArrayList<Object>();
        for (Object item : items) if (allow(model, item)) result.add(item);
        return Collections.unmodifiableList(result);
    }
}
