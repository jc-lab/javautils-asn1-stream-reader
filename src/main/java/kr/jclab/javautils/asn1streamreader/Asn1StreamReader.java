package kr.jclab.javautils.asn1streamreader;

import kr.jclab.javautils.asn1streamreader.internal.ByteBufferReadBuffer;
import kr.jclab.javautils.asn1streamreader.internal.InputStreamReadBuffer;
import kr.jclab.javautils.asn1streamreader.internal.ReadBuffer;
import kr.jclab.javautils.asn1streamreader.object.Asn1SequenceResult;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Asn1StreamReader extends FilterInputStream {
    private Asn1ReaderOptions options;
    private Thread readThread = null;
    private AtomicBoolean readThreadRun = new AtomicBoolean();

    private boolean readingUsingCallback;
    private LinkedBlockingQueue<Asn1ReadResult> queue = new LinkedBlockingQueue<>();

    private boolean eof = false;

    private static Asn1ReaderOptions defaultOptions(Asn1ReaderOptions options) {
        if(options != null) {
            return options;
        }
        return Asn1ReaderOptions.builder().build();
    }

    public Asn1StreamReader(InputStream in) throws IOException {
        this(in, null);
    }

    // 가능한 상황
    // Case-1. CallbackInputStream을 이용해서 Callback으로 받는 경우
    // Case-2. QueueInputStream을 통해 read/write가 함께 이루어 지는 경우
    // Case-3. InputStream으로 Block되어 read되는 경우
    public Asn1StreamReader(InputStream in, Asn1ReaderOptions options) throws IOException {
        super(in);
        this.options = defaultOptions(options);
        if (in instanceof CallbackInputStream) {
            // Case-1
            this.readingUsingCallback = true;
            ((CallbackInputStream)in).setCallbacks(this.inputCallbacks);
        }else{
            // Case-2/3
            this.readingUsingCallback = false;
            if (this.options.getReadCallback() != null) {
                this.readThreadRun.set(true);
                this.readThread = new Thread(() -> {
                    InputStreamReadBuffer readBuffer = new InputStreamReadBuffer(in, false);
                    while (readThreadRun.get()) {
                        try {
                            try {
                                List<Asn1ReadResult> readResults = onData(readBuffer);
                                for(Asn1ReadResult item : readResults) {
                                    options.getReadCallback().onData(item);
                                }
                            } catch (IOException e) {
                                if (e.getCause() instanceof InterruptedException) {
                                    throw (InterruptedException)e.getCause();
                                }
                                e.printStackTrace();
                            }
                        } catch (InterruptedException intException) {
                            // Nothing
                        }
                    }
                });
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (this.options.getReadCallback() != null) {
            throw new IOException("Not support on callback mode");
        }
        return this.queue.size();
    }

    public boolean eof() {
        return this.eof && this.queue.isEmpty();
    }

    private void setEof() {
        Asn1ReadResult eofResult = new Asn1ReadResult(null, Asn1ReadResult.ReadType.EOF, null);
        this.eof = true;
        if(this.options.getReadCallback() != null) {
            this.options.getReadCallback().onData(eofResult);
        }else{
            try {
                this.queue.put(eofResult);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public Asn1ReadResult readObject(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        if (this.options.getReadCallback() != null) {
            throw new IOException("Not support on callback mode");
        }
        if (this.readingUsingCallback) {
            return this.queue.poll(timeout, unit);
        }else{
            Asn1ReadResult result;
            long timeoutNs = unit.toNanos(timeout);
            long sleepMs = (timeoutNs < 1000000) ? timeoutNs / 100000 : 100;
            long beginAt = System.nanoTime();
            while((result = this.readObject(true)) == null) {
                Thread.sleep(sleepMs);
                if ((System.nanoTime() - beginAt) >= timeoutNs)
                    break;
            }
            return result;
        }
    }

    public Asn1ReadResult readObject(boolean nonBlocking) throws IOException {
        if (!this.queue.isEmpty()) {
            return this.queue.poll();
        }
        if (this.readingUsingCallback) {
            if(!nonBlocking) {
                try {
                    return this.queue.take();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }else{
            List<Asn1ReadResult> readResults = onData(new InputStreamReadBuffer(this.in, nonBlocking));
            try {
                for(int i=1; i<readResults.size(); i++) {
                    this.queue.put(readResults.get(i));
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return readResults.isEmpty() ? null : readResults.get(0);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (this.readThread != null) {
            this.readThreadRun.set(false);
            this.readThread.interrupt();
            try {
                this.readThread.join();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        this.in.close();
    }

    private final CallbackInputStream.Callbacks inputCallbacks = new CallbackInputStream.Callbacks() {
        @Override
        public void onData(ByteBuffer buffer) {
            ByteBufferReadBuffer readBuffer = new ByteBufferReadBuffer(buffer);
            try {
                while (readBuffer.available() > 0) {
                    List<Asn1ReadResult> readResults = Asn1StreamReader.this.onData(readBuffer);
                    if (options.getReadCallback() != null) {
                        for(Asn1ReadResult item : readResults) {
                            options.getReadCallback().onData(item);
                        }
                    } else {
                        queue.addAll(readResults);
                    }

                }
            } catch(IOException e) {
                if(e instanceof EOFException) {
                    setEof();
                }
                e.printStackTrace();
            }
        }

        @Override
        public void onClose() {
            setEof();
        }
    };

    private final LinkedList<ParseContext> parseContextStack = new LinkedList<>();

    private boolean _checkEmitableData(ParseContext parseContext) {
        if (this.options.isStripSequence()) {
            return parseContext.getDepth() == 1;
        } else {
            return parseContext.getDepth() == 0;
        }
    }

    private ArrayList<Byte> getTagBuffer(ParseContext parseContext) {
        if ((this.options.isStripSequence() && (parseContext.depth <= 1)) || (parseContext.depth == 0)) {
            return parseContext.tagBuffer;
        }else {
            return this.getTagBuffer(parseContext.parent);
        }
    }

    private static <T> void addIfNotNull(List<T> list, T item) {
        if(item != null) {
            list.add(item);
        }
    }

    private List<Asn1ReadResult> onData(ReadBuffer readBuffer) throws IOException {
        List<Asn1ReadResult> readResults = new ArrayList<>(2);
        while (readBuffer.available() > 0 && readResults.isEmpty()) {
            if (this.parseContextStack.size() == 0) {
                this.parseContextStack.addLast(new ParseContext(null));
            }

            final ParseContext parseContext = this.parseContextStack.getLast();
            try {
                readBuffer.setAfterReadHandler(parseContext::decrementTotalRemaining);
                switch (parseContext.step) {
                    case READ_TAG_BEGIN:
                        if (readBuffer.available(1)) {
                            byte buf = readBuffer.readByte();
                            parseContext.tagBuffer.clear();
                            getTagBuffer(parseContext).add(buf);
                            parseContext.tagClass = buf >>> 6;
                            parseContext.tagConstructed = ((buf & 0x20) != 0);
                            parseContext.tagNumber = buf & 0x1F;
                            parseContext.tagTempInt10 = 0;
                            parseContext.tagWrittenLength = 0;
                            if (parseContext.tagNumber == 0x1F) {
                                parseContext.step = ParseContext.ParseStep.READ_TAG_LONG;
                            } else {
                                parseContext.step = ParseContext.ParseStep.READ_TAG_LENGTH;
                                break;
                            }
                        } else {
                            break;
                        }

                    case READ_TAG_LONG:
                        if (readBuffer.available(1)) {
                            byte buf;
                            do {
                                buf = readBuffer.readByte();
                                getTagBuffer(parseContext).add(buf);
                                parseContext.tagTempInt10 = (parseContext.tagTempInt10 << 7) | (buf & 0x7F);
                            } while ((readBuffer.available(1)) && ((buf & 0x80) != 0));
                            if ((buf & 0x80) == 0) {
                                parseContext.tagNumber = parseContext.tagTempInt10;
                                parseContext.step = ParseContext.ParseStep.READ_TAG_LENGTH;
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }

                    case READ_TAG_LENGTH:
                        if (readBuffer.available(1)) {
                            byte buf = readBuffer.readByte();
                            int len = buf & 0x7F;

                            getTagBuffer(parseContext).add(buf);

                            if (parseContext.tagNumber == 0 && len == 0) {
                                readResults.addAll(tagReadDone(parseContext, null));
                                break;
                            }

                            if (buf == len) {
                                if (parseContext.depth == 0 && this.options.isStripSequence()) {
                                    parseContext.step = ParseContext.ParseStep.READ_TAG_CONTENT;
                                } else {
                                    parseContext.step = ParseContext.ParseStep.READ_TAG_CONTENT_FIXED_LENGTH;
                                }
                                parseContext.tagLength = len;
                                parseContext.totalRemaining = parseContext.tagLength;
                                addIfNotNull(readResults, this.tagReadPrepare(parseContext));
                                break;
                            }
                            if (len > 6) {
                                throw new IOException("Length over 48 bits not supported at position ?");
                                // this.position
                            }
                            if (len == 0) {
                                parseContext.tagLenSize = -1;
                                parseContext.tagLenRemaining = -1;
                                parseContext.step = ParseContext.ParseStep.READ_TAG_CONTENT;
                                addIfNotNull(readResults, this.tagReadPrepare(parseContext));
                                break;
                            }
                            parseContext.tagLenRemaining = len;
                            parseContext.tagTempInt10 = 0;
                            parseContext.step = ParseContext.ParseStep.READ_TAG_LENGTH_LONG;
                        } else {
                            break;
                        }

                    case READ_TAG_LENGTH_LONG:
                        if (readBuffer.available() > 0) {
                            while ((readBuffer.available() > 0) && (parseContext.tagLenRemaining > 0)) {
                                byte buf = readBuffer.readByte();
                                getTagBuffer(parseContext).add(buf);
                                parseContext.tagTempInt10 = (parseContext.tagTempInt10 << 8) | (buf & 0xFF);
                                parseContext.tagLenRemaining--;
                            }
                            if (parseContext.tagLenRemaining == 0) {
                                parseContext.tagLength = parseContext.tagTempInt10;
                                parseContext.tagTotalLength = parseContext.tagTotalReadLength + parseContext.tagLength;
                                parseContext.totalRemaining = parseContext.tagLength;
                                parseContext.step = ParseContext.ParseStep.READ_TAG_CONTENT_FIXED_LENGTH;
                                addIfNotNull(readResults, this.tagReadPrepare(parseContext));
                            }
                        }
                        break;

                    case READ_TAG_CONTENT:
                        if (parseContext.tagConstructed) {
                            ParseContext subParseContext = new ParseContext(parseContext);
                            parseContextStack.addLast(subParseContext);
                        } else if (parseContext.tagIsUniversal() && ((parseContext.tagNumber == 0x03) || (parseContext.tagNumber == 0x04))) {
                            ParseContext subParseContext = new ParseContext(parseContext);
                            parseContextStack.addLast(subParseContext);
                        }
                        break;

                    case READ_TAG_CONTENT_FIXED_LENGTH:
                        if (readBuffer.available() > 0) {
                            int remainTagContent = parseContext.tagLength - parseContext.tagWrittenLength;
                            int avail = readBuffer.available() < remainTagContent ? readBuffer.available() : remainTagContent;
                            readBuffer.readBufferTo(getTagBuffer(parseContext), avail);
                            parseContext.tagWrittenLength += avail;
                            if (parseContext.tagWrittenLength == parseContext.tagLength) {
                                readResults.addAll(this.tagReadDone(parseContext, null));
                            }
                        }
                        break;

                    case READ_TAG_CONTENT_DONE:
                        readResults.addAll(this.tagReadDone(parseContext, null));
                        break;
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        return readResults;
    }

    public static byte[] toByteArray(Collection<? extends Number> collection) {
        Object[] boxedArray = collection.toArray();
        int len = boxedArray.length;
        byte[] array = new byte[len];
        for (int i = 0; i < len; i++) {
            array[i] = ((Number) boxedArray[i]).byteValue();
        }
        return array;
    }

    private Asn1ReadResult tagReadPrepare(ParseContext parseContext) {
        if (this.options.isStripSequence() && parseContext.depth == 0) {
            byte[] buffer = toByteArray(getTagBuffer(parseContext));
            return new Asn1ReadResult(
                    buffer,
                    Asn1ReadResult.ReadType.BEGIN_SEQUENCE,
                    new Asn1SequenceResult(
                            parseContext.tagLength == 0,
                            (parseContext.tagLenSize > 0) ? (parseContext.tagTotalReadLength + parseContext.tagLength) : 0
                    )
            );
        }
        return null;
    }

    private List<Asn1ReadResult> tagReadDone(ParseContext parseContext, ParseContext currentContext) throws IOException, InterruptedException {
        List<Asn1ReadResult> readResults = new ArrayList<>(2);
        parseContext.step = ParseContext.ParseStep.READ_TAG_BEGIN;
        if (this._checkEmitableData(parseContext) && !parseContext.tagIsEOC()) {
            byte[] buffer = new byte[parseContext.tagBuffer.size()];
            for (int i = 0; i < parseContext.tagBuffer.size(); i++) {
                buffer[i] = parseContext.tagBuffer.get(i);
            }
            ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(buffer));
            ASN1Primitive primitive = asn1InputStream.readObject();
            asn1InputStream.close();
            readResults.add(new Asn1ReadResult(buffer, Asn1ReadResult.ReadType.OBJECT, primitive));
        }

        parseContextStack.removeLast();

        if(parseContext.depth == 0) {
            if(this.options.isStripSequence()) {
                Asn1SequenceResult sequenceResult = new Asn1SequenceResult(
                        parseContext.tagLength == 0,
                        parseContext.tagTotalReadLength
                );
                byte[] buffer = toByteArray(currentContext.tagBuffer);
                readResults.add(new Asn1ReadResult(buffer, Asn1ReadResult.ReadType.END_SEQUENCE, sequenceResult));
            }
        }else{
            if(parseContext.tagIsEOC()) {
                readResults.addAll(this.tagReadDone(parseContext.parent, parseContext));
            }
        }

        if(parseContext.depth == 1 && parseContext.parent.totalRemaining == 0) {
            readResults.addAll(this.tagReadDone(parseContext.parent, null));
        }

        return readResults;
    }
}
