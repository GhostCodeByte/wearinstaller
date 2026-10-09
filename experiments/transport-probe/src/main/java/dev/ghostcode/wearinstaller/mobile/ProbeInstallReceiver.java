package dev.ghostcode.wearinstaller.mobile;

import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.Build;

public final class ProbeInstallReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
  String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
  ProbeActivity.logExternal("PACKAGE_STATUS=" + status + " detail=" + detail);
  if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
   Intent confirm = Build.VERSION.SDK_INT>=33
       ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
       : intent.getParcelableExtra(Intent.EXTRA_INTENT);
   if (confirm != null) {
    try { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    catch (Exception error) { ProbeActivity.logExternal("CONFIRM_FAILED=" + error); }
   }
  }
 }
}
