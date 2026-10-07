package dev.ghostcode.wearinstaller.mobile;

import io.github.muntashirakon.adb.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class ApkInstaller {
    interface Progress { void update(int percent,String stage); }
    private final AbsAdbConnectionManager adb;
    ApkInstaller(AbsAdbConnectionManager adb) { this.adb=adb; }
    void install(File apk, Progress progress) throws Exception {
        if(apk.length()==0) throw new IOException("Die APK-Datei ist leer.");
        String remote="/data/local/tmp/wearinstaller-"+UUID.randomUUID()+".apk";
        try {
            try(AdbStream stream=adb.openStream("sync:"); InputStream file=new FileInputStream(apk)) {
                OutputStream out=stream.openOutputStream(); InputStream in=stream.openInputStream();
                byte[] name=(remote+",33188").getBytes(StandardCharsets.UTF_8);
                packet(out,"SEND",name.length); write(out,name);
                byte[] buf=new byte[32768]; int count; long sent=0;
                while((count=file.read(buf))!=-1) {
                    packet(out,"DATA",count); write(out,buf,0,count); sent+=count;
                    progress.update((int)(sent*90/apk.length()),"APK auf die Uhr übertragen …");
                }
                packet(out,"DONE",(int)(System.currentTimeMillis()/1000)); out.flush();
                byte[] response=Streams.exactly(in,8);
                if(response.length!=8) throw new IOException("Die Uhr hat die Übertragung abgebrochen.");
                String type=new String(response,0,4,StandardCharsets.US_ASCII);
                int size=ByteBuffer.wrap(response,4,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if(!"OKAY".equals(type)) {
                    String error=size>0 && size<65536 ? new String(Streams.exactly(in,size),StandardCharsets.UTF_8) : type;
                    throw new IOException("Übertragung fehlgeschlagen: "+error);
                }
            }
            progress.update(93,"Android installiert die APK …");
            String result=shell("pm install -r '"+remote+"'; result=$?; echo __WEARINSTALLER_EXIT:$result");
            checkInstallResult(result);
            progress.update(100,"Erfolgreich auf der Uhr installiert ✓");
        } finally {
            if(adb.isConnected()) try { shell("rm -f '"+remote+"'"); } catch(Exception ignored) {}
        }
    }
    String shell(String command) throws Exception {
        try(AdbStream stream=adb.openStream("shell:"+command)) {
            ByteArrayOutputStream result=new ByteArrayOutputStream(); InputStream in=stream.openInputStream();
            byte[] buf=new byte[4096]; int n;
            while((n=in.read(buf))!=-1) { result.write(buf,0,n); if(result.size()>1024*1024) throw new IOException("Antwort der Uhr ist zu groß."); }
            return new String(result.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    static void checkInstallResult(String result) throws IOException {
        if(result.contains("__WEARINSTALLER_EXIT:0") && result.matches("(?s).*\\bSuccess\\b.*")) return;
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
    private static void packet(OutputStream out,String type,int value) throws IOException {
        ByteBuffer b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); b.put(type.getBytes(StandardCharsets.US_ASCII)); b.putInt(value); write(out,b.array());
    }
    private static void write(OutputStream out,byte[] data) throws IOException { write(out,data,0,data.length); }
    // Keep each WRTE below even legacy adbd's 4096-byte payload limit.
    private static void write(OutputStream out,byte[] data,int offset,int length) throws IOException {
        while(length>0) { int n=Math.min(4096,length); out.write(data,offset,n); out.flush(); offset+=n; length-=n; }
    }
}
