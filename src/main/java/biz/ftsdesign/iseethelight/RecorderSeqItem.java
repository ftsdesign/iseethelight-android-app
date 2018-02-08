package biz.ftsdesign.iseethelight;

import java.io.Serializable;

public class RecorderSeqItem implements Serializable {
    private final ColorCode colorCode;
    private final long durationMs;

    public RecorderSeqItem(ColorCode colorCode, long durationMs) {
        this.colorCode = colorCode;
        this.durationMs = durationMs;
    }

    public ColorCode getColorCode() {
        return colorCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toString() {
        return colorCode + " " + durationMs;
    }
}
