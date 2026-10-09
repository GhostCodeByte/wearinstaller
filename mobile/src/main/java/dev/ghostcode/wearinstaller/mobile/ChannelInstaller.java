package dev.ghostcode.wearinstaller.mobile;

import android.content.Context;
import android.os.SystemClock;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

final class ChannelInstaller implements AutoCloseable {
    private final ChannelClient client;
    private volatile ChannelClient.Channel channel;
    private volatile boolean canceled;
    ChannelInstaller(Context context) { client=Wearable.getChannelClient(context); }
    void send(String node,String id,File apk,String name,ApkTransfer.Progress progress) throws Exception {
        if(canceled) throw new IOException("Übertragung abgebrochen.");
        byte[] hash=ApkTransfer.digest(apk);
        channel=Tasks.await(client.openChannel(node,Protocol.APK_CHANNEL+id),20,TimeUnit.SECONDS);
        if(canceled) { close(); throw new IOException("Übertragung abgebrochen."); }
        AtomicLong last=new AtomicLong(SystemClock.elapsedRealtime());
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        timer.scheduleWithFixedDelay(() -> {
            if(SystemClock.elapsedRealtime()-last.get()>60000) close();
        },3,3,TimeUnit.SECONDS);
        try(DataOutputStream out=new DataOutputStream(Tasks.await(client.getOutputStream(channel),20,TimeUnit.SECONDS));
            InputStream in=new FileInputStream(apk)) {
            ApkTransfer.Header header=new ApkTransfer.Header(apk.length(),hash,name.length()>240?name.substring(0,240):name);
            ApkTransfer.writeHeader(out,header);
            ApkTransfer.copyVerified(in,out,header,(done,total) -> {
                if(canceled) throw new java.util.concurrent.CancellationException();
                last.set(SystemClock.elapsedRealtime()); progress.bytes(done,total);
            });
            out.flush();
        } finally {
            timer.shutdownNow();
            // Closing the output signals EOF. Keep the channel until the receiver acknowledges
            // verification through INSTALL_STATUS; a full close could discard queued bytes.
        }
    }
    @Override public void close() {
        canceled=true;
        ChannelClient.Channel c=channel;
        if(c!=null) client.close(c);
    }
}
