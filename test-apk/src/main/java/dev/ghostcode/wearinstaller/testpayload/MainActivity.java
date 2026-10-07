package dev.ghostcode.wearinstaller.testpayload;
public class MainActivity extends android.app.Activity { public void onCreate(android.os.Bundle b) { super.onCreate(b); android.widget.TextView v = new android.widget.TextView(this); v.setText("APK erfolgreich auf der Uhr installiert ✓"); v.setTextSize(20); v.setGravity(17); setContentView(v); } }
