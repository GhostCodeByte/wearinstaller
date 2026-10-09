package dev.ghostcode.wearinstaller.wear;

import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.Build;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent result) {
        synchronized(WatchInstall.class) {
            String id=result.getData()==null?"":result.getData().getLastPathSegment();
            if(id==null || !id.equals(WatchInstall.prefs(context).getString("id","")) || !WatchInstall.active(WatchInstall.state(context))) return;
            int status=result.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
            if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm=Build.VERSION.SDK_INT>=33?result.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class):result.getParcelableExtra(Intent.EXTRA_INTENT);
                if(confirm==null) { WatchInstall.update(context,id,"failed","Android hat keinen Installationsdialog bereitgestellt."); return; }
                // The system Intent contains the installer component and primitive session ID.
                // Persist it so opening the watch app still works after process recreation.
                WatchInstall.prefs(context).edit().putString("confirm",confirm.toUri(Intent.URI_INTENT_SCHEME)).commit();
                WatchInstall.update(context,id,"confirm","Installation auf der Uhr bestätigen.");
            } else if(status==PackageInstaller.STATUS_SUCCESS) {
                WatchInstall.update(context,id,"success","Erfolgreich auf der Uhr installiert ✓");
            } else if(status==PackageInstaller.STATUS_FAILURE_ABORTED) {
                WatchInstall.update(context,id,"canceled","Installation auf der Uhr abgebrochen.");
            } else {
                String detail=result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                if(detail!=null && detail.length()>500) detail=detail.substring(0,500);
                WatchInstall.update(context,id,"failed","Installation fehlgeschlagen. "+(detail==null?"APK und freien Speicher prüfen.":detail));
            }
        }
    }
}
