package dev.ghostcode.wearinstaller.wear;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;

public class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,address,installation;
    private Button permission,confirm,cancel;
    private boolean active;
    private String launched="";
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(prefs,key) -> handler.post(this::render);
    private final Runnable refresh=new Runnable() { public void run() {
        if(!active) return;
        address.setText(WatchPublisher.wifiIp(MainActivity.this).isEmpty()?"ADB: WLAN nicht verbunden":"ADB: WLAN bereit ✓");
        Wearable.getCapabilityClient(MainActivity.this).getCapability(Protocol.PHONE_CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener(c -> status.setText(c.getNodes().isEmpty()?"Handy nicht verbunden":"Handy verbunden ✓"))
                .addOnFailureListener(e -> status.setText("Handy nicht verbunden"));
        WatchPublisher.publish(MainActivity.this,null,""); render(); handler.postDelayed(this,10000);
    }};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(Ui.BG);
        LinearLayout content=Ui.column(this,26); content.setPadding(Ui.dp(this,26),Ui.dp(this,34),Ui.dp(this,26),Ui.dp(this,34));
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        TextView title=Ui.title(this,"Wear Installer",18); title.setGravity(17); content.addView(title);
        status=Ui.text(this,"Handy suchen …",14,Ui.GREEN); status.setGravity(17); content.addView(status);
        installation=Ui.text(this,"APK am Handy auswählen. Übertragung über die Uhr-Verbindung – auch ohne gemeinsames WLAN.",13,Ui.WHITE);
        installation.setGravity(17); content.addView(installation);
        permission=Ui.button(this,"Installationen erlauben",true); content.addView(permission);
        permission.setTextSize(13); permission.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))); }
            catch(Exception e) { installation.setText("Installationserlaubnis in den Einstellungen für Wear Installer aktivieren."); }
        });
        confirm=Ui.button(this,"Installation fortsetzen",true); confirm.setTextSize(13); content.addView(confirm);
        confirm.setOnClickListener(v -> openConfirmation());
        cancel=Ui.button(this,"Abbrechen",false); cancel.setTextSize(13); content.addView(cancel);
        cancel.setOnClickListener(v -> WatchInstall.cancel(this,WatchInstall.prefs(this).getString("node",""),WatchInstall.prefs(this).getString("id","")));
        address=Ui.text(this,"",11,Ui.MUTED); address.setGravity(17); content.addView(address);
        scroll.addView(content); setContentView(scroll);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},20);
    }
    private void render() {
        if(!active) return;
        String state=WatchInstall.state(this);
        String text=WatchInstall.prefs(this).getString("message","APK am Handy auswählen. Übertragung über die Uhr-Verbindung – auch ohne gemeinsames WLAN.");
        String name=WatchInstall.prefs(this).getString("name","");
        installation.setText(name.isEmpty()?text:name+"\n"+text);
        permission.setVisibility(getPackageManager().canRequestPackageInstalls()?android.view.View.GONE:android.view.View.VISIBLE);
        confirm.setVisibility(state.equals("confirm")?android.view.View.VISIBLE:android.view.View.GONE);
        cancel.setVisibility(WatchInstall.active(state)?android.view.View.VISIBLE:android.view.View.GONE);
        if(WatchInstall.active(state)) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WatchInstall.startReady(this);
        if(state.equals("confirm") && !launched.equals(WatchInstall.prefs(this).getString("id",""))) openConfirmation();
    }
    private void openConfirmation() {
        String encoded=WatchInstall.prefs(this).getString("confirm","");
        if(encoded.isEmpty()) return;
        try {
            launched=WatchInstall.prefs(this).getString("id","");
            startActivity(Intent.parseUri(encoded,Intent.URI_INTENT_SCHEME));
        } catch(Exception e) { installation.setText("Installationsdialog konnte nicht geöffnet werden. Am Handy abbrechen und erneut versuchen."); }
    }
    @Override public void onResume() {
        super.onResume(); active=true; WatchInstall.prefs(this).registerOnSharedPreferenceChangeListener(changes); handler.post(refresh);
    }
    @Override public void onPause() {
        active=false; WatchInstall.prefs(this).unregisterOnSharedPreferenceChangeListener(changes); handler.removeCallbacks(refresh); super.onPause();
    }
}
