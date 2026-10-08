package dev.ghostcode.wearinstaller.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class InstallationTimeoutTest {
    @Test public void activeTransferCanExceedThreeMinutes() {
        InstallationTimeout timeout=new InstallationTimeout(0);
        for(long now=30000;now<=600000;now+=30000) {
            assertFalse(timeout.expired(now));
            timeout.progress(50,now);
        }
        assertFalse(timeout.expired(644999));
        assertTrue(timeout.expired(645000));
        assertTrue(timeout.message().contains("Übertragung"));
    }

    @Test public void installationGetsItsOwnThreeMinuteDeadline() {
        InstallationTimeout timeout=new InstallationTimeout(0);
        timeout.progress(90,40000);
        assertTrue(timeout.expired(85000));
        timeout.progress(93,80000);
        assertFalse(timeout.expired(259999));
        assertTrue(timeout.expired(260000));
        assertTrue(timeout.message().contains("Installation"));
    }
}
