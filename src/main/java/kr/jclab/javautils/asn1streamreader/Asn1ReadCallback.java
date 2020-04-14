package kr.jclab.javautils.asn1streamreader;

@FunctionalInterface
public interface Asn1ReadCallback {
    void onData(Asn1ReadResult result);
}
