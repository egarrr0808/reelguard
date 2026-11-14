package local.reelguard;

/** Recognizes stationary single-pointer taps without allowing drag-then-return bypasses. */
public final class TapGate {
    private final float slopSquared;
    private float startX, startY;
    private boolean active, rejected;
    public TapGate(float slop) {
        if (Float.isNaN(slop) || Float.isInfinite(slop) || slop < 0) throw new IllegalArgumentException("slop");
        slopSquared=slop*slop;
    }
    public void begin(float x,float y) {
        active=true; rejected=false; startX=x; startY=y;
        move(x,y);
    }
    public void move(float x,float y) {
        if (!active) return;
        float dx=x-startX,dy=y-startY;
        if (Float.isNaN(dx) || Float.isNaN(dy) || dx*dx+dy*dy > slopSquared) rejected=true;
    }
    public void cancel() { active=false; rejected=true; }
    public boolean finish(float x,float y) {
        move(x,y);
        boolean tap=active && !rejected;
        active=false;
        return tap;
    }
}
