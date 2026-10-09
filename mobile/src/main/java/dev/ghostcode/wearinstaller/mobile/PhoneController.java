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
import java.util.concurrent.atomic.AtomicBoolean;

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
    boolean busy, pairingNeeded, adbConnected, channelMode;
    private String installId="", installNode="", remoteState="";
    private long installStarted;
    private ChannelInstaller channelInstaller;
    String message="Uhr mit dem Handy verbinden und die Uhr-App installieren.", stage="", fileName="Keine APK ausgewählt";
    int progress;
    File apk;
    private long lastResponse, nextConnect;
    private String selectedNode;
    PhoneController(Context c) {
        context=c.getApplicationContext(); discovery=new AdbDiscovery(context,this);
        selectedNode=context.getSharedPreferences("settings",0).getString("watchNode","");
        android.content.SharedPreferences settings=context.getSharedPreferences("settings",0);
        channelMode=settings.getString("installMode",settings.getBoolean("paired_"+selectedNode,false)?"adb":"channel").equals("channel");
        installId=settings.getString("installId",""); installNode=settings.getString("installNode","");
        installStarted=settings.getLong("installStarted",0);
        if(!installId.isEmpty()) { busy=true; stage="Installationsergebnis von der Uhr abfragen …"; }
        Wearable.getMessageClient(context).addListener(this); main.post(tick);
    }
    void notifyUi() { if(observer!=null) observer.changed(); }
    private final Runnable tick=new Runnable() { public void run() {
        if(closed) return;
        if(watch!=null && SystemClock.elapsedRealtime()-lastResponse>25000 && !busy) {
            resetWatch(); message="Uhr nicht erreichbar. Öffne die Uhr-App und prüfe die Verbindung."; notifyUi();
        }
        if(!installId.isEmpty()) {
            context.getSharedPreferences("settings",0).edit().putLong("installStarted",installStarted).apply();
            if(System.currentTimeMillis()-installStarted>15*60*1000) finishChannel(false,"Kein abschließendes Ergebnis erhalten. Prüfe die Uhr-App. Die Installation wird nicht automatisch wiederholt.");
            else Wearable.getMessageClient(context).sendMessage(installNode,Protocol.INSTALL_QUERY,installId.getBytes(StandardCharsets.UTF_8));
        }
        Wearable.getCapabilityClient(context).getCapability(Protocol.WATCH_CAPABILITY,CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener(cap -> {
                    if(closed) return;
                    watches.clear(); for(Node n:cap.getNodes()) watches.put(n.getId(),n);
                    Node selected=watches.get(selectedNode);
                    if(selected==null && !busy) selected=watches.values().stream().sorted(Comparator.comparing(Node::isNearby).reversed().thenComparing(Node::getId)).findFirst().orElse(null);
                    if(selected!=null) {
                        if(!selected.getId().equals(selectedNode)) { resetWatch(); selectedNode=selected.getId(); }
                        String nonce=UUID.randomUUID().toString(); requests.put(selectedNode,nonce);
                        Wearable.getMessageClient(context).sendMessage(selectedNode,Protocol.REQUEST,nonce.getBytes(StandardCharsets.UTF_8));
                    } else if(!busy) { resetWatch(); message="Keine Uhr verbunden. Verbinde deine Uhr mit dem Handy und installiere die Uhr-App."; }
                    notifyUi();
                }).addOnFailureListener(e -> { if(!closed) { message="Wear OS-Verbindung nicht verfügbar. Prüfe Google Play-Dienste und die Kopplung der Uhr."; notifyUi(); } });
        if(!channelMode && !busy && connect!=null && !adbConnected && SystemClock.elapsedRealtime()>=nextConnect && !pairingNeeded) autoConnect();
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
        if(closed) return;
        if(Protocol.INSTALL_STATUS.equals(event.getPath())) {
            main.post(() -> channelStatus(event.getSourceNodeId(),event.getData())); return;
        }
        if(!Protocol.INFO.equals(event.getPath())) return;
        try {
            WatchInfo info=WatchInfo.parse(event.getSourceNodeId(),event.getData());
            if(!info.nodeId.equals(selectedNode) || !info.nonce.equals(requests.get(info.nodeId))) return;
            requests.remove(info.nodeId); lastResponse=SystemClock.elapsedRealtime();
            boolean changed=watch==null || !watch.nodeId.equals(info.nodeId) || !watch.ip.equals(info.ip);
            if(changed) {
                if(busy) {
                    if(!installId.isEmpty()) watch=info;
                    else message="Die WLAN-Adresse der Uhr hat sich geändert. Nach diesem Vorgang wird neu gesucht.";
                    notifyUi(); return;
                }
                resetWatch(); watch=info; if(!channelMode) discovery.start(info.ip);
                pairingNeeded=!context.getSharedPreferences("settings",0).getBoolean("paired_"+info.nodeId,false);
                message=channelMode?channelReadyMessage():info.ip.isEmpty()?"Verbinde die Uhr mit WLAN. Eine lokale IPv4-Adresse wird benötigt.":"Uhr erkannt. Wireless Debugging auf der Uhr aktivieren.";
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
        if(closed || channelMode) return;
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
    @Override public void error(String text) { if(!channelMode && !busy) { message=text; notifyUi(); } }
    String channelReadyMessage() {
        return watch!=null && watch.installerVersion>=Protocol.INSTALLER_VERSION
                ?"Bereit über die Uhr-Verbindung. Neue Apps auf der Uhr bestätigen; Updates laufen soweit erlaubt ohne Dialog."
                :"Für diesen Weg bitte auch die Uhr-App auf Version 1.2.0 oder neuer aktualisieren.";
    }
    boolean canInstall() {
        return !busy && apk!=null && (channelMode?watch!=null && watch.installerVersion>=Protocol.INSTALLER_VERSION:adbConnected);
    }
    void setChannelMode(boolean enabled) {
        if(busy || channelMode==enabled) return;
        channelMode=enabled; context.getSharedPreferences("settings",0).edit().putString("installMode",enabled?"channel":"adb").apply();
        discovery.close(); connect=null; pairing=null; connectedEndpoint=null; adbConnected=false; nextConnect=0;
        worker.execute(() -> { try { if(adb!=null) adb.disconnect(); } catch(Exception ignored) {} });
        if(enabled) message=channelReadyMessage();
        else if(watch!=null) { discovery.start(watch.ip); pairingNeeded=!context.getSharedPreferences("settings",0).getBoolean("paired_"+watch.nodeId,false); message="Wireless ADB suchen. Beide Geräte ins gleiche WLAN oder in den Handy-Hotspot bringen."; }
        else message="Uhr mit dem Handy verbinden.";
        notifyUi();
    }
    private AdbIdentity identity() throws Exception { if(adb==null) adb=new AdbIdentity(context); return adb; }
    private void autoConnect() {
        if(channelMode || connect==null || watch==null || busy || closed) return;
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
        if(busy) return;
        if(channelMode || watch==null) { main.removeCallbacks(tick); main.post(tick); return; }
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
                if(in==null) throw new IOException();
                byte[] buffer=new byte[65536]; long size=0; int count;
                while((count=in.read(buffer))!=-1) {
                    size+=count; if(size>ApkTransfer.MAX_SIZE) throw new IOException("APK größer als 512 MiB.");
                    out.write(buffer,0,count);
                }
                if(prepared.length()==0 || prepared.length()>ApkTransfer.MAX_SIZE) throw new IOException();
                try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(prepared)) {
                    if(zip.getEntry("AndroidManifest.xml")==null) throw new IOException();
                }
            } catch(Exception e) { prepared.delete(); error="Die Datei ist keine lesbare APK oder größer als 512 MiB. Wähle eine einzelne .apk-Datei."; }
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
        if(channelMode) { installChannel(); return; }
        if(busy || !adbConnected || adb==null || apk==null) return;
        busy=true; progress=0; stage="Installation vorbereiten …"; notifyUi();
        io.github.muntashirakon.adb.AdbConnection connection=adb.getAdbConnection();
        worker.execute(() -> {
            InstallationTimeout deadline=new InstallationTimeout(SystemClock.elapsedRealtime());
            AtomicBoolean timedOut=new AtomicBoolean();
            ScheduledFuture<?> timeout=timer.scheduleWithFixedDelay(() -> {
                if(deadline.expired(SystemClock.elapsedRealtime()) && timedOut.compareAndSet(false,true)) {
                    try { if(connection!=null) connection.close(); } catch(Exception ignored) {}
                }
            },5,5,TimeUnit.SECONDS);
            String error=null;
            try { new ApkInstaller(adb).install(apk,(percent,text) -> {
                deadline.progress(percent,SystemClock.elapsedRealtime());
                main.post(() -> { if(!closed) { progress=percent; stage=text; notifyUi(); } });
            }); }
            catch(Exception e) {
                error=timedOut.get() ? deadline.message()
                        : e instanceof IOException && e.getMessage()!=null && e.getMessage().startsWith("Installation fehlgeschlagen") ? e.getMessage() : "Installation abgebrochen. Prüfe WLAN, Wireless Debugging und freien Speicher auf der Uhr. "+(e.getMessage()==null?"":e.getMessage());
            }
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
    private void installChannel() {
        if(!canInstall() || watch==null) return;
        installId=UUID.randomUUID().toString(); installNode=watch.nodeId; remoteState="";
        installStarted=System.currentTimeMillis(); busy=true; progress=0; stage="APK-Übertragung vorbereiten …";
        context.getSharedPreferences("settings",0).edit().putString("installId",installId).putString("installNode",installNode).putLong("installStarted",installStarted).apply();
        ChannelInstaller sender=new ChannelInstaller(context); channelInstaller=sender;
        String id=installId,node=installNode,name=fileName; File selected=apk; notifyUi();
        worker.execute(() -> {
            try {
                long started=SystemClock.elapsedRealtime();
                sender.send(node,id,selected,name,(done,total) -> main.post(() -> {
                    if(closed || !id.equals(installId) || !(remoteState.isEmpty() || remoteState.equals("receiving"))) return;
                    installStarted=System.currentTimeMillis(); progress=(int)(done*90/total);
                    stage=String.format(Locale.GERMANY,"Übertragen: %.1f / %.1f MiB",done/1048576.0,total/1048576.0); notifyUi();
                }));
                main.post(() -> {
                    if(closed || !id.equals(installId)) return;
                    if(remoteState.isEmpty() || remoteState.equals("receiving")) { progress=90; stage="APK auf der Uhr prüfen …"; notifyUi(); }
                    android.util.Log.i("ChannelInstaller","APK gesendet in "+(SystemClock.elapsedRealtime()-started)+" ms");
                    Wearable.getMessageClient(context).sendMessage(node,Protocol.INSTALL_QUERY,id.getBytes(StandardCharsets.UTF_8));
                });
            } catch(Exception e) {
                sender.close();
                main.post(() -> {
                    if(!closed && id.equals(installId) && (remoteState.isEmpty() || remoteState.equals("receiving"))) {
                        Wearable.getMessageClient(context).sendMessage(node,Protocol.INSTALL_CANCEL,id.getBytes(StandardCharsets.UTF_8));
                        finishChannel(false,"Übertragung abgebrochen. Prüfe die Uhr-Verbindung und den Speicher. "+(e.getMessage()==null?"":e.getMessage()));
                    }
                });
            }
        });
    }
    private void channelStatus(String node,byte[] bytes) {
        if(closed || installId.isEmpty() || !installNode.equals(node)) return;
        try {
            org.json.JSONObject json=new org.json.JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!installId.equals(json.optString("id"))) return;
            remoteState=json.optString("state"); String detail=json.optString("message");
            android.util.Log.i("ChannelInstaller","Uhr-Status: "+remoteState+" "+detail);
            if(remoteState.equals("success")) finishChannel(true,detail);
            else if(remoteState.equals("failed") || remoteState.equals("canceled")) finishChannel(false,detail);
            else if(!remoteState.equals("receiving")) {
                progress=93; stage=detail; message=remoteState.equals("permission") || remoteState.equals("confirm")
                        ?"Öffne Wear Installer auf der Uhr, falls kein Dialog angezeigt wird.":"APK übertragen und geprüft. Warte auf Android …"; notifyUi();
            }
        } catch(Exception ignored) {}
    }
    void cancelInstall() {
        if(installId.isEmpty()) return;
        Wearable.getMessageClient(context).sendMessage(installNode,Protocol.INSTALL_CANCEL,installId.getBytes(StandardCharsets.UTF_8));
        finishChannel(false,"Abbruch angefordert. Prüfe die Uhr, falls Android die Installation bereits abgeschlossen hat.");
    }
    boolean channelPending() { return !installId.isEmpty(); }
    private void finishChannel(boolean success,String text) {
        if(channelInstaller!=null) channelInstaller.close(); channelInstaller=null;
        installId=""; installNode=""; remoteState="";
        context.getSharedPreferences("settings",0).edit().remove("installId").remove("installNode").remove("installStarted").apply();
        busy=false; progress=success?100:0; stage=""; message=text; notifyUi();
    }
    void close() {
        closed=true; observer=null; main.removeCallbacks(tick); discovery.close(); Wearable.getMessageClient(context).removeListener(this);
        if(channelInstaller!=null) channelInstaller.close();
        timer.shutdownNow(); worker.shutdownNow();
        AdbIdentity a=adb; if(a!=null) new Thread(() -> { try { a.close(); } catch(Exception ignored) {} },"adb-close").start();
        if(apk!=null) apk.delete();
    }
}
