package io.github.muntashirakon.adb;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AdbStreamTest {
    @Test(timeout=2000) public void earlyOpenReplyIsNotLost() throws Exception {
        AdbStream stream=stream();
        stream.updateRemoteId(2); acknowledge(stream);
        stream.awaitOpen();
    }
    @Test(timeout=2000) public void earlyOpenRejectionDoesNotWaitForever() throws Exception {
        AdbStream stream=stream(); stream.notifyClose(true);
        try { stream.awaitOpen(); fail("Rejected stream should fail"); }
        catch(java.net.ConnectException expected) {}
    }
    private void acknowledge(AdbStream stream) {
        synchronized(stream) { stream.readyForWrite(); stream.notifyAll(); }
    }
    @Test(timeout=10000) public void largeWritesRespectNegotiatedLimitsAndWaitForEveryAck() throws Exception {
        for(int maxData:new int[]{4096,65536,1024*1024}) {
            AdbConnection connection=mock(AdbConnection.class);
            when(connection.getMaxData()).thenReturn(maxData);
            AdbStream stream=new AdbStream(connection,1);
            BlockingQueue<byte[]> packets=new LinkedBlockingQueue<>();
            doAnswer(call -> { packets.add(call.getArgument(0)); return null; }).when(connection).sendPacket(any());
            byte[] source=new byte[2*1024*1024+17];
            for(int i=0;i<source.length;i++) source[i]=(byte)i;
            ExecutorService writer=Executors.newSingleThreadExecutor();
            try {
                acknowledge(stream);
                Future<?> task=writer.submit(() -> { stream.write(source,7,source.length-7); return null; });
                ByteArrayOutputStream received=new ByteArrayOutputStream();
                int packetCount=0;
                while(received.size()<source.length-7) {
                    byte[] packet=packets.poll(2,TimeUnit.SECONDS); assertNotNull(packet);
                    ByteBuffer header=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
                    assertEquals(AdbProtocol.A_WRTE,header.getInt());
                    header.position(12); int length=header.getInt();
                    assertTrue(length>0 && length<=maxData); assertEquals(24+length,packet.length);
                    received.write(packet,24,length);
                    if(packetCount++==0) {
                        assertNull("A second WRTE must wait for OKAY",packets.poll(100,TimeUnit.MILLISECONDS));
                        assertFalse(task.isDone());
                    }
                    acknowledge(stream);
                }
                task.get(2,TimeUnit.SECONDS);
                assertEquals((source.length-7+maxData-1)/maxData,packetCount);
                assertArrayEquals(Arrays.copyOfRange(source,7,source.length),received.toByteArray());
            } finally { stream.notifyClose(false); writer.shutdownNow(); }
        }
    }
    @Test(timeout=5000) public void peerCloseUnblocksWriterWaitingForNextFragment() throws Exception {
        AdbConnection connection=mock(AdbConnection.class);
        when(connection.getMaxData()).thenReturn(4096);
        AdbStream stream=new AdbStream(connection,1);
        BlockingQueue<byte[]> packets=new LinkedBlockingQueue<>();
        doAnswer(call -> { packets.add(call.getArgument(0)); return null; }).when(connection).sendPacket(any());
        ExecutorService writer=Executors.newSingleThreadExecutor();
        try {
            acknowledge(stream);
            Future<?> task=writer.submit(() -> { stream.write(new byte[8192],0,8192); return null; });
            assertNotNull(packets.poll(2,TimeUnit.SECONDS));
            stream.notifyClose(true);
            try { task.get(2,TimeUnit.SECONDS); fail("Write should fail after peer close"); }
            catch(ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
            assertTrue(packets.isEmpty());
        } finally { stream.notifyClose(false); writer.shutdownNow(); }
    }
    @Test(timeout=2000) public void zeroLengthWriteDoesNotConsumeReady() throws Exception {
        AdbConnection connection=mock(AdbConnection.class);
        when(connection.getMaxData()).thenReturn(4096);
        AdbStream stream=new AdbStream(connection,1); acknowledge(stream);
        stream.write(new byte[0],0,0); stream.write(new byte[]{42},0,1);
        verify(connection,times(1)).sendPacket(any());
    }
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
