# Verbindungsversuche — 8. Oktober 2026

## Ergebnis und Empfehlung

Für das Ziel „APK am Handy auswählen und über die bestehende Handy-Uhr-Verbindung installieren“ ist **Wear Data Layer / ChannelClient plus PackageInstaller auf der Uhr** der aussichtsreichste Ansatz. Update, frische Installation und bewusstes Abbrechen wurden mit ausgeschaltetem WLAN auf beiden Emulatoren und deaktiviertem Wireless Debugging auf der Uhr ausgeführt. Die normale Installationsbestätigung auf der Uhr bleibt erforderlich.

**Handy-Hotspot plus bestehendes TLS-ADB** funktioniert ebenfalls und ist der beste geprüfte Ausweichweg, wenn eine Installation über ADB benötigt wird. **Eine transparente ADB-Brücke über ChannelClient** ist technisch möglich, benötigt aber weiterhin laufendes Wireless Debugging auf der Uhr und war bei der APK-Übertragung erheblich langsamer.

Das ist ein erfolgreicher Nachweis auf dem unten beschriebenen Wear-OS-Emulator, keine Garantie für alle physischen Pixel-/Samsung-Uhren oder für reale Bluetooth-Geschwindigkeit.

## Umgebung und Versuchsaufbau

- Handy: `Agent_Android_01`, Android 15 / API 35, x86_64, reservierter Emulator-Port 5580.
- Uhr: `Agent_PixelWatch_34`, Wear OS 5 / Android 14 / API 34, x86_64, Port 5586.
- Android Emulator 37.1.11. Beide Geräte im T3-Device-Panel sichtbar; UI mit `agent-device` bedient.
- Separate Labor-App, eigene Application-ID `dev.ghostcode.wearinstaller.transportprobe`. Normale App-UIDs im ersten Durchlauf 10215 am Handy und 10115 auf der Uhr; nach Deinstallation und erneuter Installation neue normale App-UIDs, beispielsweise 10122 auf der Uhr. Die Uhr-App besitzt `REQUEST_INSTALL_PACKAGES`, keine privilegierte `INSTALL_PACKAGES`-Berechtigung.
- [Quellcode und Ablauf der Labor-App](../experiments/transport-probe/README.md). ADB-Schlüssel, ADB-Installation und mDNS stammen aus den tatsächlichen Klassen der normalen Handy-App.
- Testdatei: 4.203.273 Byte. SHA-256 `a6c9600b17d3cf46a3d626ae0952f95e084b9cc8e4a2d73532e2ba0cb41cc7fa`.

Die offizielle Companion-App und die Emulator-Kopplung verbinden die Wear Data Layer über Phone-Forward `tcp:5602 → tcp:5601` und Watch-Reverse `tcp:5601 → tcp:5602`. Das ist **keine echte Bluetooth-Funkstrecke**. Host-ADB wurde für Testaufbau, Bedienung, Netzwerkdiagnose, Deinstallation der eigenen Test-App und Prüfung der installierten Datei verwendet. Die untersuchten Installationen selbst wurden jeweils durch den ADB-Client der Labor-App oder die normale PackageInstaller-Session ihrer Uhr-App ausgeführt. Es gab keinen Host-ADB-Installationsbefehl für die Test-Payload.

## Tatsächlich ausgeführte Prüfungen

