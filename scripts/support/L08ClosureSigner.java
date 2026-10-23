import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;

/** Technical local-candidate signature, never a legal titleholder identity. */
public final class L08ClosureSigner {
    private static final String ALIAS = "rutafija-candidato-local";

    public static void main(String[] args) throws Exception {
        if (args.length == 6 && "sign".equals(args[0])) {
            sign(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
                    Path.of(args[4]), Path.of(args[5]));
        } else if (args.length == 4 && "verify".equals(args[0])) {
            verify(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        } else {
            throw new IllegalArgumentException("EXPECTED_SIGN_OR_VERIFY_ARGUMENTS_NO_SECRET_VALUES");
        }
    }

    private static void sign(Path input, Path store, Path passwordFile,
                             Path signatureFile, Path publicCertificate) throws Exception {
        char[] password = Files.readString(passwordFile).toCharArray();
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (var stream = Files.newInputStream(store)) {
                keyStore.load(stream, password);
            }
            PrivateKey key = (PrivateKey) keyStore.getKey(ALIAS, password);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(ALIAS);
            certificate.checkValidity();
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(Files.readAllBytes(input));
            Files.write(signatureFile, signer.sign(), StandardOpenOption.CREATE_NEW);
            Files.write(publicCertificate, certificate.getEncoded(), StandardOpenOption.CREATE_NEW);
            System.out.println("L08_CLOSURE_SIGNED=TECHNICAL_AUTOMATED_LOCAL_CANDIDATE");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void verify(Path input, Path signatureFile, Path publicCertificate) throws Exception {
        X509Certificate certificate;
        try (var stream = Files.newInputStream(publicCertificate)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
        }
        certificate.checkValidity();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        byte[] content = Files.readAllBytes(input);
        verifier.update(content);
        boolean valid = verifier.verify(Files.readAllBytes(signatureFile));
        System.out.println("L08_CLOSURE_SIGNATURE_VALID=" + valid);
        System.out.println("L08_CLOSURE_DOCUMENT_SHA256=" + sha256(content));
        System.out.println("L08_CLOSURE_CERTIFICATE_SHA256=" + sha256(certificate.getEncoded()));
        if (!valid) System.exit(3);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
