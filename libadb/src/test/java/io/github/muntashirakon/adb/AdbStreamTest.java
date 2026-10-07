package io.github.muntashirakon.adb;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AdbStreamTest {
    private AdbStream stream() throws Exception {
        AdbConnection connection=mock(AdbConnection.class);
        when(connection.getMaxData()).thenReturn(4096);
        return new AdbStream(connection,1);
    }
    @Test(timeout=2000) public void queuedResponseThenPeerCloseDrainsBeforeEof() throws Exception {
        AdbStream stream=stream();
        stream.addPayload("Failure [INSTALL_FAILED_INVALID_APK]\n".getBytes(StandardCharsets.UTF_8));
        stream.addPayload("__WEARINSTALLER_EXIT:1\n".getBytes(StandardCharsets.UTF_8));
        stream.notifyClose(true);
        java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[7]; int count;
        while((count=stream.openInputStream().read(buffer))!=-1) result.write(buffer,0,count);
        assertEquals("Failure [INSTALL_FAILED_INVALID_APK]\n__WEARINSTALLER_EXIT:1\n",result.toString("UTF-8"));
    }
    @Test(timeout=2000) public void peerClosePreservesPartiallyReadPayload() throws Exception {
        AdbStream stream=stream(); stream.addPayload(new byte[]{1,2,3,4});
        byte[] buffer=new byte[2]; assertEquals(2,stream.read(buffer,0,2));
        stream.notifyClose(true);
        assertEquals(2,stream.openInputStream().read(buffer)); assertArrayEquals(new byte[]{3,4},buffer);
        assertEquals(-1,stream.openInputStream().read(buffer));
    }
    @Test(timeout=2000) public void emptyPeerCloseIsEofAndByteReadsAreUnsigned() throws Exception {
        AdbStream stream=stream(); stream.addPayload(new byte[]{(byte)255}); stream.notifyClose(true);
        assertEquals(255,stream.openInputStream().read()); assertEquals(-1,stream.openInputStream().read());
        assertEquals(0,stream.openInputStream().read(new byte[0]));
    }
}
