package kr.jclab.javautils.asn1streamreader.internal;

@FunctionalInterface
public interface ReadBufferAfterReadHandler {
    void afterReadHandler(int length);
}
