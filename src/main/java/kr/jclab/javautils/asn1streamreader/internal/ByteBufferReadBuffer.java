package kr.jclab.javautils.asn1streamreader.internal;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ByteBufferReadBuffer implements ReadBuffer {
    private final ByteBuffer backendBuffer;
    private ReadBufferAfterReadHandler afterReadHandler = null;

    public void setAfterReadHandler(ReadBufferAfterReadHandler handler) {
        this.afterReadHandler = handler;
    }

    public ByteBufferReadBuffer(ByteBuffer backendBuffer) {
        this.backendBuffer = backendBuffer;
    }

    @Override
    public boolean isNonBlocking() {
        return true;
    }

    @Override
    public int available() {
        return backendBuffer.remaining();
    }

    @Override
    public boolean available(int length) {
        return backendBuffer.remaining() >= length;
    }

    @Override
    public byte readByte() {
        byte value = backendBuffer.get();
        if(afterReadHandler != null) {
            afterReadHandler.afterReadHandler(1);
        }
        return value;
    }

    @Override
    public void readBufferTo(byte[] buffer) {
        backendBuffer.get(buffer);
        if(afterReadHandler != null) {
            afterReadHandler.afterReadHandler(buffer.length);
        }
    }

    @Override
    public void readBufferTo(ArrayList<Byte> list, int length) {
        byte[] buffer = new byte[length];
        this.readBufferTo(buffer);
        list.ensureCapacity(list.size() + buffer.length);
        for(byte b : buffer) {
            list.add(b);
        }
    }
}
