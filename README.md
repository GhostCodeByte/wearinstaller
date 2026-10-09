# Wear Installer

Zwei native Android-Apps: Das Handy installiert eine ausgewählte APK auf einer Wear-OS-Uhr. Es gibt zwei Installationswege, die am Handy unter **Installationsweg** gewählt werden:

- **Über Uhr-Verbindung** (neu in 1.2.0): Die APK läuft über die bestehende Wear-OS-Verbindung (Data Layer ChannelClient) zur Uhr-App, die sie mit Androids PackageInstaller installiert. Kein gemeinsames WLAN, kein Wireless Debugging, keine Kopplung. Einmalig „Installationen erlauben“ auf der Uhr; neue Apps bestätigt man auf der Uhr, Updates eigener Installationen laufen unter Android 12+ ohne Dialog.
- **Über ADB · wie bisher**: Direkte Installation über Wireless ADB nach einmaliger Kopplung, ohne Dialog auf der Uhr. Handy und Uhr müssen im selben WLAN oder im Handy-Hotspot sein.

![Installation am Handy](docs/screenshots/04-install-success.png)

## Installation und Bedienung

1. `wearinstaller-phone-1.2.0.apk` auf dem Handy installieren.
2. `wearinstaller-watch-1.2.0.apk` auf der Uhr installieren, etwa über einen Computer mit ADB. Die Uhr-App muss bereits vorhanden sein, damit das Handy die Uhr erkennt. Bestehende Release-Installationen lassen sich mit demselben Signing-Key aktualisieren.
3. Die Uhr mit der offiziellen Begleit-App mit dem Handy verbinden.
4. Wear Installer auf dem Handy öffnen. Die Uhr wird automatisch erkannt. Die Uhr-App kann auf Anfragen auch im Hintergrund antworten.

### Über Uhr-Verbindung

1. **APK auswählen** und **Auf Uhr installieren** drücken. Die APK wird mit SHA-256-Prüfsumme übertragen.
2. Beim ersten Mal auf der Uhr in Wear Installer **Installationen erlauben** antippen und „Unbekannte Apps installieren“ aktivieren.
3. Bei einer neuen App den Android-Installationsdialog auf der Uhr bestätigen. Updates von Apps, die Wear Installer bereits installiert hat, laufen ohne Dialog.

Das Handy zeigt den Status der Uhr live an. Eine laufende Installation lässt sich am Handy oder auf der Uhr abbrechen und wird nicht automatisch wiederholt. Maximal 512 MiB pro APK.

### Über ADB · wie bisher

Beide Geräte ins gleiche WLAN oder in den Handy-Hotspot bringen.

5. Beim ersten Mal auf der Uhr **Einstellungen → Entwickleroptionen → Wireless Debugging → Gerät mit Kopplungscode koppeln** öffnen. Auf englischen Uhren heißt der letzte Eintrag „Pair new device“.
6. Nur den sechsstelligen Code am Handy eingeben und **Koppeln** drücken. IP und beide Ports werden automatisch gefunden.
7. **APK auswählen** öffnet den Android File Picker. Danach **Auf Uhr installieren** drücken.

Bei späteren Starts erfolgt die Verbindung automatisch mit demselben ADB-Schlüssel. Verbindungsports werden niemals gespeichert. Nach einem Portwechsel sucht die App automatisch weiter. Wireless Debugging muss auf der Uhr weiterhin aktiv sein. Wenn Android es nach einem Neustart oder Netzwerkwechsel ausschaltet, muss der Nutzer es wieder einschalten; eine normale App kann das nicht selbst aktivieren.

## Voraussetzungen