| Versuch | Beobachtung | Aussage |
| --- | --- | --- |
| Direktes TLS-ADB im gemeinsamen `AndroidWifi` | Shell läuft als UID 2000; vollständige APK-Installation erfolgreich, 4.175 ms. | Referenz für den bestehenden ADB-Installationsweg. |
| TLS-Pairing durch die eigene Data-Layer-Brücke | `PAIR_RESULT=true relay=true`, 843 ms. | Auch das Pairing kann über einen eigenen Transport zum lokalen Uhr-Dienst weitergeleitet werden. |
| APK über transparente ADB-Brücke | Shell und vollständige Installation erfolgreich, 153.595 ms; temporäre ADB-Datei anschließend entfernt. | Machbar, aber mit dem vorhandenen blockweise bestätigten ADB-Stream rund 37-mal langsamer als die direkte Referenz. |
| Relay-Shell bei ausgeschaltetem Handy-WLAN | Uhr-WLAN an, Handy-WLAN aus; `ADB_SHELL_OK relay=true`, 739 ms. | Das Handy braucht für den Relay-Weg keine direkte WLAN-Verbindung zur Uhr. Die Data-Layer-Verbindung muss bestehen. |
| Relay bei ausgeschaltetem Uhr-WLAN | Wireless Debugging wechselt auf 0; Verbindung der Uhr-App zu `127.0.0.1:45223` endet mit `ECONNREFUSED`; am Handy kein ADB-Erfolg. | Diese Variante beseitigt die WLAN-Voraussetzung des Wireless-Debugging-Dienstes auf der Uhr nicht. |
| Echter emulierter Handy-Hotspot | Handy aktiviert `AndroidAP_3796`; Uhr findet und verbindet diesen WPA2-Hotspot. Handy `192.168.172.138`, Uhr `192.168.172.233`. | Kein externer WLAN-Router erforderlich. Es bleibt eine lokale WLAN-Strecke. |
| ADB über den Handy-Hotspot | Nach erneutem Erlauben von Wireless Debugging erscheint Port 45223; die kopierte mDNS-Klasse findet ihn. Direkte APK-Installation erfolgreich, 4.022 ms. | Hotspot-Netzwerk, Dienstsuche und tatsächliche Installation funktionieren im Emulator. |
| APK über ChannelClient | 4.203.273 Byte vollständig empfangen, SHA-256 stimmt; erste Übertragung rund 6,6 s. | Die Datei lässt sich über die Wear-Verbindung statt über direkten Phone-to-Watch-ADB übertragen. |
| Regulärer PackageInstaller | Zunächst `STATUS_PENDING_USER_ACTION=-1`; Uhr bietet Erlaubnis für unbekannte Quellen und anschließend Bestätigungsdialog. Nach Bestätigung `STATUS_SUCCESS=0`. | Auf diesem Wear-OS-Image ist eine normale Installation durch eine unprivilegierte Uhr-App möglich. |
| Update ohne WLAN und ohne Wireless Debugging | WLAN an beiden Geräten aus, Hotspot aus, `adb_wifi_enabled=0`; Channel-Empfang 6.584 ms, nach **UPDATE** `PACKAGE_STATUS=0`. | Installation braucht in diesem Ablauf kein Wireless-ADB. |
| Frische Installation bewusst abgebrochen | Eigene Test-Payload zuvor deinstalliert. Channel-Empfang 6.543 ms, Dialog fragt nach Erstinstallation. Zurück-Taste → `PACKAGE_STATUS=3`; Paket bleibt abwesend. | Kein falscher Erfolg nach verweigerter Bestätigung. |
| Frische Installation ohne WLAN erfolgreich | Erneute Übertragung, 6.554 ms; **INSTALL** bestätigt, `PACKAGE_STATUS=0`. Installierte `base.apk` hat denselben SHA-256 wie die Quelldatei, Test-App startet. | Vollständiger Weg von Dateiübertragung bis tatsächlicher Erstinstallation nachgewiesen. |
| Strenger Schlussdurchlauf mit nativer WLAN-aus-Einstellung | Finale getrennte Phone-/Watch-Laborvarianten neu installiert, eigene Test-Payload abwesend. WLAN auf der Uhr über die Oberfläche ausdrücklich aus. Empfang 6.511 ms, **INSTALL**, `PACKAGE_STATUS=0`. Beide Geräte melden vor und nach dem Ablauf WLAN aus; Uhr zusätzlich vor Bestätigung und bei gestarteter Test-App, Wireless Debugging 0. Installierte Prüfsumme identisch. | Der Erfolg hängt auf diesem Emulator weder vom gemeinsamen WLAN noch von Wireless Debugging ab. |

Zeitwerte der direkten/Relay-ADB-Installationen umfassen Verbindungsaufbau, Test-Shell, Vorbereitung, Übertragung, Installation und Cleanup in der Labor-App. Channel-Zeitwerte messen den Dateiempfang einschließlich Prüfsumme; die anschließende Zeit für manuelle Bestätigung ist nicht enthalten. **Keine dieser Zahlen misst reale Bluetooth-Leistung.**

