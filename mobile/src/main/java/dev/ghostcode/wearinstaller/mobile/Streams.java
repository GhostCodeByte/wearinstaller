package dev.ghostcode.wearinstaller.mobile;
import java.io.*;
final class Streams {
    static byte[] all(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); copy(in,out); return out.toByteArray();
    }
    static void copy(InputStream in,OutputStream out) throws IOException {
        byte[] buffer=new byte[32768]; int count;
        while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
    }
    static byte[] exactly(InputStream in,int size) throws IOException {
        byte[] bytes=new byte[size]; new DataInputStream(in).readFully(bytes); return bytes;
    }
    private Streams() {}
}
