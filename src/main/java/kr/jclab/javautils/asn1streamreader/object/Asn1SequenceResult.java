package kr.jclab.javautils.asn1streamreader.object;

public class Asn1SequenceResult {
    private final boolean ber;
    private final int size;

    public Asn1SequenceResult(boolean ber, int size) {
        this.ber = ber;
        this.size = size;
    }

    public boolean isBer() {
        return ber;
    }

    public int getSize() {
        return size;
    }
}
