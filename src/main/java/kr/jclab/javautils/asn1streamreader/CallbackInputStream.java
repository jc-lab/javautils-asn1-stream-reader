package kr.jclab.javautils.asn1streamreader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class CallbackInputStream extends InputStream {
    private final LinkedList<ByteBuffer> queue = new LinkedList<>();

    public interface Callbacks {
        void onData(ByteBuffer buffer);
        void onClose();
    }

    private Callbacks callbacks = null;
    private boolean closed = false;

    public CallbackInputStream() { }

    public void setCallbacks(Callbacks callbacks) throws IOException {
        if (this.queue.isEmpty() && this.closed) {
            throw new IOException("Already closed stream");
        }
        this.callbacks = callbacks;
        if(callbacks != null) {
            for (ByteBuffer b : this.queue) {
                callbacks.onData(b);
            }
            if(this.closed) {
                callbacks.onClose();
                this.callbacks = null;
            }
            this.queue.clear();
        }
    }

    public void write(byte b) throws IOException {
        if (this.closed) {
            throw new IOException("Already closed stream");
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(1);
        byteBuffer.put(b);
        byteBuffer.flip();
        if (this.callbacks != null) {
            this.callbacks.onData(byteBuffer);
        }else{
            this.queue.add(byteBuffer);
        }
    }

    public void write(byte[] b, int offset, int length) throws IOException {
        if (this.closed) {
            throw new IOException("Already closed stream");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(b, offset, length);
        if (this.callbacks != null) {
            this.callbacks.onData(byteBuffer);
        }else{
            this.queue.add(byteBuffer);
        }
    }

    public void write(byte[] b) throws IOException {
        if (this.closed) {
            throw new IOException("Already closed stream");
        }

        this.write(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Can not use");
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        if(this.callbacks != null) {
            this.callbacks.onClose();
            this.callbacks = null;
        }
    }
}
