import local.reelguard.quota.DayQuota;
import java.util.*;
public class DayQuotaTest {
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
    static String id(int n) { return String.format("%064x", n); }
    public static void main(String[] args) {
        DayQuota q = new DayQuota(20261114, new HashSet<String>());
        for (int i=0;i<20;i++) { q=q.admit(id(i)); check(q!=null,"admit "+i); }
        check(q.admit(id(20))==null,"21st denied");
        check(q.admit(id(0))==q,"duplicate free");
        DayQuota restored=new DayQuota(q.day,q.ids());
        check(restored.admit(id(20))==null,"restart retains limit");
        check(restored.advance(20261113).admit(id(20))==null,"rollback retains limit");
        check(restored.advance(20261115).admit(id(20)).ids().size()==1,"new day resets");
        check(restored.admit(null)==null && restored.admit("")==null,"invalid denied");
        Set<String> copy=q.ids();copy.clear();check(q.ids().size()==20,"immutable state");
        Set<String> bad=q.ids();bad.add(id(20));
        try { new DayQuota(q.day,bad);throw new AssertionError("oversized state"); } catch(IllegalArgumentException expected) {}
        try { new DayQuota(q.day,Collections.singleton("bad"));throw new AssertionError("malformed state"); } catch(IllegalArgumentException expected) {}
        System.out.println("PASS daily quota: cap, duplicates, restore, day rollover, rollback, invalid state");
    }
}