- Handy: Android 11 / API 30 oder neuer, Google Play-Dienste und die passende offizielle Wear-OS-Begleit-App.
- Uhr-Verbindung: Wear OS mit Android 11 / API 30 oder neuer; beide Apps in Version 1.2.0 oder neuer.
- ADB: Uhr mit **TLS Wireless Debugging und Pairing-Code**, typischerweise Wear OS 4 oder neuer. Ältere Uhren mit ausschließlich unverschlüsseltem TCP-ADB werden nicht unterstützt. Dasselbe lokale IPv4-WLAN oder der Handy-Hotspot, in dem mDNS/Multicast und Geräteverbindungen erlaubt sind. Gastnetze mit Client-Isolation verhindern die Verbindung.
- Einzelne, eigenständig installierbare `.apk`-Dateien. Split-APK-Pakete (`.apks`, `.apkm`, `.xapk`) werden nicht unterstützt.

## Projekt

| Modul | Aufgabe |
| --- | --- |
| `mobile` | Oberfläche, File Picker, mDNS, TLS-Pairing, Schlüssel und ADB-Installation |
| `wear` | Gerätename, Modell, aktuelle WLAN-IP, Verbindungsstatus, APK-Empfang und PackageInstaller-Installation |
| `common` | Nachrichtenprotokoll, geprüftes APK-Übertragungsformat und kleine native UI-Helfer |
| `libadb` | LibADB Android 3.1.1 mit begrenzten Netzwerkwartezeiten |
| `test-apk` | Installierbare Test-App mit generiertem 4-MiB-Asset |

Beide Apps verwenden `dev.ghostcode.wearinstaller`, denselben Versionscode und denselben Signing-Key. Die Watch- und Phone-Capabilities identifizieren die passenden Apps. Bei mehreren Uhren wählt die App zunächst eine nahe Uhr; **Andere Uhr wählen** speichert eine bewusste Auswahl.

Das Handy fordert regelmäßig eine frische Antwort mit einem zufälligen Nonce an und akzeptiert nur die ausgewählte Data-Layer-Node. Die Uhr ermittelt ihre aktuelle IPv4-Adresse aus den WLAN-LinkProperties. Zusätzlich veröffentlicht sie einen dringenden DataItem; die Handy-Verbindung verwendet ausschließlich frische Antworten, damit ein alter DataItem keine Installation auf eine veraltete IP auslöst.

NSD sucht nach `_adb-tls-connect._tcp` und `_adb-tls-pairing._tcp`. Aufgelöste Adressen müssen exakt zur IP der ausgewählten Uhr passen. Portinformationen bleiben im Arbeitsspeicher. Dienstverlust, geänderte IP und geänderte Ports führen zu erneuter Suche.

Die RSA-ADB-Identität liegt AES-GCM-verschlüsselt im privaten `no_backup`-Verzeichnis des Handys. Der nicht exportierbare AES-Schlüssel liegt im Android Keystore. Backup ist deaktiviert. Der Pairing-Code wird nicht gespeichert. Die Uhr enthält keine ADB-Bibliothek und führt keine Shell-Kommandos aus.

Beim Weg über die Uhr-Verbindung öffnet das Handy einen Channel zur ausgewählten Node. Die Uhr akzeptiert nur Channels der verbundenen Handy-App mit derselben Signatur, immer nur eine Installation gleichzeitig, prüft Größe, freien Speicher, SHA-256 und den APK-Container und installiert dann über eine PackageInstaller-Session mit `USER_ACTION_NOT_REQUIRED`. Android entscheidet selbst, ob ein Bestätigungsdialog nötig ist.

Die Handy-App kopiert die File-Picker-Datei in ihren privaten Cache, prüft den APK-Container und überträgt sie direkt an Androids Paketinstaller: `exec:cmd package install -r -S <Dateigröße>`. Die Uhr liest exakt die angegebene Anzahl von Bytes aus dem ADB-Stream. Eine separate APK unter `/data/local/tmp` sowie der anschließende zusätzliche Kopierschritt entfallen. Android verwaltet seine internen Installationsdateien weiterhin selbst. Erfolg wird nur bei `Success` zusammen mit Exit-Status 0 gemeldet. Eine unterbrochene Installation wird nicht automatisch wiederholt.

