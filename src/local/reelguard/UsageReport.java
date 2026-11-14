package local.reelguard;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

final class UsageReport {
    static boolean granted(Context context) {
        AppOpsManager ops = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.getPackageName())
            == AppOpsManager.MODE_ALLOWED;
    }
    static JSONObject snapshot(Context context) {
        JSONObject report = new JSONObject();
        Records.put(report,"permission_granted",granted(context));
        Records.put(report,"source","Android UsageStatsManager");
        Records.put(report,"scope","Instagram app usage, not Reels-only usage. OS daily buckets may extend beyond requested range; values follow Android aggregation and retention.");
        if (!granted(context)) return report;
        long end=System.currentTimeMillis();
        long start=LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Records.put(report,"requested_start",Instant.ofEpochMilli(start).toString());
        Records.put(report,"requested_end",Instant.ofEpochMilli(end).toString());
        JSONArray rows = new JSONArray();
        try {
            UsageStatsManager manager=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> entries=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,end);
            if (entries != null) for (UsageStats stats:entries) {
                if (!"com.instagram.android".equals(stats.getPackageName())) continue;
                JSONObject row=new JSONObject();
                Records.put(row,"bucket_start",Instant.ofEpochMilli(stats.getFirstTimeStamp()).toString());
                Records.put(row,"bucket_end",Instant.ofEpochMilli(stats.getLastTimeStamp()).toString());
                Records.put(row,"foreground_ms",stats.getTotalTimeInForeground());
                Records.put(row,"last_used_at",stats.getLastTimeUsed() == 0 ? JSONObject.NULL : Instant.ofEpochMilli(stats.getLastTimeUsed()).toString());
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    Records.put(row,"visible_ms",stats.getTotalTimeVisible());
                    Records.put(row,"foreground_service_ms",stats.getTotalTimeForegroundServiceUsed());
                }
                rows.put(row);
            }
            Records.put(report,"daily_buckets",rows);
        } catch (RuntimeException ex) { Records.put(report,"error",ex.getClass().getSimpleName()); }
        return report;
    }
}
