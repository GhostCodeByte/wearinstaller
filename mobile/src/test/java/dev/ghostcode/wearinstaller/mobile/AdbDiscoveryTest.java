package dev.ghostcode.wearinstaller.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdbDiscoveryTest {
    @Test public void androidResolvedTypesMayHaveLeadingDot() {
        assertEquals(AdbDiscovery.CONNECT,AdbDiscovery.normalize("._adb-tls-connect._tcp"));
        assertEquals(AdbDiscovery.PAIRING,AdbDiscovery.normalize("_adb-tls-pairing._tcp.local."));
        assertEquals(AdbDiscovery.CONNECT,AdbDiscovery.normalize("_adb-tls-connect._tcp."));
    }
    @Test public void servicesMustBelongToSelectedWatch() {
        assertTrue(AdbDiscovery.matches("192.168.1.4","192.168.1.4",41837));
        assertFalse(AdbDiscovery.matches("192.168.1.4","192.168.1.5",41837));
        assertFalse(AdbDiscovery.matches("","",41837));
        assertFalse(AdbDiscovery.matches("192.168.1.4","192.168.1.4",0));
        assertFalse(AdbDiscovery.matches("192.168.1.4","192.168.1.4",65536));
    }
}
