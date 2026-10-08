# Testprotokoll — 7. Oktober 2026

## Umgebung

- Handy: `Agent_Android_01`, Android 15 / API 35, x86_64.
- Uhr: `Agent_PixelWatch_34`, Wear OS 5 / Android 14 / API 34, x86_64, rundes 384 × 384-Display.
- Android Emulator 37.1.11, KVM, gemeinsames virtuelles WLAN `AndroidWifi`.
- Google Wear OS Phone Companion `2.66.107.587544675.gms`. APK-Signatur geprüft: Google ClockWork, SHA-256 `85cd5973541be6f477d847a0bcc6aa2527684b819cd5968529664cb07157b6fe`.
- Beide Geräte im T3-Gerätepanel geöffnet und mit `agent-device` bedient. Systemdienste, Builds, Installation der beiden Ausgangs-Apps und Paket-/Prüfsummenprüfung erfolgten mit ADB.

Die Emulator-Verbindung der Google Data Layer wurde entsprechend Android Studios Pairing Assistant mit einem Phone-Forward `tcp:5602 → tcp:5601`, einem Watch-Reverse `tcp:5601 → tcp:5602` und der offiziellen `.EmulatorActivity` eingerichtet. Die Peer-IDs `7f6755fd` / `f4d1e135` meldeten `true,true`. Diese Weiterleitungen betreffen ausschließlich die Emulator-Data-Layer; die App-Installation läuft direkt vom Handy zu `adbd` auf der WLAN-IP der Uhr, ohne Host-ADB als Proxy.

## Ausgeführte Abläufe

| Prüfung | Tatsächlich beobachtetes Ergebnis |
| --- | --- |
| Uhr ohne Wireless Debugging erkennen | Handy zeigt `sdk_gwear_x86_64` und „Uhr verbunden ✓“; Uhr meldet ihre WLAN-IP `10.0.2.17` über eine echte Data-Layer-Nachricht. |
| Automatische mDNS-Suche | `_adb-tls-connect._tcp` und `_adb-tls-pairing._tcp` werden auf der passenden Uhr-IP aufgelöst. Kein IP-/Port-Eingabefeld vorhanden. |
| Falscher Pairing-Code | `000000` wird abgelehnt; verständliche Fehlermeldung und erneute Eingabe möglich. |
| Richtiger Pairing-Code | TLS-/SPAKE2-Pairing erfolgreich; Uhr schließt den Kopplungsdialog, Handy verbindet ADB automatisch. Auch ein Code mit führender Null wurde im Release-Build erfolgreich verwendet. |
| Android File Picker | APK über Downloads ausgewählt; Dateiname erscheint am Handy. |
| Echte APK-Installation | Erst kleine Test-APK, dann APK mit generiertem 4-MiB-Asset über `sync:` übertragen und auf der Uhr installiert. Fortschritt sichtbar, anschließend 100 % und Erfolg. |
| Vollständigkeit der Übertragung | SHA-256 der Eingabedatei stimmt mit der installierten `base.apk` auf der Uhr überein. |
| Start der installierten App | Test-App zeigt „APK erfolgreich auf der Uhr installiert ✓“. |
| App-Neustart | Handy-App force-stopped und erneut geöffnet: Uhr und ADB werden ohne neuen Code automatisch verbunden. |
| Gespeicherte Identität | SHA-256 des verschlüsselten Identitätsfiles vor/nach Neustart identisch: `971f22b1a36c99f532c4c9c562c9f2f0d3957c286a781b2cf8f12e2aa1dc4c2e` im Debug-Durchlauf. |
| Geänderter ADB-Port | Wireless Debugging aus/ein: alter Connect-Port `34911`, neuer Port `42383`; Handy erkennt Dienstverlust und verbindet automatisch mit dem neu entdeckten Port, ohne erneutes Pairing. |
| Fehlerhafte APK | ZIP mit beschädigtem Manifest ausgewählt und Installation ausgelöst. Meldung: „Installation fehlgeschlagen. Die Datei ist keine gültige, eigenständig installierbare APK.“ Keine Erfolgsmeldung; Bedienelemente wieder freigegeben. |
| Temporäre Dateien | Nach erfolgreicher und abgelehnter Installation keine `wearinstaller-*.apk` unter `/data/local/tmp`. |
| Release-Signaturen | Beide Release-APKs bestehen `apksigner verify`; identischer Signer SHA-256 `35893122c24d65b863f5847355546f98a35767d20e60caae31baebb05e3e10eb`. |
| Release-Apps | Debug-Apps entfernt, beide Release-APKs installiert; Data Layer, Erst-Pairing und Installation erneut durchlaufen. Korrigierte finale Release-APK anschließend als signaturgleiches Update installiert und Erfolg sowie Fehler erneut getestet. |