## Relevante Grenzen und offene Punkte

1. **Bluetooth bleibt auf echter Hardware zu prüfen.** ChannelClient unterstützt offiziell Bluetooth als bevorzugten Transport. Die Emulator-Kopplung ersetzt diese Funkstrecke durch die oben beschriebene Weiterleitung. Erfolgreiche Tests ohne WLAN beweisen hier die App- und Systemabläufe über die Data Layer, nicht die reale Funkverbindung oder ihre Geschwindigkeit.
2. **Hersteller und Wear-OS-Versionen:** Getestet wurden genau API 34 auf der Uhr und API 35 am Handy. Insbesondere die Verfügbarkeit und Bedienbarkeit der Berechtigung für unbekannte Quellen und der Installationsbestätigung müssen auf einer physischen Zieluhr geprüft werden.
3. **Hotspot-Automatik braucht weitere Arbeit:** Die Uhr deaktivierte Wireless Debugging beim Netzwerkwechsel und verlangte danach erneut die Zustimmung zum neuen Netzwerk. Nach dem Wechsel löste mDNS zeitweise sowohl den alten Port 37537 als auch den neuen Port 45223 unter der neuen IP auf. Die Labor-App verwendete für die erfolgreiche Installation bewusst den aktuellen Port. Das vollständige automatische Verbinden der normalen App im Hotspot-Betrieb ist dadurch noch nicht nachgewiesen; insbesondere die Auswahl bei veralteten Dienstanzeigen muss gehärtet werden.
4. **ADB-Autorisierung:** Das Emulator-Image meldet `ro.adb.secure=0`. TLS-/SPAKE2-Pairing wurde tatsächlich ausgeführt; ein Emulatorerfolg beweist trotzdem nicht die vollständige Schlüsselzulassung oder sämtliche SELinux-Regeln einer Produktionsuhr.
5. **Vordergrund-Prototyp:** Beide Labor-Apps wurden für die Übertragungen geöffnet gehalten. Standby, Hintergrundlimits, große Dateien, Reichweitenabbrüche, echte Bluetooth-Wiederverbindung und mehrere Uhren sind nicht validiert. Die transparente ADB-Brücke mit rund 154 s liegt für diese 4-MB-Datei bereits nahe der Drei-Minuten-Grenze der normalen Installer-App.
6. **WLAN-Automatik der Uhr:** In einem zusätzlichen Gegencheck reaktivierte Wear OS das WLAN zwischen dem Ausschalten per `cmd wifi` und der Installationsbestätigung. Die frühen WLAN-aus-Durchläufe und die Systembefehle allein belegen deshalb keine dauerhaft ausgeschaltete Funkstrecke. Anschließend wurde der Versuch mit der ausdrücklichen WLAN-aus-Einstellung in der Uhr-Oberfläche wiederholt. Dabei blieb WLAN an allen kontrollierten Punkten aus, einschließlich nach `STATUS_SUCCESS` und beim Start der installierten App. Die Belege dieses strengeren Versuchs sind separat mit `native-off-` benannt.

## Belege und reproduzierbarer Code

- [Phone-Logcat](transport-evidence/phone-log.txt): Pairing, direkte/Relay-Installation, Hotspot-Portsuche, Channel-Übertragungen und Relay-Shell.
- [Watch-Logcat](transport-evidence/watch-log.txt): Installation ohne WLAN, Abbruch, Erstinstallation, erwarteter lokaler ADB-Fehler und spätere Relay-Shell.
- [Hotspot-Uhr-Netzwerk](transport-evidence/hotspot-watch-network.txt), [Hotspot-Handy-Interface](transport-evidence/hotspot-phone-network.txt).
- [WLAN aus am Handy](transport-evidence/no-wifi-phone.txt), [WLAN aus und Wireless Debugging 0 auf der Uhr](transport-evidence/no-wifi-watch.txt).
- [Relay: Handy-WLAN aus](transport-evidence/relay-phone-no-wifi.txt), [Relay: Uhr-WLAN an](transport-evidence/relay-watch-wifi.txt).
- [Installierte APK-Prüfsumme](transport-evidence/installed-apk-sha256.txt), [regulär erteilte Installationsquellen-Erlaubnis](transport-evidence/package-source-permission.txt).
- [Installationsbestätigung auf der Uhr](transport-evidence/package-confirmation.png), [gestartete, ohne WLAN installierte App](transport-evidence/no-wifi-installed-app.png).

