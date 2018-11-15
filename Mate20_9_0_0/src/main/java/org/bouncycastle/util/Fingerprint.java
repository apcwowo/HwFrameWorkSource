package org.bouncycastle.util;

import org.bouncycastle.crypto.digests.SHA512tDigest;
import org.bouncycastle.crypto.tls.CipherSuite;

public class Fingerprint {
    private static char[] encodingTable = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private final byte[] fingerprint;

    public Fingerprint(byte[] bArr) {
        this.fingerprint = calculateFingerprint(bArr);
    }

    public static byte[] calculateFingerprint(byte[] bArr) {
        SHA512tDigest sHA512tDigest = new SHA512tDigest((int) CipherSuite.TLS_DH_RSA_WITH_AES_128_GCM_SHA256);
        sHA512tDigest.update(bArr, 0, bArr.length);
        bArr = new byte[sHA512tDigest.getDigestSize()];
        sHA512tDigest.doFinal(bArr, 0);
        return bArr;
    }

    public boolean equals(Object obj) {
        return obj == this ? true : obj instanceof Fingerprint ? Arrays.areEqual(((Fingerprint) obj).fingerprint, this.fingerprint) : false;
    }

    public byte[] getFingerprint() {
        return Arrays.clone(this.fingerprint);
    }

    public int hashCode() {
        return Arrays.hashCode(this.fingerprint);
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i != this.fingerprint.length; i++) {
            if (i > 0) {
                stringBuffer.append(":");
            }
            stringBuffer.append(encodingTable[(this.fingerprint[i] >>> 4) & 15]);
            stringBuffer.append(encodingTable[this.fingerprint[i] & 15]);
        }
        return stringBuffer.toString();
    }
}