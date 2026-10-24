package pe.rutafija.backup;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class AuthenticatedBackupArchiveTest {
    private final AuthenticatedBackupArchive archive=new AuthenticatedBackupArchive();
    private byte[] encrypt(byte[] content,byte[] key)throws Exception {
        var output=new ByteArrayOutputStream();archive.encrypt(new ByteArrayInputStream(content),output,key);return output.toByteArray();
    }
    @Test void authenticatesFullPostgresStreamWithoutPersistingPlaintext()throws Exception{
        var key=AuthenticatedBackupArchive.newKey();byte[] content="PGDMPSYNTHETIC-DATA".getBytes(StandardCharsets.US_ASCII);
        byte[] encrypted=encrypt(content,key);
        assertThat(new String(encrypted,StandardCharsets.ISO_8859_1)).doesNotContain("SYNTHETIC-DATA");
        assertThat(archive.verify(new ByteArrayInputStream(encrypted),key,true)).isEqualTo(content.length);
    }
    @Test void wrongKeyNeverPasses()throws Exception{
        byte[] encrypted=encrypt("PGDMPsynthetic".getBytes(),AuthenticatedBackupArchive.newKey());
        assertThatThrownBy(()->archive.verify(new ByteArrayInputStream(encrypted),AuthenticatedBackupArchive.newKey(),true)).isInstanceOf(IOException.class);
    }
    @Test void corruptedTagNeverPasses()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),encrypted=encrypt("PGDMPsynthetic".getBytes(),key);encrypted[encrypted.length-1]^=1;
        assertThatThrownBy(()->archive.verify(new ByteArrayInputStream(encrypted),key,true)).isInstanceOf(IOException.class);
    }
    @Test void truncatedArchiveNeverPasses()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),encrypted=encrypt("PGDMPsynthetic".getBytes(),key);
        assertThatThrownBy(()->archive.verify(new ByteArrayInputStream(Arrays.copyOf(encrypted,encrypted.length-5)),key,true)).isInstanceOf(IOException.class);
    }
    @Test void wrongMagicIsRejected()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),encrypted=encrypt("PGDMPsynthetic".getBytes(),key);encrypted[0]^=1;
        assertThatThrownBy(()->archive.verify(new ByteArrayInputStream(encrypted),key,true)).hasMessage("BACKUP_HEADER_INVALID");
    }
    @Test void rejectsAuthenticatedNonPostgresContent()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),encrypted=encrypt("NOT_A_POSTGRES_DUMP".getBytes(),key);
        assertThatThrownBy(()->archive.verify(new ByteArrayInputStream(encrypted),key,true)).hasMessage("BACKUP_DUMP_FORMAT_INVALID");
    }
    @Test void equalPlaintextsUseDifferentNonces()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),content="PGDMPsynthetic".getBytes();
        assertThat(encrypt(content,key)).isNotEqualTo(encrypt(content,key));
    }
    @Test void refusesShortOrMissingKey(){
        assertThatThrownBy(()->archive.encrypt(InputStream.nullInputStream(),OutputStream.nullOutputStream(),new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void returnsRestoreBytesOnlyAfterFullAuthentication()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),content="PGDMPsynthetic".getBytes();
        byte[] encrypted=encrypt(content,key);
        assertThat(archive.authenticatedPostgresBytes(new ByteArrayInputStream(encrypted),key,1024)).isEqualTo(content);
        encrypted[encrypted.length-1]^=1;
        assertThatThrownBy(()->archive.authenticatedPostgresBytes(new ByteArrayInputStream(encrypted),key,1024)).isInstanceOf(IOException.class);
    }
    @Test void restoreBytesRefuseUnboundedMemoryOrOversizedDump()throws Exception{
        byte[] key=AuthenticatedBackupArchive.newKey(),encrypted=encrypt("PGDMPsynthetic".getBytes(),key);
        assertThatThrownBy(()->archive.authenticatedPostgresBytes(new ByteArrayInputStream(encrypted),key,5)).hasMessage("BACKUP_MEMORY_LIMIT_EXCEEDED");
        assertThatThrownBy(()->archive.authenticatedPostgresBytes(new ByteArrayInputStream(encrypted),key,65*1024*1024)).isInstanceOf(IllegalArgumentException.class);
    }
}
