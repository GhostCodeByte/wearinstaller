package dev.ghostcode.wearinstaller.mobile;

import io.github.muntashirakon.adb.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApkInstallerTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void rawTransferPreservesFileAndBatchesShortReads() throws Exception {
        byte[] source=new byte[4*1024*1024+17]; new Random(123).nextBytes(source);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        List<Integer> writes=new ArrayList<>(), progress=new ArrayList<>();
        OutputStream transport=new OutputStream() {
            @Override public void write(int b) { fail("Avoid byte-at-a-time transport writes"); }
            @Override public void write(byte[] bytes,int off,int len) { writes.add(len); wire.write(bytes,off,len); }
        };
        InputStream shortReads=new ByteArrayInputStream(source) {
            @Override public synchronized int read(byte[] bytes,int off,int len) { return super.read(bytes,off,Math.min(777,len)); }
        };
        ApkInstaller.transfer(source.length,shortReads,transport,(percent,text) -> progress.add(percent));
        assertArrayEquals(source,wire.toByteArray());
        assertEquals(17,writes.size()); assertEquals(Integer.valueOf(256*1024),writes.get(0));
        assertEquals(Integer.valueOf(17),writes.get(writes.size()-1));
        assertEquals(Integer.valueOf(90),progress.get(progress.size()-1));
        for(int i=1;i<progress.size();i++) assertTrue(progress.get(i)>=progress.get(i-1));
    }

    @Test public void brokenTransferDoesNotReportBytesAsSent() throws Exception {
        OutputStream broken=new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("Disconnected"); }
            @Override public void write(byte[] b,int off,int len) throws IOException { throw new IOException("Disconnected"); }
        };
        List<Integer> progress=new ArrayList<>();
        try {
            ApkInstaller.transfer(65536,new ByteArrayInputStream(new byte[65536]),broken,(percent,text) -> progress.add(percent));
            fail("Disconnected transfer should fail");
        } catch(IOException expected) { assertTrue(progress.isEmpty()); }
    }

    @Test public void truncatedOrGrowingFilesFail() throws Exception {
        for(int actual:new int[]{15,17}) {
            try {
                ApkInstaller.transfer(16,new ByteArrayInputStream(new byte[actual]),new ByteArrayOutputStream(),(p,t) -> {});
                fail("Changed file should fail");
            } catch(IOException expected) {}
        }
    }

    @Test public void streamsInstallationWithoutTempFileAndWaitsForConfirmation() throws Exception {
        runInstallation("Success\n__WEARINSTALLER_EXIT:0\n",true);
    }

    @Test public void failedOrUnconfirmedInstallationNeverReportsSuccessOrRetries() throws Exception {
        for(String response:new String[]{"", "Success\n", "Failure [INSTALL_FAILED_INVALID_APK]\n__WEARINSTALLER_EXIT:1\n"}) {
            runInstallation(response,false);
        }
    }

    private void runInstallation(String response,boolean success) throws Exception {
        byte[] source=new byte[256*1024+17]; new Random(12).nextBytes(source);
        File apk=temporary.newFile();
        try(OutputStream out=new FileOutputStream(apk)) { out.write(source); }
        AbsAdbConnectionManager adb=mock(AbsAdbConnectionManager.class);
        AdbStream stream=mock(AdbStream.class);
        ByteArrayOutputStream received=new ByteArrayOutputStream();
        ByteArrayInputStream reply=new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        when(adb.openStream(anyString())).thenReturn(stream);
        when(stream.openOutputStream()).thenReturn(new AdbOutputStream(stream));
        when(stream.openInputStream()).thenReturn(new AdbInputStream(stream));
        doAnswer(call -> { received.write(call.getArgument(0),call.getArgument(1),call.getArgument(2)); return null; })
                .when(stream).write(any(byte[].class),anyInt(),anyInt());
        when(stream.read(any(byte[].class),anyInt(),anyInt())).thenAnswer(call ->
                reply.read(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
        List<Integer> progress=new ArrayList<>();
        try {
            new ApkInstaller(adb).install(apk,(p,t) -> progress.add(p));
            assertTrue("Expected failure for "+response,success);
        } catch(IOException e) { assertFalse(e.getMessage(),success); }
        assertArrayEquals(source,received.toByteArray());
        verify(adb).openStream("exec:cmd package install -r -S "+source.length+"; result=$?; echo __WEARINSTALLER_EXIT:$result");
        verifyNoMoreInteractions(adb);
        verify(stream).close();
        assertEquals(success,progress.contains(100));
        assertTrue(progress.contains(93));
    }

    @Test public void requiresBothSuccessAndExitStatus() throws Exception {
        ApkInstaller.checkInstallResult("Success\n__WEARINSTALLER_EXIT:0\n");
        for(String result:new String[]{"Success","__WEARINSTALLER_EXIT:0","Success\n__WEARINSTALLER_EXIT:01\n",
                "Success\n__WEARINSTALLER_EXIT:1\n","Failure [INSTALL_FAILED_INVALID_APK]\n__WEARINSTALLER_EXIT:1"}) {
            try { ApkInstaller.checkInstallResult(result); fail(result); } catch(IOException expected) {}
        }
    }

    @Test public void explainsAbiAndSignatureErrors() {
        for(String[] item:new String[][]{{"INSTALL_FAILED_NO_MATCHING_ABIS","Prozessor"},{"INSTALL_FAILED_UPDATE_INCOMPATIBLE","Signing-Key"},{"INSTALL_FAILED_INSUFFICIENT_STORAGE","Speicher"},{"Failed to parse APK file: Corrupt XML binary file","gültige"}}) {
            try { ApkInstaller.checkInstallResult("Failure ["+item[0]+"]"); fail(); } catch(IOException e) { assertTrue(e.getMessage(),e.getMessage().contains(item[1])); }
        }
    }
}
