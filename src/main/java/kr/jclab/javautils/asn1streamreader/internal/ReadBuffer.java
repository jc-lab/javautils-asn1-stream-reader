package kr.jclab.javautils.asn1streamreader.internal;

import java.io.IOException;
import java.util.ArrayList;

public interface ReadBuffer {
    boolean isNonBlocking();
    int available() throws IOException;
    boolean available(int length) throws IOException;
    byte readByte() throws IOException;
    void readBufferTo(byte[] buffer) throws IOException;
    void readBufferTo(ArrayList<Byte> list, int length) throws IOException;
    void setAfterReadHandler(ReadBufferAfterReadHandler handler);
}