Test-APK: 4.203.273 Byte; SHA-256 `73233491553aa3ee62ccf586e1263c33331f0a12b3f0c3a2fd759b947d535e93`.

Die Test-APK wurde ausschließlich als Datei auf das Handy gelegt; der eigentliche Installationsbefehl wurde durch die Handy-App über ihre ADB-Verbindung ausgeführt. Die Uhr-App war an Übertragung und Installation nicht beteiligt.

## Automatisierte Prüfungen

- **11 JVM-Tests bestanden:** lokale IP-Validierung, genaue Zuordnung zur Uhr-IP, Androids aufgelöste mDNS-Diensttypen mit führendem Punkt, echte Erfolgsbestätigung, verständliche Installationsfehler, ADB-Public-Key-Encoding und drei Stream-EOF-Regressionen.
- **1 Android-Instrumentierungstest bestanden:** dieselbe RSA-Identität wird erneut geladen; weder Private Key noch Zertifikat sind als Klartext im gespeicherten Identitätsfile enthalten.
- **Android Lint bestanden:** Handy und Uhr, Debug und Release. Verbleibende Warnungen betreffen u. a. neuere SDK-/Dependency-Versionen und native UI-Texte; keine Lint-Fehler.
- **Release-Build bestanden:** Java 17, Gradle 8.14.3, Android Gradle Plugin 8.11.0, Compile/Target SDK 35.

Der Zwei-Geräte-Test fand zwei relevante Fehler, die vor dem Release korrigiert wurden: Androids führender Punkt im NSD-Service-Typ und ein upstream Stream-EOF-Fehler, der bei bereits empfangenen Antwortpaketen mit anschließendem Peer-Close warten konnte. Beide Reparaturen haben Regressionstests.

## Screenshots

- [Uhr erkannt](screenshots/01-watch-detected.png)
- [Pairing-Dialog auf der Uhr](screenshots/02-watch-pairing.png)
- [Pairing-Eingabe auf dem Handy](screenshots/07-phone-pairing.png)
- [Uhr-App verbunden](screenshots/03-watch-connected.png)
- [Übertragungsfortschritt](screenshots/08-install-progress.png)
- [Installation erfolgreich](screenshots/04-install-success.png)
- [Installierte App auf der Uhr](screenshots/05-installed-app.png)
- [Wiederverbindung nach Portwechsel](screenshots/06-reconnected-new-port.png)
- [Verständlicher APK-Fehler](screenshots/09-invalid-apk-error.png)

Die abgebildeten Pairing-Codes stammen von Test-Emulatoren und sind nach Abschluss des jeweiligen Kopplungsdialogs ungültig.

## Grenzen

Keine physische Pixel Watch oder Samsung-Uhr getestet. API 30–33 wurden durch Lint auf API-Kompatibilität geprüft, aber nicht auf weiteren Geräten ausgeführt. Mehrere gleichzeitig gekoppelte Uhren, reale Router mit Multicast-Filterung, WLAN-IP-Wechsel und 16-KiB-Speicherseiten wurden nicht praktisch getestet. Portwechsel, App-Neustart und die Verbindungsanzeigen beider Apps wurden wie oben angegeben geprüft; ein vollständiger Uhr-Neustart war nicht Teil des Ablaufs.

