package kr.jclab.javautils.asn1streamreader;

import java.util.Arrays;

public class Asn1ReadResult {
    public enum ReadType {
        CLOSE,
        EOF,
        BEGIN_SEQUENCE,
        END_SEQUENCE,
        OBJECT
    }

    private final byte[] rawBuffer;
    private final ReadType readType;
    private final Object object;

    public Asn1ReadResult(byte[] rawBuffer, ReadType readType, Object object) {
        this.rawBuffer = rawBuffer;
        this.readType = readType;
        this.object = object;
    }

    public byte[] getRawBuffer() {
        return this.rawBuffer;
    }

    public ReadType getReadType() {
        return readType;
    }

    public Object getObject() {
        return object;
    }

    @Override
    public String toString() {
        return "Asn1ReadResult{" +
                "rawBuffer=" + Arrays.toString(rawBuffer) +
                ", readType=" + readType +
                ", object=" + object +
                '}';
    }
}
