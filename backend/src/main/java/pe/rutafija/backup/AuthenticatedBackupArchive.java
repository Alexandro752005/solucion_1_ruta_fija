package pe.rutafija.backup;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

/** Versioned authenticated archive. Keys, plaintext and content are never logged. */
public final class AuthenticatedBackupArchive {
    private static final byte[] MAGIC = "RFDB-GCM-01\n".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;

    public static byte[] newKey() { byte[] key=new byte[32]; new SecureRandom().nextBytes(key); return key; }

    public void encrypt(InputStream plaintext, OutputStream destination, byte[] key) throws IOException, GeneralSecurityException {
        valid(key);
        byte[] nonce=new byte[NONCE_BYTES]; new SecureRandom().nextBytes(nonce);
        destination.write(MAGIC); destination.write(nonce);
        Cipher cipher=cipher(Cipher.ENCRYPT_MODE,key,nonce);
        try (CipherOutputStream encrypted=new CipherOutputStream(destination,cipher)) { plaintext.transferTo(encrypted); }
    }

    /** Returns a byte count only after the complete GCM tag is authenticated. */
    public long verify(InputStream archive, byte[] key, boolean requirePostgresDump) throws IOException, GeneralSecurityException {
        byte[] plaintext=authenticatedBytes(archive,key,64*1024*1024,requirePostgresDump);
        try{return plaintext.length;}finally{Arrays.fill(plaintext,(byte)0);}
    }

    /** Small isolated-test restores only: no byte is returned until GCM authentication completes. */
    public byte[] authenticatedPostgresBytes(InputStream archive, byte[] key, int maximumBytes) throws IOException, GeneralSecurityException {
        return authenticatedBytes(archive,key,maximumBytes,true);
    }

    private byte[] authenticatedBytes(InputStream archive,byte[] key,int maximumBytes,boolean requirePostgresDump)throws IOException,GeneralSecurityException{
        valid(key);
        if(maximumBytes<5||maximumBytes>64*1024*1024)throw new IllegalArgumentException("BACKUP_MEMORY_LIMIT_INVALID");
        byte[] header=archive.readNBytes(MAGIC.length),nonce=archive.readNBytes(NONCE_BYTES);
        if(!MessageDigest.isEqual(header,MAGIC)||nonce.length!=NONCE_BYTES)throw new IOException("BACKUP_HEADER_INVALID");
        byte[] encrypted=archive.readNBytes(maximumBytes+17);
        if(encrypted.length>maximumBytes+16)throw new IOException("BACKUP_MEMORY_LIMIT_EXCEEDED");
        byte[] result;
        try{
            // Use Cipher.doFinal explicitly: CipherInputStream is not the authentication
            // boundary for GCM (Java21 API warns it may suppress integrity failures).
            result=cipher(Cipher.DECRYPT_MODE,key,nonce).doFinal(encrypted);
        }catch(javax.crypto.AEADBadTagException invalid){throw new IOException("BACKUP_AUTHENTICATION_FAILED");}
        finally{Arrays.fill(encrypted,(byte)0);}
        if(requirePostgresDump&&(result.length<5||!Arrays.equals(Arrays.copyOf(result,5),"PGDMP".getBytes(StandardCharsets.US_ASCII)))){
            Arrays.fill(result,(byte)0);throw new IOException("BACKUP_DUMP_FORMAT_INVALID");
        }
        return result;
    }

    private Cipher cipher(int mode,byte[] key,byte[] nonce) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(MAGIC); return cipher;
    }
    private void valid(byte[] key) { if(key==null||key.length!=32)throw new IllegalArgumentException("BACKUP_256_BIT_KEY_REQUIRED"); }
}
