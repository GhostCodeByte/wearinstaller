package dev.ghostcode.wearinstaller.common;
import org.junit.Test;
import static org.junit.Assert.*;
public class WatchInfoTest {
    @Test public void onlyLocalNumericAddressesAreAccepted() {
        for(String ip:new String[]{"192.168.178.64","10.0.2.16","172.16.2.3","172.31.255.255","169.254.3.4"}) assertTrue(ip,WatchInfo.isLocalIpv4(ip));
        for(String ip:new String[]{"8.8.8.8","127.0.0.1","172.32.0.1","192.168.1.999","192.168.1.-1","192.168.1.1.attacker","example.com","","::1"}) assertFalse(ip,WatchInfo.isLocalIpv4(ip));
    }
}
