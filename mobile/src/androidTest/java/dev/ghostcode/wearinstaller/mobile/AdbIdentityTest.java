package dev.ghostcode.wearinstaller.mobile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.content.Context;
import java.io.File;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class AdbIdentityTest {
    @Test public void reloadsSameRsaIdentityFromEncryptedStorage() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AdbIdentity first=new AdbIdentity(c), second=new AdbIdentity(c);
        assertArrayEquals(first.getCertificate().getEncoded(),second.getCertificate().getEncoded());
        assertArrayEquals(first.getPrivateKey().getEncoded(),second.getPrivateKey().getEncoded());
        byte[] disk=Files.readAllBytes(new File(c.getNoBackupFilesDir(),"adb-identity-v1").toPath());
        assertFalse("Private key must not be present in plaintext",contains(disk,first.getPrivateKey().getEncoded()));
        assertFalse("Certificate is stored in the same encrypted identity",contains(disk,first.getCertificate().getEncoded()));
    }
    private static boolean contains(byte[] bytes,byte[] value) {
        outer:for(int i=0;i<=bytes.length-value.length;i++) {
            for(int j=0;j<value.length;j++) if(bytes[i+j]!=value[j]) continue outer;
            return true;
        }
        return false;
    }
}
