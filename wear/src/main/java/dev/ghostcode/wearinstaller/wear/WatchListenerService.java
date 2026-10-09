package dev.ghostcode.wearinstaller.wear;

import android.os.SystemClock;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class WatchListenerService extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        String data=new String(event.getData(),StandardCharsets.UTF_8);
        if(Protocol.REQUEST.equals(event.getPath())) WatchPublisher.publish(this,event.getSourceNodeId(),data);
        else if(Protocol.INSTALL_QUERY.equals(event.getPath())) WatchInstall.send(this,event.getSourceNodeId(),data);
        else if(Protocol.INSTALL_CANCEL.equals(event.getPath())) WatchInstall.cancel(this,event.getSourceNodeId(),data);
    }
    @Override public void onChannelOpened(ChannelClient.Channel channel) {
        if(!channel.getPath().startsWith(Protocol.APK_CHANNEL)) return;
        ChannelClient client=Wearable.getChannelClient(this);
        String id=channel.getPath().substring(Protocol.APK_CHANNEL.length());
        if(!ApkTransfer.validRequest(id)) { client.close(channel); return; }
        boolean reserved=false;
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        AtomicLong last=new AtomicLong(SystemClock.elapsedRealtime());
        timer.scheduleWithFixedDelay(() -> {
            if(SystemClock.elapsedRealtime()-last.get()>60000) client.close(channel);
        },3,3,TimeUnit.SECONDS);
        try {
            CapabilityInfo phones=Tasks.await(Wearable.getCapabilityClient(this).getCapability(Protocol.PHONE_CAPABILITY,CapabilityClient.FILTER_REACHABLE),15,TimeUnit.SECONDS);
            if(phones.getNodes().stream().noneMatch(n -> n.getId().equals(channel.getNodeId()))) throw new IOException("Handy-App nicht verbunden.");
            reserved=WatchInstall.begin(this,channel.getNodeId(),id);
            if(!reserved) throw new IOException("Auf der Uhr wartet bereits eine Installation. Diese zuerst abschließen oder abbrechen.");
            try(DataInputStream in=new DataInputStream(Tasks.await(client.getInputStream(channel),20,TimeUnit.SECONDS))) {
                ApkTransfer.Header header=ApkTransfer.readHeader(in);
                if(header.size()+16L*1024*1024>getFilesDir().getUsableSpace()) throw new IOException("Zu wenig freier Speicher auf der Uhr.");
                WatchInstall.prefs(this).edit().putString("name",header.name()).commit();
                try(OutputStream out=new FileOutputStream(WatchInstall.file(this))) {
                    AtomicLong persisted=new AtomicLong(SystemClock.elapsedRealtime());
                    ApkTransfer.copyVerified(in,out,header,(done,total) -> {
                        long now=SystemClock.elapsedRealtime(); last.set(now);
                        if(now-persisted.get()>5000) {
                            persisted.set(now); WatchInstall.prefs(this).edit().putLong("updated",System.currentTimeMillis()).apply();
                        }
                    });
                }
                if(getPackageManager().getPackageArchiveInfo(WatchInstall.file(this).getPath(),0)==null) throw new IOException("Die Datei ist keine gültige APK.");
                WatchInstall.update(this,id,getPackageManager().canRequestPackageInstalls()?"ready":"permission",
                        getPackageManager().canRequestPackageInstalls()?"APK geprüft. Installation vorbereiten …":"Auf der Uhr einmal Installationen erlauben. Uhr-App öffnen.");
                WatchInstall.startReady(this);
            }
        } catch(Exception e) {
            String message=e.getMessage()==null?"Übertragung unterbrochen. Uhr-Verbindung prüfen.":e.getMessage();
            if(reserved) WatchInstall.update(this,id,"failed",message);
            else WatchInstall.reject(this,channel.getNodeId(),id,message);
        } finally {
            timer.shutdownNow(); client.close(channel);
        }
    }
}