Die Übertragung verwendet 256-KiB-Blöcke, auch wenn die Eingabe nur kurze Leseabschnitte liefert. Der ADB-Stream zerlegt die Ausgabe bei Bedarf gemäß dem von der Uhr gemeldeten Paketlimit und wartet vor jedem weiteren WRTE auf dessen Bestätigung. Dadurch entfallen die bisherigen separaten 4-KiB-Schreibvorgänge und SYNC-Header. Die Anzeige nennt übertragene MiB und die durchschnittliche Übertragungsrate; 90 % bedeutet, dass die APK gesendet wurde, 93 % das Warten auf Androids abschließendes Installationsergebnis. Die Übertragung wird nach 45 Sekunden ohne Fortschritt abgebrochen, mit einer Prüfauflösung von fünf Sekunden. Solange Daten übertragen werden, gilt keine feste Gesamtgrenze. Nach der Übertragung darf Android bis zu drei Minuten für das abschließende Installationsergebnis benötigen.

## Bauen und prüfen

Java 17, Android SDK Platform 35 und die SDK Build Tools installieren. `ANDROID_HOME` setzen oder `sdk.dir` in einer lokalen, nicht eingecheckten `local.properties` angeben.

```bash
./gradlew :mobile:assembleDebug :wear:assembleDebug :test-apk:assembleDebug
./gradlew :common:testDebugUnitTest :mobile:testDebugUnitTest :libadb:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug
```

Die Debug-APKs beider Module werden mit demselben Android-Debug-Key signiert. Release- und Debug-APKs dürfen für die Data Layer nicht gemischt werden.

Für Release-Builds einen gemeinsamen Keystore über diese Variablen bereitstellen:

```bash
export WEARINSTALLER_KEYSTORE=/sicherer/pfad/release.p12
export WEARINSTALLER_STORE_PASSWORD='dein-passwort'
./scripts/build-release.sh
```

Der Alias ist `wearinstaller`. Alternativ eine ignorierte `signing.properties` im Projekt anlegen:

```properties
storeFile=/sicherer/pfad/release.p12
storePassword=dein-passwort
keyAlias=wearinstaller
keyPassword=dein-passwort
```

Auf dem ursprünglichen Build-Host liegen Keystore und Passwort außerhalb des Repositories unter `~/.local/share/wearinstaller/signing/`. Diese Dateien sicher aufbewahren: Ohne denselben Signing-Key sind später keine Updates der Release-APKs möglich. Das Repository und die Release-Assets enthalten keine privaten Schlüssel.

`scripts/build-release.sh` führt Tests und Release-Lint aus und erzeugt beide signierten APKs sowie `SHA256SUMS` unter `artifacts/v1.2.0/`. Die GitHub-Actions-Konfiguration baut und prüft Debug-APKs ohne Release-Zugangsdaten.

## Tests und Quellen

Der tatsächlich ausgeführte Zwei-Geräte-Test, Screenshots und Grenzen der Emulatorprüfung stehen in [docs/TESTING.md](docs/TESTING.md). Die Untersuchung alternativer Übertragungswege steht in [docs/TRANSPORT_EXPERIMENTS.md](docs/TRANSPORT_EXPERIMENTS.md).

- [Google: Data Layer API, identischer Paketname und Signatur](https://developer.android.com/training/wearables/data/overview)
- [Google: Wear-Emulator mit Handy verbinden](https://developer.android.com/training/wearables/get-started/connect-phone)
- [Google: gemeinsames Emulator-WLAN und NSD](https://developer.android.com/studio/run/emulator-networking-interconnect)
- [Google: Network Service Discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [LibADB Android](https://github.com/MuntashirAkon/libadb-android), lokale Anpassungen und Lizenzen in [libadb/UPSTREAM.md](libadb/UPSTREAM.md)

Eigener Anwendungscode: Apache-2.0. Drittanbieter behalten ihre jeweiligen Lizenzen; siehe [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
