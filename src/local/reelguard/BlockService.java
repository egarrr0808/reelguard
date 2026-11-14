package local.reelguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayDeque;

public final class BlockService extends AccessibilityService {
    static volatile boolean connected;
    private static final String INSTAGRAM = "com.instagram.android";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private View cover;
    private View tabCover;
    private Rect tabCoverBounds;
    private Rect coverBounds;
    private Records records;
    private SharedPreferences prefs;
    private boolean foreground;
    private long lastViewerSeen;
    private long lastFeedExit;
    private String coverMode = "";
    private WindowManager.LayoutParams coverParams;
    private boolean forwarding;
    private int coverGeneration;
    private String lastMatch = "";
    private final Runnable inspectTask = this::inspect;
    private final Runnable heartbeat = new Runnable() {
        public void run() { if (foreground) { inspect(); handler.postDelayed(this,15000); } }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (p,k) -> {
        clearAll(); handler.removeCallbacks(inspectTask); handler.post(inspectTask);
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        public void onReceive(Context c,Intent i) { leave("screen_off"); }
    };
    @Override protected void onServiceConnected() {
        connected = true; records = Records.get(this);
        prefs = getSharedPreferences("settings",0);
        prefs.registerOnSharedPreferenceChangeListener(changed);
        windows = (WindowManager)getSystemService(WINDOW_SERVICE);
        registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        records.event("service_connected", "Accessibility connected");
        handler.post(inspectTask);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (records == null) return;
        // Window changes from all packages are needed to remove overlays outside Instagram.
        // Inspect window transitions immediately; waiting for debounce left a re-entry gap.
        handler.removeCallbacks(inspectTask);
        inspect();
    }
    private void inspect() {
        if (records == null) return;
        android.os.PowerManager power = (android.os.PowerManager)getSystemService(POWER_SERVICE);
        android.app.KeyguardManager lock = (android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || lock.isKeyguardLocked()) { leave("screen_locked"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (foreground && cover != null && SystemClock.elapsedRealtime()-lastViewerSeen < 700) {
                retainViewerDuringTransition();
            } else leave("window_unavailable");
            return;
        }
        AccessibilityNodeInfo homeAction=null;
        try {
            if (!INSTAGRAM.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                leave("left_instagram"); return;
            }
            if (!foreground) {
                foreground = true; records.begin();
                handler.removeCallbacks(heartbeat); handler.postDelayed(heartbeat,15000);
            }
            records.touch();
            if (!prefs.getBoolean("enabled",true)) { clearAll(); return; }
            Rect screen = new Rect(); root.getBoundsInScreen(screen);
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(AccessibilityNodeInfo.obtain(root));
            Rect tab = null, viewerBounds = null; String viewer = null; int visited = 0;
            boolean reelsSelected=false, otherTabSelected=false;
            org.json.JSONArray diagnosticNodes = new org.json.JSONArray();
            try {
                while (!queue.isEmpty() && visited++ < 1500) {
                    AccessibilityNodeInfo node = queue.removeFirst();
                    try {
                        if (!node.isVisibleToUser()) continue;
                        String id = node.getViewIdResourceName();
                        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                        if (id != null && diagnosticNodes.length() < 40
                                && (id.contains("clips") || id.contains("reel") || id.contains("direct") || id.contains("message") || id.endsWith("_tab"))) {
                            org.json.JSONObject diagnostic = new org.json.JSONObject();
                            Records.put(diagnostic,"id",id);
                            Records.put(diagnostic,"bounds",bounds.flattenToString());
                            Records.put(diagnostic,"clickable",node.isClickable());
                            Records.put(diagnostic,"selected",node.isSelected());
                            diagnosticNodes.put(diagnostic);
                        }
                        if (ReelRules.isViewerId(id) && bounds.width() > 0 && bounds.height() > 0
                                && Rect.intersects(bounds,screen)) {
                            viewer = id; viewerBounds = new Rect(bounds);
                            viewerBounds.intersect(screen);
                            // During opening animation the viewer can be smaller than half-screen.
                            // Protect the app area immediately, then resize to the settled viewer.
                            if (bounds.width() <= screen.width()/2 || bounds.height() <= screen.height()/2) {
                                viewerBounds.set(screen);
                            }
                        }
                        if (ReelRules.isHomeTabId(id) && node.isClickable()) {
                            if (homeAction != null) homeAction.recycle();
                            homeAction=AccessibilityNodeInfo.obtain(node);
                        }
                        if (id != null && id.endsWith("_tab") && node.isSelected()
                                && !id.endsWith("/clips_tab") && !id.endsWith("/reels_tab")) otherTabSelected=true;
                        CharSequence description = node.getContentDescription();
                        if (ReelRules.isTab(id,description == null ? null : description.toString(),node.isClickable(),
                                bounds.left,bounds.top,bounds.right,bounds.bottom,
                                screen.left,screen.top,screen.right,screen.bottom)) {
                            tab = bounds; reelsSelected=node.isSelected();
                        }
                        for (int i=0;i<node.getChildCount();i++) {
                            AccessibilityNodeInfo child=node.getChild(i);
                            if (child != null) queue.addLast(child);
                        }
                    } finally { node.recycle(); }
                }
            } finally { while (!queue.isEmpty()) queue.removeFirst().recycle(); }
            records.screenDiagnostics(diagnosticNodes,visited,screen.flattenToString());
            // Keep the tab blocked independently, including while a shared Reel is open.
            if (tab != null) showTabCover(tab); else clearTabCover();
            if (viewer != null && viewerBounds != null) {
                lastViewerSeen=SystemClock.elapsedRealtime();
                showCover(viewerBounds,"viewer");
                if (ReelRules.isFeedViewer(true,reelsSelected,homeAction != null)
                        && lastViewerSeen-lastFeedExit > 1000) {
                    lastFeedExit=lastViewerSeen;
                    boolean accepted=homeAction.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    records.event("feed_exit",accepted ? "Native Reels feed redirected to Home" : "Home navigation rejected");
                }
                if (!lastMatch.equals("viewer")) records.event("viewer_detections",viewer);
                lastMatch="viewer";
            } else if (tab != null) {
                if (otherTabSelected) clearCover(); else retainViewerDuringTransition();
                if (!lastMatch.equals("tab")) records.event("detection_status","Reels tab detected");
                lastMatch="tab";
            } else {
                retainViewerDuringTransition();
                if (!lastMatch.equals("none")) records.event("detection_status","No supported Reels nodes on this screen");
                lastMatch="none";
            }
        } catch (RuntimeException ex) {
            clearAll(); records.event("inspection_error",ex.getClass().getSimpleName());
        } finally { if (homeAction != null) homeAction.recycle(); root.recycle(); }
    }
    private void retainViewerDuringTransition() {
        if (cover != null && SystemClock.elapsedRealtime()-lastViewerSeen < 700) {
            handler.removeCallbacks(inspectTask);handler.postDelayed(inspectTask,40);
        } else clearCover();
    }
    private void showCover(Rect bounds, String mode) {
        if (cover != null && mode.equals(coverMode)) {
            if (!bounds.equals(coverBounds)) {
                coverParams.x=bounds.left;coverParams.y=bounds.top;
                coverParams.width=bounds.width();coverParams.height=bounds.height();
                try { windows.updateViewLayout(cover,coverParams);coverBounds=new Rect(bounds); }
                catch (RuntimeException ex) { records.event("overlay_error",ex.getClass().getSimpleName());clearCover(); }
            }
            return;
        }
        clearCover();
        View view = new View(this);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        view.setContentDescription(mode.equals("viewer") ? "Reel playback allowed; scrolling blocked" : "Reels tab disabled by ReelGuard");
        {
            TapGate taps=new TapGate(ViewConfiguration.get(this).getScaledTouchSlop());
            view.setOnTouchListener((v,event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        taps.begin(event.getRawX(),event.getRawY()); break;
                    case MotionEvent.ACTION_MOVE:
                        // Historical samples matter when motion events are batched.
                        for (int i=0;i<event.getHistorySize();i++) {
                            float dx=event.getRawX()-event.getX();
                            float dy=event.getRawY()-event.getY();
                            taps.move(event.getHistoricalX(i)+dx,event.getHistoricalY(i)+dy);
                        }
                        taps.move(event.getRawX(),event.getRawY()); break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_CANCEL:
                        taps.cancel(); break;
                    case MotionEvent.ACTION_UP:
                        if (taps.finish(event.getRawX(),event.getRawY())) {
                            forwardTap(event.getRawX(),event.getRawY());
                        } else records.event("blocked_swipes","Reel drag or multitouch consumed");
                        break;
                    default: break;
                }
                return true;
            });
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(bounds.width(),bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT; params.x=bounds.left; params.y=bounds.top;
        if (android.os.Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        try {
            windows.addView(view,params); cover=view; coverBounds=new Rect(bounds);
            coverMode=mode; coverParams=params;
        } catch (RuntimeException ex) { records.event("overlay_error",ex.getClass().getSimpleName()); }
    }
    private void showTabCover(Rect bounds) {
        if (tabCover != null && bounds.equals(tabCoverBounds)) return;
        clearTabCover();
        View view=new View(this);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        view.setContentDescription("Reels tab disabled by ReelGuard");
        view.setOnClickListener(v -> {
            records.event("blocked_tab_taps","Reels tab tapped");
            Toast.makeText(this,"Reels feed blocked. Individual shared Reels remain playable.",Toast.LENGTH_SHORT).show();
        });
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(bounds.width(),bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT; params.x=bounds.left; params.y=bounds.top;
        if (android.os.Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        try { windows.addView(view,params);tabCover=view;tabCoverBounds=new Rect(bounds); }
        catch (RuntimeException ex) { records.event("overlay_error",ex.getClass().getSimpleName()); }
    }
    private void clearTabCover() {
        if (tabCover != null) {
            try { windows.removeViewImmediate(tabCover); } catch (RuntimeException ignored) {}
            tabCover=null;tabCoverBounds=null;
        }
    }
    private void clearAll() { clearCover();clearTabCover(); }
    private boolean instagramActive() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if (root == null) return false;
        try { return INSTAGRAM.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()); }
        finally { root.recycle(); }
    }
    private void forwardTap(float x,float y) {
        if (forwarding || cover == null || !coverMode.equals("viewer") || !instagramActive()) return;
        final View target=cover;
        final int generation=coverGeneration;
        forwarding=true;
        coverParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { windows.updateViewLayout(target,coverParams); }
        catch (RuntimeException ex) { clearCover(); return; }
        // Let InputDispatcher see the pass-through flag before injecting a stationary tap.
        handler.postDelayed(() -> {
            if (generation != coverGeneration || cover != target) return;
            if (!instagramActive() || !prefs.getBoolean("enabled",true)) { clearCover(); return; }
            Path path=new Path(); path.moveTo(x,y);
            GestureDescription gesture=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path,0,40)).build();
            GestureResultCallback callback=new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    records.event("forwarded_taps","Single viewer tap forwarded");
                    restoreTouch(target,generation);
                }
                @Override public void onCancelled(GestureDescription g) { restoreTouch(target,generation); }
            };
            try {
                if (!dispatchGesture(gesture,callback,handler)) {
                    records.event("tap_forward_error","Gesture capability unavailable; re-enable accessibility after update");
                    restoreTouch(target,generation);
                }
            } catch (RuntimeException ex) {
                records.event("tap_forward_error",ex.getClass().getSimpleName());
                restoreTouch(target,generation);
            }
        },32);
        handler.postDelayed(() -> restoreTouch(target,generation),500);
    }
    private void restoreTouch(View target,int generation) {
        if (generation != coverGeneration || target != cover || !forwarding) return;
        forwarding=false;
        coverParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { windows.updateViewLayout(target,coverParams); }
        catch (RuntimeException ex) { clearCover(); }
        handler.removeCallbacks(inspectTask); handler.post(inspectTask);
    }
    private void clearCover() {
        coverGeneration++; forwarding=false;
        if (cover != null) {
            try { windows.removeViewImmediate(cover); } catch (RuntimeException ignored) {}
            cover=null; coverBounds=null;
        }
        coverMode=""; coverParams=null;
    }
    private void leave(String reason) {
        clearAll(); handler.removeCallbacks(heartbeat);
        if (foreground) { records.end(reason); foreground=false; }
        lastMatch="";
    }
    @Override public void onInterrupt() { if (records != null) leave("service_interrupted"); }
    @Override public void onDestroy() {
        connected=false; handler.removeCallbacksAndMessages(null);
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(changed);
        try { unregisterReceiver(screenOff); } catch (IllegalArgumentException ignored) {}
        if (records != null) { leave("service_destroyed"); records.save(); }
        super.onDestroy();
    }
}
