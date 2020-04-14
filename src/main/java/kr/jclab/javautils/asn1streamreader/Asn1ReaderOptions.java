package kr.jclab.javautils.asn1streamreader;

public class Asn1ReaderOptions {
    private final boolean stripSequence;
    private final Asn1ReadCallback readCallback;

    protected Asn1ReaderOptions(boolean stripSequence, Asn1ReadCallback readCallback) {
        this.stripSequence = stripSequence;
        this.readCallback = readCallback;
    }

    public boolean isStripSequence() {
        return stripSequence;
    }

    public Asn1ReadCallback getReadCallback() {
        return readCallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean stripSequence = false;
        private Asn1ReadCallback readCallback;

        private Builder() {
        }

        public Builder stripSequence(boolean stripSequence) {
            this.stripSequence = stripSequence;
            return this;
        }

        public Builder readCallback(Asn1ReadCallback readCallback) {
            this.readCallback = readCallback;
            return this;
        }

        public Asn1ReaderOptions build() {
            return new Asn1ReaderOptions(stripSequence, readCallback);
        }
    }
}
