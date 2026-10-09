package dev.ghostcode.wearinstaller.common;

import org.junit.Test;
import java.io.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class ApkTransferTest {
    private final byte[] apk={0,1,2,3,4,5};
    private ApkTransfer.Header header() throws Exception { return new ApkTransfer.Header(apk.length,MessageDigest.getInstance("SHA-256").digest(apk),"Uhr-App.apk"); }
    @Test public void headerAndShortReadsRoundTripWithoutLosingBytes() throws Exception {
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ApkTransfer.writeHeader(new DataOutputStream(wire),header()); wire.write(apk);
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(wire.toByteArray()) {
            @Override public synchronized int read(byte[] b,int off,int len) { return super.read(b,off,Math.min(len,1)); }
        });
        ApkTransfer.Header actual=ApkTransfer.readHeader(in); assertEquals("Uhr-App.apk",actual.name());
        ByteArrayOutputStream received=new ByteArrayOutputStream(); long[] last={0};
        ApkTransfer.copyVerified(in,received,actual,(done,total) -> { assertTrue(done<=total); last[0]=done; });
        assertArrayEquals(apk,received.toByteArray()); assertEquals(apk.length,last[0]);
    }
    @Test public void truncatedApkCannotBeCommitted() throws Exception {
        assertThrows(EOFException.class,() -> ApkTransfer.copyVerified(new ByteArrayInputStream(new byte[]{0,1}),new ByteArrayOutputStream(),header(),(d,t) -> {}));
    }
    @Test public void tamperedApkFailsDigestCheck() throws Exception {
        assertThrows(IOException.class,() -> ApkTransfer.copyVerified(new ByteArrayInputStream(new byte[]{0,1,2,3,4,9}),new ByteArrayOutputStream(),header(),(d,t) -> {}));
    }
    @Test public void zeroLengthAndOversizedTransfersAreRejected() {
        for(long size:new long[]{0,-1,ApkTransfer.MAX_SIZE+1})
            assertThrows(IOException.class,() -> ApkTransfer.writeHeader(new DataOutputStream(new ByteArrayOutputStream()),new ApkTransfer.Header(size,new byte[32],"x.apk")));
    }
    @Test public void unknownProtocolAndTruncatedHeaderAreRejected() {
        assertThrows(IOException.class,() -> ApkTransfer.readHeader(new DataInputStream(new ByteArrayInputStream(new byte[12]))));
        assertThrows(EOFException.class,() -> ApkTransfer.readHeader(new DataInputStream(new ByteArrayInputStream(new byte[0]))));
    }
    @Test public void streamOnlyConsumesAnnouncedSize() throws Exception {
        ByteArrayOutputStream data=new ByteArrayOutputStream(); data.write(apk); data.write(99);
        ByteArrayInputStream in=new ByteArrayInputStream(data.toByteArray());
        ApkTransfer.copyVerified(in,new ByteArrayOutputStream(),header(),(d,t) -> {}); assertEquals(99,in.read());
    }
    @Test public void requestIdsCannotInjectPaths() {
        assertTrue(ApkTransfer.validRequest("99fc0624-8443-471c-b15c-d84d70c80831"));
        for(String id:new String[]{"../../cache","","abc","99fc0624-8443-471c-b15c-d84d70c80831/x"}) assertFalse(ApkTransfer.validRequest(id));
    }
}
