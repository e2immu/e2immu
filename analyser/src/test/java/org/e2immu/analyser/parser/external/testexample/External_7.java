package org.e2immu.analyser.parser.external.testexample;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.KeySpec;

public class External_7 {

    public static final String S1 = "s1";
    public static final String K = "key";
    public final KeySpec keySpec;
    public final SecretKeyFactory keyFactory;
    public final Cipher cipher;
    private static final String UNICODE_FORMAT = "UTF8";

    public External_7(String encryptionScheme) {
        this(encryptionScheme, K);
    }

    public External_7(String scheme, String key) {
        try {
            if (scheme.equals(S1)) {
                byte[] keyAsBytes = key.getBytes(UNICODE_FORMAT);
                keySpec = new DESKeySpec(keyAsBytes);
            } else {
                keySpec = null;
            }
            keyFactory = SecretKeyFactory.getInstance(scheme);
            cipher = Cipher.getInstance(scheme);
        } catch (InvalidKeyException | UnsupportedEncodingException | NoSuchAlgorithmException |
                 NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }
}
