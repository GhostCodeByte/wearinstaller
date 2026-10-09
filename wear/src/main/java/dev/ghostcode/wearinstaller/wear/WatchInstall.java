package dev.ghostcode.wearinstaller.wear;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import com.google.android.gms.wearable.Wearable;
import dev.ghostcode.wearinstaller.common.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Durable state for one explicit phone request; install callbacks survive process recreation. */
final class WatchInstall {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("install",0); }
    static String state(Context c) { return prefs(c).getString("state",""); }
    static boolean active(String state) {
        return state.equals("receiving") || state.equals("permission") || state.equals("ready")
                || state.equals("writing") || state.equals("installing") || state.equals("confirm");
    }
    static File file(Context c) { return file(c,prefs(c).getString("id","")); }
    static File file(Context c,String id) {
        if(!ApkTransfer.validRequest(id)) return new File(c.getFilesDir(),"unused.apk");
        return new File(c.getFilesDir(),"incoming-"+id+".apk");
    }
    static synchronized boolean begin(Context c,String node,String id) {
        android.content.SharedPreferences p=prefs(c);
        if(active(state(c))) {
            if(System.currentTimeMillis()-p.getLong("updated",0)<15*60*1000) return false;
            abandon(c);
        }
        file(c).delete();
        p.edit().clear().putString("node",node).putString("id",id).putString("name","APK")
                .putString("state","receiving").putString("message","APK wird empfangen …")
                .putLong("updated",System.currentTimeMillis()).commit();
        return true;
    }
    static synchronized void update(Context c,String id,String state,String message) {
        if(!id.equals(prefs(c).getString("id",""))) return;
        prefs(c).edit().putString("state",state).putString("message",message)
                .putLong("updated",System.currentTimeMillis()).commit();
        android.util.Log.i("WatchInstall",state+" "+id+" "+message);
        send(c,prefs(c).getString("node",""),id);
        if(state.equals("permission") || state.equals("confirm")) notifyAction(c,message);
        else if(!active(state)) {
            c.getSystemService(NotificationManager.class).cancel(100);
            file(c).delete();
        }
    }
    static void send(Context c,String node,String id) {
        android.content.SharedPreferences p=prefs(c);
        if(!node.equals(p.getString("node","")) || !id.equals(p.getString("id",""))) return;
        try {
            byte[] data=new JSONObject().put("id",id).put("state",state(c))
                    .put("message",p.getString("message","")).toString().getBytes(StandardCharsets.UTF_8);
            Wearable.getMessageClient(c).sendMessage(node,Protocol.INSTALL_STATUS,data);
        } catch(Exception e) { android.util.Log.w("WatchInstall","Status konnte nicht gesendet werden",e); }
    }
    static void reject(Context c,String node,String id,String message) {
        try {
            Wearable.getMessageClient(c).sendMessage(node,Protocol.INSTALL_STATUS,new JSONObject()
                    .put("id",id).put("state","failed").put("message",message).toString().getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored) {}
    }
    static synchronized void cancel(Context c,String node,String id) {
        if(!node.equals(prefs(c).getString("node","")) || !id.equals(prefs(c).getString("id","")) || !active(state(c))) return;
        abandon(c); update(c,id,"canceled","Installation abgebrochen.");
    }
    private static void abandon(Context c) {
        int session=prefs(c).getInt("session",-1);
        if(session!=-1) try { c.getPackageManager().getPackageInstaller().abandonSession(session); } catch(Exception ignored) {}
        prefs(c).edit().remove("confirm").remove("session").commit();
    }
    static void startReady(Context context) {
        Context c=context.getApplicationContext(); String id;
        synchronized(WatchInstall.class) {
            String state=state(c);
            if(!(state.equals("permission") || state.equals("ready")) || !c.getPackageManager().canRequestPackageInstalls()) return;
            id=prefs(c).getString("id","");
            update(c,id,"writing","Installation vorbereiten …");
        }
        new Thread(() -> install(c,id),"watch-package-install").start();
    }
    private static void install(Context c,String id) {
        PackageInstaller installer=c.getPackageManager().getPackageInstaller(); int sessionId=-1; boolean committed=false;
        try {
            File file=file(c,id);
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(file.length());
            // Android only honors this for eligible updates; new apps still require confirmation.
            if(Build.VERSION.SDK_INT>=31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            sessionId=installer.createSession(params);
            synchronized(WatchInstall.class) {
                if(!id.equals(prefs(c).getString("id","")) || !state(c).equals("writing")) throw new IOException("Installation abgebrochen.");
                prefs(c).edit().putInt("session",sessionId).commit();
            }
            try(PackageInstaller.Session session=installer.openSession(sessionId);
                InputStream in=new FileInputStream(file)) {
                try(OutputStream out=session.openWrite("base.apk",0,file.length())) {
                    byte[] buffer=new byte[65536]; int n;
                    while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                    session.fsync(out);
                }
                Intent result=new Intent(c,InstallReceiver.class).setAction("dev.ghostcode.wearinstaller.install."+id)
                        .setData(Uri.parse("wearinstaller://install/"+id));
                PendingIntent pending=PendingIntent.getBroadcast(c,sessionId,result,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                synchronized(WatchInstall.class) {
                    if(!id.equals(prefs(c).getString("id","")) || !state(c).equals("writing")) throw new IOException("Installation abgebrochen.");
                    update(c,id,"installing","Android installiert die APK …");
                    session.commit(pending.getIntentSender()); committed=true;
                }
            }
            file.delete();
        } catch(Exception e) {
            synchronized(WatchInstall.class) {
                if(id.equals(prefs(c).getString("id","")) && active(state(c)))
                    update(c,id,"failed","Installation fehlgeschlagen. "+(e.getMessage()==null?"Speicher und APK prüfen.":e.getMessage()));
            }
        } finally {
            if(!committed && sessionId!=-1) try { installer.abandonSession(sessionId); } catch(Exception ignored) {}
        }
    }
    private static void notifyAction(Context c,String message) {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("installation","APK-Installation",NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open=PendingIntent.getActivity(c,100,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,"installation").setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Wear Installer").setContentText(message).setContentIntent(open).setAutoCancel(true).build();
        try { manager.notify(100,notification); } catch(SecurityException ignored) {}
    }
    private WatchInstall() {}
}