Emulator-Images können ADB mit gelockerten Autorisierungsregeln betreiben. Die Pairing-Code-Prüfung und TLS-Kommunikation wurden tatsächlich ausgeführt; das Ausschließen ungekoppelter Clients auf einer physischen Uhr und Hardware-Keystore-Schutz lassen sich damit nicht vollständig nachweisen. Bei Verbindungsabbruch kann die Remote-Cleanup-Datei verbleiben, bis sie entfernt werden kann.

## Übertragungsoptimierung — 8. Oktober 2026

Nach der Umstellung auf gepufferte SYNC-Ausgaben wurden **18 JVM-Tests erfolgreich ausgeführt**. Die neuen Regressionen prüfen vollständige Multi-MiB-Daten mit ADB-Paketlimits von 4 KiB, 64 KiB und 1 MiB, die notwendige Bestätigung jedes WRTE-Fragments, einen Peer-Abbruch während des Wartens und leere Schreibvorgänge. Ein weiterer Test rekonstruiert eine 4-MiB-Datei aus der gepufferten SYNC-Ausgabe, prüft das 64-KiB-Limit jedes DATA-Records und weniger als 100 Ausgabeschreibvorgänge. Ein fehlgeschlagener Schreibvorgang darf keinen Übertragungsfortschritt melden. Die Timeout-Tests prüfen eine zehn Minuten lang aktive Übertragung sowie die separate Drei-Minuten-Grenze für die Installation.

Debug-Build und Debug-Lint waren im ersten Prüflauf erfolgreich; nach den abschließenden Änderungen waren alle JVM-Tests, Release-Lint und der signierte Handy-Release-Build erfolgreich. `apksigner verify` bestätigt denselben Release-Signer wie oben. Für diese Änderung wurde kein neuer Geräte-Durchlauf ausgeführt. Die Paket-/Protokolltests belegen weniger Schreibvorgänge und korrektes Verhalten bei Abbrüchen; sie liefern keinen WLAN-Geschwindigkeitsvergleich mit Wear Installer 2 oder einer physischen Uhr.

## Release 1.1.0 — direkte Streaming-Installation

Der finale Installationsweg ersetzt die oben beschriebene SYNC-Zwischenstufe durch `exec:cmd package install -r -S <Dateigröße>`. Die App sendet rohe APK-Bytes in 256-KiB-Blöcken und liest anschließend die Antwort und den ausdrücklich ausgegebenen Shell-Exit-Status aus demselben Stream. Auf der Uhr wird keine separate APK unter `/data/local/tmp` erzeugt.

**23 JVM-Tests bestanden**, einschließlich neuer Prüfungen für den vollständigen Streaming-Ablauf, kurze InputStream-Leseabschnitte, verkürzte oder gewachsene Quelldateien, fehlende Erfolgsbestätigung und Installationsfehler ohne automatischen Wiederholungsversuch. Die simulierte 4-MiB-Datei plus 17 Byte benötigt 17 Ausgabeschreibvorgänge. Das ist eine Protokollprüfung, keine WLAN-Zeitmessung. Zwei weitere ADB-Tests prüfen, dass eine vorzeitig empfangene OPEN-Bestätigung oder Ablehnung keinen unbegrenzten Wartezustand auslöst.

Das Release-Skript hat alle JVM-Tests, Release-Lint für Handy und Uhr und beide signierten Release-Builds erfolgreich ausgeführt. Beide Apps tragen Version `1.1.0` / Versionscode `2`. Der zusätzliche Geräte-Durchlauf war nicht möglich: Der gemeinsame Emulator-Pool war bei beiden Reservierungsversuchen ausgelastet. Für 1.1.0 wird daher weder ein neuer praktischer Wear-OS-Test noch ein gemessener Geschwindigkeitsgewinn gegenüber Wear Installer 2 behauptet.
