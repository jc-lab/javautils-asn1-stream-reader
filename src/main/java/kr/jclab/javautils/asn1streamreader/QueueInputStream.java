package kr.jclab.javautils.asn1streamreader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public class QueueInputStream extends InputStream {
    private final LinkedBlockingDeque<ByteBuffer> queue = new LinkedBlockingDeque<>();
    private final AtomicLong queuedBytes = new AtomicLong(0);

    public QueueInputStream() {
        super();
    }

    public boolean offer(byte[] b, int off, int len) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(b, off, len);
        if(this.queue.offerLast(byteBuffer)) {
            this.queuedBytes.addAndGet(byteBuffer.remaining());
            return true;
        }
        return false;
    }

    public boolean offer(byte[] b) {
        return this.offer(b, 0, b.length);
    }

    public void put(byte[] b, int off, int len) throws InterruptedException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(b, off, len);
        this.queue.putLast(byteBuffer);
        this.queuedBytes.addAndGet(byteBuffer.remaining());
    }

    public void put(byte[] b) throws InterruptedException {
        this.put(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        try {
            synchronized (this) {
                ByteBuffer byteBuffer = this.queue.takeFirst();
                int value = byteBuffer.get() & 0xff;
                this.queuedBytes.decrementAndGet();
                if(byteBuffer.hasRemaining()) {
                    this.queue.putFirst(byteBuffer);
                }
                return value;
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            ByteBuffer outputBuffer = ByteBuffer.wrap(b, off, len);
            synchronized (this) {
                while(outputBuffer.hasRemaining()) {
                    ByteBuffer byteBuffer = this.queue.takeFirst();
                    int readBytes = copyBuffer(byteBuffer, outputBuffer, outputBuffer.remaining());
                    this.queuedBytes.addAndGet(-readBytes);
                    if(byteBuffer.hasRemaining()) {
                        this.queue.putFirst(byteBuffer);
                    }
                }
                outputBuffer.flip();
                return outputBuffer.remaining();
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        long available = this.queuedBytes.get();
        if(available > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int)available;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private static int copyBuffer(ByteBuffer src, ByteBuffer dest, int length) {
        int srcRemaining = src.remaining();
        int available = (srcRemaining > length) ? length : srcRemaining;
        byte[] buffer = new byte[available];
        src.get(buffer);
        dest.put(buffer);
        return available;
    }
}
