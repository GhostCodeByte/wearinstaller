package dev.ghostcode.wearinstaller.mobile;

import android.content.Context;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.util.*;

/** Ports exist only in memory and are rediscovered for the current watch IP. */
final class AdbDiscovery implements AutoCloseable {
    static final String CONNECT = "_adb-tls-connect._tcp.", PAIRING = "_adb-tls-pairing._tcp.";
    record Endpoint(String serviceName, String ip, int port) {}
    interface Listener { void changed(Endpoint connect, Endpoint pairing); void error(String message); }
    private final NsdManager nsd;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final WifiManager.MulticastLock lock;
    private final List<NsdManager.DiscoveryListener> browsers = new ArrayList<>();
    private final Map<String,NsdServiceInfo> found = new HashMap<>();
    private final ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
    private String ip = "";
    private Endpoint connect, pairing;
    private boolean active, resolving;
    private int generation;
    AdbDiscovery(Context context, Listener listener) {
        nsd = context.getSystemService(NsdManager.class); this.listener=listener;
        lock = context.getSystemService(WifiManager.class).createMulticastLock("wearinstaller-mdns"); lock.setReferenceCounted(false);
    }
    void start(String watchIp) {
        close(); ip=watchIp;
        if (ip.isEmpty()) return;
        active=true; lock.acquire(); browse(CONNECT); browse(PAIRING); main.postDelayed(refresh,12000);
    }
    private final Runnable refresh = new Runnable() { public void run() {
        if (!active) return;
        for (NsdServiceInfo info : new ArrayList<>(found.values())) enqueue(info);
        main.postDelayed(this,12000);
    }};
    private void browse(String type) {
        int token=generation;
        NsdManager.DiscoveryListener browser = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onStartDiscoveryFailed(String t,int code) { main.post(() -> { if(token==generation) listener.error("mDNS-Suche fehlgeschlagen ("+code+"). WLAN prüfen und erneut versuchen."); }); }
            public void onStopDiscoveryFailed(String t,int code) {}
            public void onServiceFound(NsdServiceInfo info) { main.post(() -> { if (active && token==generation) { found.put(key(info),info); enqueue(info); } }); }
            public void onServiceLost(NsdServiceInfo info) { main.post(() -> {
                if(token!=generation) return;
                found.remove(key(info));
                if (connect != null && CONNECT.equals(normalize(info.getServiceType())) && connect.serviceName.equals(info.getServiceName())) connect=null;
                if (pairing != null && PAIRING.equals(normalize(info.getServiceType())) && pairing.serviceName.equals(info.getServiceName())) pairing=null;
                listener.changed(connect,pairing);
            }); }
        };
        browsers.add(browser); nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,browser);
    }
    static String normalize(String type) {
        while(type.startsWith(".")) type=type.substring(1);
        if(type.endsWith(".local.")) type=type.substring(0,type.length()-7);
        else if(type.endsWith(".local")) type=type.substring(0,type.length()-6);
        return type.endsWith(".") ? type : type+".";
    }
    private static String key(NsdServiceInfo info) { return info.getServiceType()+"/"+info.getServiceName(); }
    private void enqueue(NsdServiceInfo info) { if (!queue.contains(info)) queue.add(info); resolveNext(); }
    @SuppressWarnings("deprecation") private void resolveNext() {
        if (!active || resolving || queue.isEmpty()) return;
        NsdServiceInfo info = queue.remove(); resolving=true; int token=generation;
        nsd.resolveService(info,new NsdManager.ResolveListener() {
            public void onResolveFailed(NsdServiceInfo i,int code) { main.post(() -> { if(token==generation) { resolving=false; resolveNext(); } }); }
            public void onServiceResolved(NsdServiceInfo resolved) { main.post(() -> {
                if(BuildConfig.DEBUG) android.util.Log.d("AdbDiscovery","Resolved "+resolved+"; expected="+ip);
                if(token != generation) return;
                resolving=false;
                boolean hostMatches=Build.VERSION.SDK_INT>=34
                        ? resolved.getHostAddresses().stream().anyMatch(host -> matches(ip,host.getHostAddress(),resolved.getPort()))
                        : resolved.getHost()!=null && matches(ip,resolved.getHost().getHostAddress(),resolved.getPort());
                if(active && found.containsKey(key(info)) && hostMatches) {
                    Endpoint endpoint = new Endpoint(resolved.getServiceName(),ip,resolved.getPort());
                    if(CONNECT.equals(normalize(resolved.getServiceType()))) connect=endpoint;
                    if(PAIRING.equals(normalize(resolved.getServiceType()))) pairing=endpoint;
                    listener.changed(connect,pairing);
                }
                resolveNext();
            }); }
        });
    }
    static boolean matches(String expected, String actual, int port) { return !expected.isEmpty() && expected.equals(actual) && port>0 && port<=65535; }
    @Override public void close() {
        active=false; generation++; resolving=false; main.removeCallbacks(refresh);
        for(NsdManager.DiscoveryListener browser:browsers) { try { nsd.stopServiceDiscovery(browser); } catch(Exception ignored) {} }
        browsers.clear(); found.clear(); queue.clear(); connect=null; pairing=null;
        if(lock.isHeld()) lock.release();
    }
}
