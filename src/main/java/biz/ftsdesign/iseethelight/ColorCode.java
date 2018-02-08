package biz.ftsdesign.iseethelight;

import android.graphics.Color;

public enum ColorCode {
    BLACK(0, Color.BLACK),
    RED(1, Color.RED),
    GREEN(2, Color.GREEN),
    YELLOW(3, Color.YELLOW),
    BLUE(4, Color.BLUE),
    MAGENTA(5, Color.MAGENTA),
    CYAN(6, Color.CYAN),
    WHITE(7, Color.WHITE);

    private final int code;
    private final int colorUi;

    ColorCode(int code, int colorUi) {
        this.code = code;
        this.colorUi = colorUi;
    }

    public int getColor() {
        return colorUi;
    }

    public int getCode() {
        return code;
    }
}
