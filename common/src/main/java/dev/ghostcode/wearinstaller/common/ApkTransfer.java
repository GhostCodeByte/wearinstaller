package dev.ghostcode.wearinstaller.common;

import java.io.*;
import java.security.*;

/** Versioned, bounded stream protocol. A digest is verified before the installer may commit. */
public final class ApkTransfer {
    public static final long MAX_SIZE=512L*1024*1024;
    private static final int MAGIC=0x57494150;
    public interface Progress { void bytes(long completed,long total); }
    public record Header(long size,byte[] digest,String name) {}
    public static byte[] digest(File file) throws IOException {
        MessageDigest hash=sha256();
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536]; int n;
            while((n=in.read(buffer))!=-1) hash.update(buffer,0,n);
        }
        return hash.digest();
    }
    public static void writeHeader(DataOutputStream out,Header header) throws IOException {
        validate(header);
        out.writeInt(MAGIC); out.writeInt(1); out.writeLong(header.size());
        out.write(header.digest()); out.writeUTF(header.name());
    }
    public static Header readHeader(DataInputStream in) throws IOException {
        if(in.readInt()!=MAGIC || in.readInt()!=1) throw new IOException("Übertragungsformat nicht unterstützt. Aktualisiere beide Apps.");
        long size=in.readLong(); byte[] hash=new byte[32]; in.readFully(hash);
        Header header=new Header(size,hash,in.readUTF()); validate(header); return header;
    }
    private static void validate(Header h) throws IOException {
        if(h.size()<=0 || h.size()>MAX_SIZE || h.digest().length!=32 || h.name().length()>240)
            throw new IOException("Ungültige APK-Größe oder Übertragungsdaten (maximal 512 MiB).");
    }
    public static void copyVerified(InputStream in,OutputStream out,Header h,Progress progress) throws IOException {
        validate(h); MessageDigest hash=sha256(); byte[] buffer=new byte[65536]; long done=0;
        while(done<h.size()) {
            int n=in.read(buffer,0,(int)Math.min(buffer.length,h.size()-done));
            if(n<0) throw new EOFException("APK-Übertragung unterbrochen.");
            if(n==0) continue;
            out.write(buffer,0,n); hash.update(buffer,0,n); done+=n; progress.bytes(done,h.size());
        }
        if(!MessageDigest.isEqual(hash.digest(),h.digest())) throw new IOException("APK-Prüfsumme stimmt nicht überein. Datei erneut auswählen.");
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static boolean validRequest(String id) {
        return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    private ApkTransfer() {}
}