Logcat hat begrenzte Ringpuffer. Der finale Watch-Auszug enthält die späteren Versuche ab 21:06:55; ältere Beobachtungen sind im Ablauf oben und, soweit vorhanden, im Phone-Log dokumentiert.

## Abschlussprüfung und Aufräumen

- `./gradlew -PtransportProbe :transport-probe:assembleDebug :transport-probe:lintPhoneDebug :transport-probe:lintWatchDebug` erfolgreich. Keine Lint-Fehler; Warnungen betreffen unter anderem vorhandene Abhängigkeiten, Backup-Regeln und die Laboroberfläche einschließlich des statischen Verweises für Statusmeldungen.
- Finale Phone-/Watch-APKs auf den Emulatoren installiert. Der strenge Schlussdurchlauf oben ist mit genau diesen Varianten erfolgt. Eine erste Lint-Prüfung führte zur Trennung in Phone-/Watch-Varianten und zu API-Guards für API 31/33; diese Änderungen wurden anschließend erneut gebaut, gelintet und praktisch geprüft.
- Der reguläre Projektaufruf ohne `-PtransportProbe` enthält weiterhin ausschließlich `common`, `libadb`, `mobile`, `test-apk`, `wear`; `./gradlew projects` erfolgreich.
- Labor-APKs und eigene Test-Payload deinstalliert, Test-Hotspot-Netzwerk von der Uhr entfernt, Test-Forward/Reverse entfernt. WLAN auf beiden Geräten wieder aktiviert, Wireless Debugging auf der Uhr auf den ursprünglichen ausgeschalteten Zustand zurückgesetzt.
- Beide Emulatoren gestoppt, Pool-Reservierungen freigegeben, Device-Panel-Einträge geschlossen und die vorübergehende Aufnahme des vorhandenen Watch-AVD in den Pool zurückgenommen.

Zusätzliche finale Belege:

- [Strenger Watch-Durchlauf](transport-evidence/native-off-watch-log.txt), [Phone-Durchlauf](transport-evidence/native-off-phone-log.txt).
- [Uhr vor Beginn](transport-evidence/native-off-watch-before.txt), [vor Bestätigung](transport-evidence/native-off-watch-before-confirm.txt), [nach Erfolg](transport-evidence/native-off-watch-after.txt), [mit gestarteter Test-App](transport-evidence/native-off-watch-app-running.txt).
- [Handy vor Beginn](transport-evidence/native-off-phone-before.txt), [nach Erfolg](transport-evidence/native-off-phone-after.txt).
- [Prüfsumme der abschließend installierten APK](transport-evidence/native-off-installed-apk-sha256.txt).
- [Gegencheck mit automatischer WLAN-Reaktivierung](transport-evidence/final-watch-network-before.txt), [Zustand nach diesem Gegencheck](transport-evidence/final-watch-network-after.txt). Diese Dateien dokumentieren ausdrücklich den schwächeren Versuch vor der nativen WLAN-aus-Prüfung.

## Quellen für Plattformverhalten

- [Google: Wear Data Layer und ChannelClient](https://developer.android.com/training/wearables/data/client-types)
- [Google: PackageInstaller und Nutzerbestätigung](https://developer.android.com/reference/android/content/pm/PackageInstaller)
- [Google: Wireless Debugging und mobile Hotspots](https://developer.android.com/training/wearables/get-started/debug-wifi)
- [Google: Emulator-Kopplung](https://developer.android.com/training/wearables/get-started/connect-phone)
- [Google: Emulator-Netzwerke und simulierte Funkstrecken](https://developer.android.com/studio/run/emulator-networking-advanced)
