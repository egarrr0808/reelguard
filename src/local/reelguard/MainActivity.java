package local.reelguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;
    private EditText username;
    private String exportSnapshot;
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() { updateStatus(); refreshHandler.postDelayed(this,1000); }
    };
    private static final int EXPORT=71;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("settings",0);
        if (saved != null) exportSnapshot=saved.getString("export_snapshot");
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(24*getResources().getDisplayMetrics().density);
        page.setPadding(padding,padding*2,padding,padding); scroll.addView(page);
        text(page,"ReelGuard",28);
        text(page,"Instagram scroll blocker · local prototype",16);
        status=text(page,"",16);
        text(page,"Disables the Reels tab without painting over the menu. Individual Reels opened from messages can play. Detected native Reels feed opens return to Home. A transparent touch layer blocks drags in supported viewers and forwards single taps. Instagram's menu spacing remains unchanged. Detection depends on Instagram's interface.",16);
        Switch enabled=new Switch(this); enabled.setText("Block Reels scrolling");
        enabled.setChecked(prefs.getBoolean("enabled",true)); page.addView(enabled);
        enabled.setOnCheckedChangeListener((button,value)->prefs.edit().putBoolean("enabled",value).apply());
        text(page,"Your Instagram username (optional manual label, not verified)",14);
        username=new EditText(this); username.setSingleLine(true);
        username.setHint("@username"); username.setText(prefs.getString("username","")); page.addView(username);
        button(page,"Save settings",()->saveSettings());
        button(page,"Enable accessibility blocker",()->new AlertDialog.Builder(this)
            .setTitle("Accessibility access")
            .setMessage("ReelGuard reads Instagram interface identifiers and element positions to disable the Reels tab and block viewer swipes. Single taps are replayed to Instagram so playback controls remain usable. It records local session timestamps, durations and blocked attempts. It does not record messages, passwords or screen text. Accessibility access is powerful. Continue only if you agree. Disable the service in Android Settings at any time.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Agree; open Settings",(dialog,which)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .show());
        button(page,"Allow Android usage statistics (optional)",()->new AlertDialog.Builder(this)
            .setTitle("Usage Access")
            .setMessage("With your permission, exported reports include Android's Instagram foreground and visible time for the last seven calendar days. This measures the entire Instagram app, not only Reels. ReelGuard exports only Instagram totals. No data leaves the phone unless you export it.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Open Settings",(dialog,which)->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
            .show());
        button(page,"Export config + diagnostics JSON",()->{
            if (!saveSettings()) return;
            try {
                exportSnapshot=Records.get(this).export();
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"reelguard-"+java.time.LocalDate.now()+".json");
                startActivityForResult(intent,EXPORT);
            } catch (Exception ex) { message("Export failed: "+ex.getClass().getSimpleName()); }
        });
        text(page,"Export contains settings, your manual account label, Android/app versions, observed Instagram sessions, timestamps, durations, blocked attempts, and optional Android usage totals. Keeps 500 sessions and 300 events. No account login or network access. Account switches are not detected. It cannot retrieve full Instagram account history.",14);
        setContentView(scroll);
    }
    private TextView text(LinearLayout page,String value,int size) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setPadding(0,12,0,12);page.addView(view);return view;
    }
    private void button(LinearLayout page,String label,Runnable action) {
        Button button=new Button(this);button.setText(label);page.addView(button);button.setOnClickListener(v->action.run());
    }
    private boolean saveSettings() {
        prefs.edit().putString("username",username.getText().toString().trim()).apply();
        return true;
    }
    private void message(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refresh); refreshHandler.post(refresh);
    }
    private void updateStatus() {
        if (status == null) return;
        boolean active=BlockService.connected && prefs.getBoolean("enabled",true);
        status.setTextColor(active ? Color.rgb(20,110,40) : Color.rgb(180,35,25));
        status.setText((active ? "BLOCKER RUNNING — detection still depends on Instagram layout" : "BLOCKING INACTIVE")
            +"\nAccessibility: "+(BlockService.connected ? "connected" : "OFF — enable ReelGuard in Android Accessibility Settings")
            +"\nAndroid usage access: "+(UsageReport.granted(this) ? "allowed" : "off")
            +"\n"+Records.get(this).error);
    }
    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refresh); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putString("export_snapshot",exportSnapshot);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != EXPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        if (exportSnapshot == null) { message("Export interrupted. Try again.");return; }
        try (OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) {
            if (out == null) throw new java.io.IOException("No output stream");
            out.write(exportSnapshot.getBytes(StandardCharsets.UTF_8));message("JSON exported");
        } catch (Exception ex) { message("Export failed: "+ex.getClass().getSimpleName()); }
        exportSnapshot=null;
    }
}
