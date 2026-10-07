package dev.ghostcode.wearinstaller.mobile;

import android.content.*;
import android.net.Uri;
import android.os.*;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class PhoneController implements AdbDiscovery.Listener, MessageClient.OnMessageReceivedListener {
    interface Observer { void changed(); }
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AdbDiscovery discovery;
    private final Map<String,String> requests=new HashMap<>();
    private final Map<String,Node> watches=new LinkedHashMap<>();
    private volatile AdbIdentity adb;
    private volatile boolean closed;
    Observer observer;
    WatchInfo watch;
    AdbDiscovery.Endpoint connect, pairing, connectedEndpoint;
    boolean busy, pairingNeeded, adbConnected;
    String message="Uhr-App öffnen. Handy und Uhr müssen im selben WLAN sein.", stage="", fileName="Keine APK ausgewählt";
    int progress;
    File apk;
    private long lastResponse, nextConnect;
    private String selectedNode;
    PhoneController(Context c) {
        context=c.getApplicationContext(); discovery=new AdbDiscovery(context,this);
        selectedNode=context.getSharedPreferences("settings",0).getString("watchNode","");
        Wearable.getMessageClient(context).addListener(this); main.post(tick);
    }
    void notifyUi() { if(observer!=null) observer.changed(); }
    private final Runnable tick=new Runnable() { public void run() {
        if(closed) return;
        if(watch!=null && SystemClock.elapsedRealtime()-lastResponse>25000 && !busy) {
            resetWatch(); message="Uhr nicht erreichbar. Öffne die Uhr-App und prüfe die Verbindung."; notifyUi();
        }
        Wearable.getCapabilityClient(context).getCapability(Protocol.WATCH_CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener(cap -> {
                    if(closed) return;
                    watches.clear(); for(Node n:cap.getNodes()) watches.put(n.getId(),n);
                    Node selected=watches.get(selectedNode);
                    if(selected==null) selected=watches.values().stream().sorted(Comparator.comparing(Node::isNearby).reversed().thenComparing(Node::getId)).findFirst().orElse(null);
                    if(selected!=null) {
                        if(!selected.getId().equals(selectedNode)) { resetWatch(); selectedNode=selected.getId(); }
                        String nonce=UUID.randomUUID().toString(); requests.put(selectedNode,nonce);
                        Wearable.getMessageClient(context).sendMessage(selectedNode,Protocol.REQUEST,nonce.getBytes(StandardCharsets.UTF_8));
                    } else if(!busy) { resetWatch(); message="Keine Uhr verbunden. Verbinde deine Uhr mit dem Handy und installiere die Uhr-App."; }
                    notifyUi();
                }).addOnFailureListener(e -> { if(!closed) { message="Wear OS-Verbindung nicht verfügbar. Prüfe Google Play-Dienste und die Kopplung der Uhr."; notifyUi(); } });
        if(!busy && connect!=null && !adbConnected && SystemClock.elapsedRealtime()>=nextConnect && !pairingNeeded) autoConnect();
        if(adbConnected && adb!=null && !busy && !adb.isConnected()) { adbConnected=false; connectedEndpoint=null; nextConnect=0; notifyUi(); }
        main.postDelayed(this,8000);
    }};
    List<Node> watches() { return new ArrayList<>(watches.values()); }
    void selectWatch(String node) {
        if(busy) return;
        resetWatch(); selectedNode=node; context.getSharedPreferences("settings",0).edit().putString("watchNode",node).apply();
        requests.clear(); main.removeCallbacks(tick); main.post(tick);
    }
    @Override public void onMessageReceived(MessageEvent event) {
        if(!Protocol.INFO.equals(event.getPath()) || closed) return;
        try {
            WatchInfo info=WatchInfo.parse(event.getSourceNodeId(),event.getData());
            if(!info.nodeId.equals(selectedNode) || !info.nonce.equals(requests.get(info.nodeId))) return;
            requests.remove(info.nodeId); lastResponse=SystemClock.elapsedRealtime();
            boolean changed=watch==null || !watch.nodeId.equals(info.nodeId) || !watch.ip.equals(info.ip);
            if(changed) {
                if(busy) { message="Die WLAN-Adresse der Uhr hat sich geändert. Nach diesem Vorgang wird neu gesucht."; return; }
                resetWatch(); watch=info; discovery.start(info.ip);
                pairingNeeded=!context.getSharedPreferences("settings",0).getBoolean("paired_"+info.nodeId,false);
                message=info.ip.isEmpty()?"Verbinde die Uhr mit WLAN. Eine lokale IPv4-Adresse wird benötigt.":"Uhr erkannt. Wireless Debugging auf der Uhr aktivieren.";
            } else watch=info;
            notifyUi();
        } catch(Exception e) { message="Ungültige Antwort der Uhr-App. Aktualisiere beide Apps."; notifyUi(); }
    }
    private void resetWatch() {
        watch=null; connect=null; pairing=null; connectedEndpoint=null; adbConnected=false; pairingNeeded=false;
        discovery.close();
        worker.execute(() -> { try { if(adb!=null) adb.disconnect(); } catch(Exception ignored) {} });
    }
    @Override public void changed(AdbDiscovery.Endpoint c,AdbDiscovery.Endpoint p) {
        if(closed) return;
        boolean moved=connectedEndpoint!=null && !connectedEndpoint.equals(c);
        connect=c; pairing=p;
        if(moved && !busy) {
            adbConnected=false; connectedEndpoint=null; nextConnect=0;
            message="ADB-Verbindung unterbrochen. Wireless Debugging auf der Uhr aktivieren; der aktuelle Port wird automatisch gesucht.";
            worker.execute(() -> { try { if(adb!=null) adb.disconnect(); } catch(Exception ignored) {} });
        }
        if(c!=null && !adbConnected && !busy && !pairingNeeded && SystemClock.elapsedRealtime()>=nextConnect) autoConnect();
        notifyUi();
    }
    @Override public void error(String text) { message=text; notifyUi(); }
    private AdbIdentity identity() throws Exception { if(adb==null) adb=new AdbIdentity(context); return adb; }
    private void autoConnect() {
        if(connect==null || watch==null || busy || closed) return;
        AdbDiscovery.Endpoint target=connect; int api=watch.api;
        busy=true; stage="ADB-Verbindung herstellen …"; notifyUi();
        worker.execute(() -> {
            boolean success=false, needsPairing=false; String failure="";
            try { AdbIdentity a=identity(); a.setApi(api); a.disconnect(); success=a.connect(target.ip(),target.port()); }
            catch(io.github.muntashirakon.adb.AdbPairingRequiredException | io.github.muntashirakon.adb.AdbAuthenticationFailedException e) { needsPairing=true; failure="ADB einrichten: Gib den Kopplungscode deiner Uhr ein."; }
            catch(Exception e) { failure="ADB nicht erreichbar. Wireless Debugging und dasselbe WLAN prüfen. Falls die Uhr dieses Handy vergessen hat, erneut koppeln."; }
            boolean ok=success, pair=needsPairing; String error=failure;
            main.post(() -> {
                if(closed) return;
                busy=false; stage=""; adbConnected=ok && target.equals(connect); connectedEndpoint=adbConnected?target:null;
                pairingNeeded=pair; nextConnect=SystemClock.elapsedRealtime()+15000;
                message=adbConnected?"Bereit. Wähle eine APK für deine Uhr.":error.isEmpty()?"ADB-Verbindung fehlgeschlagen. Erneuter Versuch folgt automatisch.":error;
                notifyUi();
            });
        });
    }
    void retry() {
        if(busy || watch==null) return;
        nextConnect=0; pairingNeeded=!context.getSharedPreferences("settings",0).getBoolean("paired_"+watch.nodeId,false);
        connect=null; pairing=null; adbConnected=false; connectedEndpoint=null;
        discovery.start(watch.ip); message="Wireless ADB erneut suchen …"; notifyUi();
    }
    void pair(String code) {
        if(!code.matches("[0-9]{6}")) { message="Bitte den sechsstelligen Pairing-Code eingeben."; notifyUi(); return; }
        if(busy || pairing==null) { message="Öffne auf der Uhr „Gerät mit Kopplungscode koppeln“. Der Pairing-Port wird automatisch gesucht."; notifyUi(); return; }
        AdbDiscovery.Endpoint target=pairing;
        String pairingNode=selectedNode;
        busy=true; stage="Mit der Uhr koppeln …"; notifyUi();
        worker.execute(() -> {
            String error=null;
            try { if(!identity().pair(target.ip(),target.port(),code)) throw new IOException(); }
            catch(Exception e) { error="Koppeln fehlgeschlagen. Prüfe den Code und öffne den Kopplungsdialog auf der Uhr erneut."; }
            String result=error;
            main.post(() -> {
                if(closed) return;
                busy=false; stage="";
                if(result==null) {
                    context.getSharedPreferences("settings",0).edit().putBoolean("paired_"+pairingNode,true).apply();
                    pairingNeeded=false; nextConnect=0; message="Pairing erfolgreich. ADB wird automatisch verbunden.";
                    if(connect!=null) autoConnect();
                } else { pairingNeeded=true; message=result; }
                notifyUi();
            });
        });
    }
    void selectApk(Uri uri,String name) {
        if(busy) return;
        busy=true; stage="APK vorbereiten …"; progress=0; notifyUi();
        worker.execute(() -> {
            File prepared=new File(context.getCacheDir(),"selected-"+UUID.randomUUID()+".apk"); String error=null;
            try(InputStream in=context.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(prepared)) {
                if(in==null) throw new IOException(); Streams.copy(in,out);
                if(prepared.length()==0) throw new IOException();
                try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(prepared)) {
                    if(zip.getEntry("AndroidManifest.xml")==null) throw new IOException();
                }
            } catch(Exception e) { prepared.delete(); error="Die Datei ist keine lesbare APK. Wähle eine einzelne .apk-Datei."; }
            String result=error;
            main.post(() -> {
                if(closed) { prepared.delete(); return; }
                busy=false; stage="";
                if(result==null) { if(apk!=null) apk.delete(); apk=prepared; fileName=name; message="APK ausgewählt. Bereit zur Installation."; }
                else message=result;
                notifyUi();
            });
        });
    }
    void install() {
        if(busy || !adbConnected || adb==null || apk==null) return;
        busy=true; progress=0; stage="Installation vorbereiten …"; notifyUi();
        io.github.muntashirakon.adb.AdbConnection connection=adb.getAdbConnection();
        ScheduledFuture<?> timeout=timer.schedule(() -> { try { if(connection!=null) connection.close(); } catch(Exception ignored) {} },3,TimeUnit.MINUTES);
        worker.execute(() -> {
            String error=null;
            try { new ApkInstaller(adb).install(apk,(percent,text) -> main.post(() -> { progress=percent; stage=text; notifyUi(); })); }
            catch(Exception e) { error=e instanceof IOException && e.getMessage()!=null && e.getMessage().startsWith("Installation fehlgeschlagen") ? e.getMessage() : "Installation abgebrochen. Prüfe WLAN, Wireless Debugging und freien Speicher auf der Uhr. "+(e.getMessage()==null?"":e.getMessage()); }
            finally { timeout.cancel(false); }
            String result=error;
            main.post(() -> {
                if(closed) return;
                busy=false; adbConnected=adb.isConnected(); stage="";
                message=result==null?"Erfolgreich auf der Uhr installiert ✓":result;
                progress=result==null?100:0; notifyUi();
            });
        });
    }
    void close() {
        closed=true; observer=null; main.removeCallbacks(tick); discovery.close(); Wearable.getMessageClient(context).removeListener(this);
        timer.shutdownNow(); worker.shutdownNow();
        AdbIdentity a=adb; if(a!=null) new Thread(() -> { try { a.close(); } catch(Exception ignored) {} },"adb-close").start();
        if(apk!=null) apk.delete();
    }
}
