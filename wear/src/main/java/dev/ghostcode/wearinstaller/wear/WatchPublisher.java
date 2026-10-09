package dev.ghostcode.wearinstaller.wear;

import android.content.Context;
import android.net.*;
import android.os.Build;
import android.provider.Settings;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;
import org.json.JSONObject;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;

final class WatchPublisher {
    static String wifiIp(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
            LinkProperties props = cm.getLinkProperties(n);
            if (props == null) continue;
            for (LinkAddress a : props.getLinkAddresses()) {
                String ip = a.getAddress().getHostAddress();
                if (a.getAddress() instanceof Inet4Address && WatchInfo.isLocalIpv4(ip)) return ip;
            }
        }
        return "";
    }
    static String deviceName(Context c) {
        String name = Settings.Global.getString(c.getContentResolver(), "device_name");
        return name == null || name.trim().isEmpty() ? Build.MODEL : name;
    }
    static void publish(Context c, String node, String nonce) {
        try {
            String ip = wifiIp(c);
            JSONObject json = new JSONObject().put("deviceName",deviceName(c)).put("model",Build.MODEL)
                    .put("ip",ip).put("api",Build.VERSION.SDK_INT).put("nonce",nonce).put("installerVersion",Protocol.INSTALLER_VERSION);
            if (node != null) Wearable.getMessageClient(c).sendMessage(node,Protocol.INFO,
                    json.toString().getBytes(StandardCharsets.UTF_8));
            PutDataMapRequest req = PutDataMapRequest.create(Protocol.DATA);
            req.getDataMap().putString("deviceName",deviceName(c)); req.getDataMap().putString("model",Build.MODEL);
            req.getDataMap().putString("ip",ip); req.getDataMap().putLong("updatedAt",System.currentTimeMillis());
            Wearable.getDataClient(c).putDataItem(req.asPutDataRequest().setUrgent());
        } catch (Exception e) { android.util.Log.w("WatchPublisher","Data Layer nicht verfügbar",e); }
    }
}
