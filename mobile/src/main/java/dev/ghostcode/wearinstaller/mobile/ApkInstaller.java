package dev.ghostcode.wearinstaller.mobile;

import io.github.muntashirakon.adb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class ApkInstaller {
    interface Progress { void update(int percent,String stage); }
    private final AbsAdbConnectionManager adb;
    ApkInstaller(AbsAdbConnectionManager adb) { this.adb=adb; }
    void install(File apk, Progress progress) throws Exception {
        long size=apk.length();
        if(size==0) throw new IOException("Die APK-Datei ist leer.");
        // exec: is a raw duplex stream; package reads exactly -S bytes from stdin.
        String service="exec:cmd package install -r -S "+size
                +"; result=$?; echo __WEARINSTALLER_EXIT:$result";
        try(InputStream file=new FileInputStream(apk); AdbStream stream=adb.openStream(service)) {
            transfer(size,file,stream.openOutputStream(),progress);
            progress.update(93,"Android installiert die APK …");
            checkInstallResult(readResponse(stream.openInputStream()));
            progress.update(100,"Erfolgreich auf der Uhr installiert ✓");
        }
    }
    static void transfer(long size,InputStream file,OutputStream out,Progress progress) throws IOException {
        if(size==0) throw new IOException("Die APK-Datei ist leer.");
        byte[] buf=new byte[256*1024]; long sent=0;
        DataInputStream input=new DataInputStream(file);
        long started=System.nanoTime(), lastUpdate=started;
        int lastPercent=-1;
        while(sent<size) {
            // Fill a block even if the input source returns short reads.
            int count=(int)Math.min(buf.length,size-sent);
            input.readFully(buf,0,count);
            out.write(buf,0,count); out.flush(); sent+=count;
            long now=System.nanoTime();
            int percent=(int)(sent*90/size);
            if(percent!=lastPercent || now-lastUpdate>=250_000_000L) {
                double seconds=Math.max(.001,(now-started)/1_000_000_000.0);
                progress.update(percent,String.format(java.util.Locale.GERMANY,
                        "APK übertragen: %.1f / %.1f MiB · %.2f MiB/s …",
                        sent/1048576.0,size/1048576.0,sent/1048576.0/seconds));
                lastPercent=percent; lastUpdate=now;
            }
        }
        if(file.read()!=-1) throw new IOException("Die APK-Datei hat sich während der Übertragung geändert.");
    }
    private static String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1) { result.write(buf,0,n); if(result.size()>1024*1024) throw new IOException("Antwort der Uhr ist zu groß."); }
        return new String(result.toByteArray(),StandardCharsets.UTF_8);
    }
    static void checkInstallResult(String result) throws IOException {
        if(result.matches("(?s).*\\bSuccess\\b.*") && result.matches("(?s).*\\n?__WEARINSTALLER_EXIT:0\\s*")) return;
        String detail=result.replaceAll("__WEARINSTALLER_EXIT:[0-9]+","").trim();
        if(result.contains("INSTALL_FAILED_NO_MATCHING_ABIS")) detail="Die APK unterstützt den Prozessor dieser Uhr nicht.";
        else if(result.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE")) detail="Auf der Uhr ist nicht genügend Speicher frei.";
        else if(result.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) detail="Eine vorhandene App hat einen anderen Signing-Key. Deinstalliere sie zuerst auf der Uhr.";
        else if(result.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) detail="Auf der Uhr ist bereits eine neuere Version installiert.";
        else if(result.contains("INSTALL_FAILED_OLDER_SDK")) detail="Die APK benötigt eine neuere Android-Version auf der Uhr.";
        else if(result.contains("INSTALL_PARSE_FAILED") || result.contains("INSTALL_FAILED_INVALID_APK")
                || result.toLowerCase(java.util.Locale.ROOT).contains("failed to parse")
                || result.toLowerCase(java.util.Locale.ROOT).contains("corrupt")) detail="Die Datei ist keine gültige, eigenständig installierbare APK.";
        throw new IOException("Installation fehlgeschlagen. "+(detail.isEmpty()?"Keine Bestätigung von der Uhr erhalten.":detail));
    }
}
