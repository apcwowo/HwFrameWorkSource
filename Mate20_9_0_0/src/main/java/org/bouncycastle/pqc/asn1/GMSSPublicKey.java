package org.bouncycastle.pqc.asn1;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.util.Arrays;

public class GMSSPublicKey extends ASN1Object {
    private byte[] publicKey;
    private ASN1Integer version;

    private GMSSPublicKey(ASN1Sequence aSN1Sequence) {
        if (aSN1Sequence.size() == 2) {
            this.version = ASN1Integer.getInstance(aSN1Sequence.getObjectAt(0));
            this.publicKey = ASN1OctetString.getInstance(aSN1Sequence.getObjectAt(1)).getOctets();
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("size of seq = ");
        stringBuilder.append(aSN1Sequence.size());
        throw new IllegalArgumentException(stringBuilder.toString());
    }

    public GMSSPublicKey(byte[] bArr) {
        this.version = new ASN1Integer(0);
        this.publicKey = bArr;
    }

    public static GMSSPublicKey getInstance(Object obj) {
        return obj instanceof GMSSPublicKey ? (GMSSPublicKey) obj : obj != null ? new GMSSPublicKey(ASN1Sequence.getInstance(obj)) : null;
    }

    public byte[] getPublicKey() {
        return Arrays.clone(this.publicKey);
    }

    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector aSN1EncodableVector = new ASN1EncodableVector();
        aSN1EncodableVector.add(this.version);
        aSN1EncodableVector.add(new DEROctetString(this.publicKey));
        return new DERSequence(aSN1EncodableVector);
    }
}
