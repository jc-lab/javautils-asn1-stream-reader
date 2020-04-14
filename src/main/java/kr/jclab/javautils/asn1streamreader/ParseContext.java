package kr.jclab.javautils.asn1streamreader;

import java.util.ArrayList;

public class ParseContext {
    public enum ParseStep {
        READ_TAG_BEGIN ,
        READ_TAG_LONG,
        READ_TAG_LENGTH,
        READ_TAG_LENGTH_LONG,
        READ_TAG_CONTENT,
        READ_TAG_CONTENT_FIXED_LENGTH,
        READ_TAG_CONTENT_DONE
    }

    public final ParseContext parent;
    public final int depth;

    public int totalRemaining = -1;

    public ParseStep step = ParseStep.READ_TAG_BEGIN;

    public int tagClass = 0;
    public boolean tagConstructed = false;
    public int tagNumber = 0;
    public int tagLength = 0;
    public int tagTempInt10 = 0;
    public int tagLenSize = 0;
    public int tagLenRemaining = 0;
    public ArrayList<Byte> tagBuffer = new ArrayList<>(16);
    public int tagWrittenLength = 0;
    public int tagTotalLength = 0;
    public int tagTotalReadLength = 0;

    ParseContext(ParseContext parent) {
        this.parent = parent;
        if(parent != null) {
            this.depth = parent.depth + 1;
        }else{
            this.depth = 0;
        }
    }

    public int getDepth() {
        return this.depth;
    }

    public boolean tagIsUniversal() {
        return this.tagClass == 0x00;
    }
    public boolean tagIsEOC(){
        return this.tagClass == 0x00 && this.tagNumber == 0x00;
    }

    public void decrementTotalRemaining(int length) {
        if (this.totalRemaining > 0) {
            this.totalRemaining -= length;
        }
        this.tagTotalReadLength += length;
        if (this.parent != null) {
            this.parent.decrementTotalRemaining(length);
        }
    }
}
