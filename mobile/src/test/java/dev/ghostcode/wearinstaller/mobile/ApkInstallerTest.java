package dev.ghostcode.wearinstaller.mobile;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;
public class ApkInstallerTest {
    @Test public void requiresBothSuccessAndExitStatus() throws Exception {
        ApkInstaller.checkInstallResult("Success\n__WEARINSTALLER_EXIT:0\n");
        for(String result:new String[]{"Success","__WEARINSTALLER_EXIT:0","Failure [INSTALL_FAILED_INVALID_APK]\n__WEARINSTALLER_EXIT:1"}) {
            try { ApkInstaller.checkInstallResult(result); fail(result); } catch(IOException expected) {}
        }
    }
    @Test public void explainsAbiAndSignatureErrors() {
        for(String[] item:new String[][]{{"INSTALL_FAILED_NO_MATCHING_ABIS","Prozessor"},{"INSTALL_FAILED_UPDATE_INCOMPATIBLE","Signing-Key"},{"INSTALL_FAILED_INSUFFICIENT_STORAGE","Speicher"},{"Failed to parse APK file: Corrupt XML binary file","gültige"}}) {
            try { ApkInstaller.checkInstallResult("Failure ["+item[0]+"]"); fail(); } catch(IOException e) { assertTrue(e.getMessage(),e.getMessage().contains(item[1])); }
        }
    }
}
