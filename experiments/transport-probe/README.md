# Transport Probe — ausschließlich Emulator-Labor

Opt-in-Test-App für den Vergleich von direktem TLS-ADB, einer Data-Layer-Weiterleitung zu lokalem TLS-ADB und APK-Übertragung mit anschließendem `PackageInstaller`. Ergebnisprotokoll: [TRANSPORT_EXPERIMENTS.md](../../docs/TRANSPORT_EXPERIMENTS.md).

Die normale Handy- und Uhr-App werden nicht verändert. Ohne `-PtransportProbe` gehört dieses Modul nicht zum Gradle-Projekt. Die beiden Labor-Varianten haben dieselbe eigene Application-ID `dev.ghostcode.wearinstaller.transportprobe` und denselben Debug-Signing-Key. Sie übernehmen weder die Daten noch den ADB-Schlüssel der normalen Installer-App.

```bash
./gradlew -PtransportProbe :transport-probe:assembleDebug \
  :transport-probe:lintPhoneDebug :transport-probe:lintWatchDebug
```

Ausgaben:

- `build/outputs/apk/phone/debug/transport-probe-phone-debug.apk`
- `build/outputs/apk/watch/debug/transport-probe-watch-debug.apk`

Die APK mit dem generierten 4-MiB-Asset aus `:test-apk` wird beim Build als Testdatei eingebettet. `AdbIdentity`, `AdbDiscovery`, `ApkInstaller` und `Streams` werden aus der Handy-App in das Build-Verzeichnis kopiert, damit der Versuch deren tatsächliches Verhalten nutzt.

## Ablauf

1. Zwei eigene Emulatoren reservieren. Phone- und Watch-Variante auf dem jeweiligen Gerät installieren und beide Apps öffnen.
2. Die Wear Data Layer mit der offiziellen Companion-App koppeln. Für die hier genutzte Emulator-Kopplung: am Handy `adb forward tcp:5602 tcp:5601`, auf der Uhr `adb reverse tcp:5601 tcp:5602`, am Handy die Activity `com.google.android.wearable.app/.EmulatorActivity` starten. Das ersetzt den realen Bluetooth-Transport und darf nicht als Bluetooth-Nachweis ausgegeben werden.
3. **Peers** prüft die Verbindung zur genau einen Uhr. **Channel + PackageInstaller** sendet die eingebettete APK mit Länge und SHA-256. Die Uhr prüft die gesamte Datei, erstellt eine reguläre Installationssession und öffnet die vom System geforderte Bestätigung. Unbekannte Installationsquellen müssen für die Labor-App auf der Uhr über deren Einstellungen erlaubt werden.
4. Für ADB auf der Uhr Wireless Debugging aktivieren. IP und den jeweils aktuellen Port am Handy eintragen. **Direct pair / Relay pair** verwenden den Pairing-Port und den angezeigten Code. **Direct install / Relay install / Relay shell** verwenden den davon verschiedenen Connect-Port. **Discover ADB** protokolliert die unveränderte mDNS-Suche der Handy-App; es trägt keinen Port automatisch in das Eingabefeld ein.
5. Für die Relay-Variante endet der Phone-ADB-Socket auf `127.0.0.1` am Handy. Ein `ChannelClient` leitet die Bytes an die Uhr-App weiter. Diese verbindet ausschließlich zu `127.0.0.1:<Port>` auf der Uhr. Es gibt keine Weiterleitung zu frei wählbaren entfernten Hosts.
6. Für den Hotspot-Test den Hotspot am Handy aktivieren, die Uhr über ihre normale WLAN-Einstellung damit verbinden und Wireless Debugging für das neue Netzwerk erneut erlauben. Für den Versuch ohne WLAN beide WLAN-Interfaces und den Hotspot ausschalten. Auf der Uhr ausdrücklich die native **Einstellungen → WLAN → aus**-Einstellung verwenden: Ein bloßes `cmd wifi set-wifi-enabled disabled` wurde später durch die Wear-WLAN-Automatik rückgängig gemacht. Den Zustand vor Empfang, vor Bestätigung und nach erfolgreicher Installation kontrollieren.

Beide Labor-Apps während der Versuche geöffnet halten. Ergebnisse stehen in Logcat unter `TransportProbe`: `PAIR_RESULT`, `ADB_INSTALL_OK`, `ADB_SHELL_OK`, `CHANNEL_RECEIVED`, `PACKAGE_STATUS`. Erst `PACKAGE_STATUS=0` bestätigt eine normale Installation; `-1` fordert eine Nutzeraktion, `3` meldet einen Abbruch.

## Grenzen des Prototyps

Die UI und Kanalverwaltung dienen kontrollierten Vordergrundversuchen, nicht dem produktiven Einsatz. Die Portangabe erfolgt manuell, nur ein Peer wird akzeptiert, Übertragungen sind auf 64 MiB begrenzt. Es gibt keinen vollständigen Dienst für Hintergrundbetrieb, keine robuste Wiederaufnahme und keine ausgereifte Behandlung paralleler Aktionen oder sämtlicher Verbindungsabbrüche. Testdateien bleiben im privaten App-Verzeichnis bis zur Deinstallation. Die Messwerte sind keine Bluetooth-Benchmarks.

Für eine Produktimplementierung wären außerdem Installationsstatus am Handy, Session-/Datei-Cleanup, sichere Auswahl der Uhr, Übertragungsabbruch, Hintergrundregeln und Herstellerunterschiede zu behandeln. Die vorhandene normale Installer-App bleibt das getrennte Produkt.
