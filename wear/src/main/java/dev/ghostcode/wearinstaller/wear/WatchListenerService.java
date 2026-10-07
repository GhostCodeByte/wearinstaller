package dev.ghostcode.wearinstaller.wear;

import com.google.android.gms.wearable.*;
import dev.ghostcode.wearinstaller.common.Protocol;
import java.nio.charset.StandardCharsets;

public class WatchListenerService extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if (Protocol.REQUEST.equals(event.getPath())) {
            WatchPublisher.publish(this,event.getSourceNodeId(),new String(event.getData(),StandardCharsets.UTF_8));
        }
    }
}
