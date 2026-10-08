package dev.ghostcode.wearinstaller.mobile;

/** Tracks stalled transfers separately from the package manager's installation deadline. */
final class InstallationTimeout {
    private long lastActivity;
    private boolean installing;

    InstallationTimeout(long now) { lastActivity=now; }

    synchronized void progress(int percent,long now) {
        lastActivity=now;
        installing=percent>=93;
    }

    synchronized boolean expired(long now) {
        return now-lastActivity>=(installing?180000:45000);
    }

    synchronized String message() {
        return installing ? "Installation abgebrochen: Die Uhr hat drei Minuten lang nicht geantwortet."
                : "Übertragung abgebrochen: Seit 45 Sekunden wurden keine weiteren Daten übertragen. Prüfe die WLAN-Verbindung zur Uhr.";
    }
}
