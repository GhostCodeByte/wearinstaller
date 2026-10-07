package dev.ghostcode.wearinstaller.mobile;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Exportable ADB RSA identity encrypted with a non-exportable Android Keystore AES key. */
final class AdbIdentity extends AbsAdbConnectionManager {
    private final PrivateKey key;
    private final Certificate certificate;
    AdbIdentity(Context context) throws Exception {
        setApi(30); setTimeout(12,TimeUnit.SECONDS); setThrowOnUnauthorised(true);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        String alias = "wearinstaller-adb-wrap-v1";
        if (!ks.containsAlias(alias)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        SecretKey wrappingKey = (SecretKey) ks.getKey(alias,null);
        AtomicFile file = new AtomicFile(new File(context.getNoBackupFilesDir(),"adb-identity-v1"));
        if (file.getBaseFile().exists()) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(file.readFully()));
            byte[] iv = new byte[in.readInt()]; in.readFully(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,wrappingKey,new GCMParameterSpec(128,iv));
            DataInputStream clear = new DataInputStream(new ByteArrayInputStream(cipher.doFinal(Streams.all(in))));
            byte[] privateBytes = new byte[clear.readInt()]; clear.readFully(privateBytes);
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Streams.all(clear)));
        } else {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048);
            java.security.KeyPair pair = gen.generateKeyPair(); key = pair.getPrivate();
            X500Name name = new X500Name("CN=Wear Installer");
            long now = System.currentTimeMillis();
            certificate = new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,
                    new BigInteger(128,new SecureRandom()),new Date(now - 86400000L),new Date(now + 10L*365*86400000L),name,pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(key)));
            ByteArrayOutputStream clearBytes = new ByteArrayOutputStream(); DataOutputStream clear = new DataOutputStream(clearBytes);
            clear.writeInt(key.getEncoded().length); clear.write(key.getEncoded()); clear.write(certificate.getEncoded());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,wrappingKey);
            FileOutputStream out = file.startWrite();
            try {
                DataOutputStream data = new DataOutputStream(out); data.writeInt(cipher.getIV().length); data.write(cipher.getIV());
                data.write(cipher.doFinal(clearBytes.toByteArray())); file.finishWrite(out);
            } catch (Exception e) { file.failWrite(out); throw e; }
        }
    }
    @Override protected PrivateKey getPrivateKey() { return key; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "WearInstaller@Android"; }
}
