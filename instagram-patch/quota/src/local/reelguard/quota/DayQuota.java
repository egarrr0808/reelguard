package local.reelguard.quota;

import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable, bounded daily admission state. Persist a candidate before exposing its post. */
public final class DayQuota {
    public static final int LIMIT = 20;
    public final long day;
    private final LinkedHashSet<String> ids;

    public DayQuota(long day, Set<String> ids) {
        if (day < 1 || ids == null || ids.size() > LIMIT) throw new IllegalArgumentException("quota state");
        this.day = day;
        this.ids = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id == null || !id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("post hash");
            this.ids.add(id);
        }
    }
    public boolean contains(String id) { return ids.contains(id); }
    public Set<String> ids() { return new LinkedHashSet<String>(ids); }
    public DayQuota advance(long today) {
        if (today < 1) throw new IllegalArgumentException("day");
        return today > day ? new DayQuota(today, new LinkedHashSet<String>()) : this;
    }
    public DayQuota admit(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) return null;
        if (ids.contains(id)) return this;
        if (ids.size() == LIMIT) return null;
        LinkedHashSet<String> next = new LinkedHashSet<String>(ids);
        next.add(id);
        return new DayQuota(day, next);
    }
}
