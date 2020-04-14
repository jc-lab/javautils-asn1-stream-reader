package kr.jclab.javautils.asn1streamreader.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class InputStreamReadBuffer implements ReadBuffer {
    private final InputStream backendIn;
    private final boolean nonBlocking;
    private ReadBufferAfterReadHandler afterReadHandler = null;

    public void setAfterReadHandler(ReadBufferAfterReadHandler handler) {
        this.afterReadHandler = handler;
    }

    public InputStreamReadBuffer(InputStream backendIn, boolean nonBlocking) {
        this.backendIn = backendIn;
        this.nonBlocking = nonBlocking;
    }

    @Override
    public int available() throws IOException {
        return this.backendIn.available();
    }

    @Override
    public boolean available(int length) throws IOException {
        return !this.nonBlocking || this.available() >= length;
    }

    @Override
    public byte readByte() throws IOException {
        int value = this.backendIn.read();
        if(value < 0) {
            throw new EOFException();
        }
        if(afterReadHandler != null) {
            afterReadHandler.afterReadHandler(1);
        }
        return (byte)value;
    }

    @Override
    public void readBufferTo(byte[] buffer) throws IOException {
        int position = 0;
        int remaining;
        while((remaining = (buffer.length - position)) > 0) {
            int readLength = this.backendIn.read(buffer, position, remaining);
            if(readLength > 0) {
                position += readLength;
                if(afterReadHandler != null) {
                    afterReadHandler.afterReadHandler(readLength);
                }
            }else if(readLength < 0) {
                throw new EOFException();
            }
        }
    }

    @Override
    public void readBufferTo(ArrayList<Byte> list, int length) throws IOException {
        byte[] buffer = new byte[length];
        this.readBufferTo(buffer);
        list.ensureCapacity(list.size() + buffer.length);
        for(byte b : buffer) {
            list.add(b);
        }
    }
}
