package dev.ghostcode.wearinstaller.wear;

import android.app.Activity;
import android.os.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, address;
    private boolean active;
    private final Runnable refresh = new Runnable() { public void run() {
        if (!active) return;
        address.setText(WatchPublisher.wifiIp(MainActivity.this).isEmpty() ? "WLAN verbinden" : "WLAN bereit ✓");
        Wearable.getCapabilityClient(MainActivity.this).getCapability(Protocol.PHONE_CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener(c -> status.setText(c.getNodes().isEmpty() ? "Handy nicht verbunden" : "Handy verbunden ✓"))
                .addOnFailureListener(e -> status.setText("Handy nicht verbunden"));
        WatchPublisher.publish(MainActivity.this,null,""); handler.postDelayed(this,10000);
    }};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Ui.BG);
        LinearLayout content = Ui.column(this,22); content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        TextView icon=Ui.text(this,"↓",32,Ui.GREEN); icon.setGravity(17); content.addView(icon);
        TextView title=Ui.title(this,"Wear Installer",18); title.setGravity(17); content.addView(title);
        status = Ui.text(this,"Handy suchen …",14,Ui.GREEN); status.setGravity(17); content.addView(status);
        address = Ui.text(this,"WLAN prüfen …",12,Ui.MUTED); address.setGravity(17); content.addView(address);
        scroll.addView(content); setContentView(scroll);
    }
    @Override public void onStart() { super.onStart(); active=true; handler.post(refresh); }
    @Override public void onStop() { active=false; handler.removeCallbacks(refresh); super.onStop(); }
}
