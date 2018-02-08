package biz.ftsdesign.iseethelight;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.ftstrading.iseethelight.R;

import java.util.HashMap;
import java.util.Map;

public class DirectActivity extends AbstractActivity implements SeekBar.OnSeekBarChangeListener, View.OnTouchListener {
    private static final long DEBOUNCE_INTERVAL_MS = 500;
    private int r = 0;
    private int g = 0;
    private int b = 0;
    private long lastSentTimestamp = 0;
    private String lastSentColourString;

    public DirectActivity() {
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        SeekBar sb = (SeekBar) findViewById(R.id.brightnessBar);
        sb.setOnSeekBarChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SeekBar sb = (SeekBar) findViewById(R.id.brightnessBar);
        sb.setOnSeekBarChangeListener(null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_direct);
        setupActionBar();

        View imageView = findViewById(R.id.imageRGBCircle);
        if (imageView != null) {
            ImageView iv = (ImageView) imageView;
            iv.setOnTouchListener(this);
        } else {
            Log.e(this.getClass().getSimpleName(), "imageRGBCircle is null");
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        onChange();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void onChange() {
        sendRGB(r, g, b);
    }

    private void sendRGB(int r, int g, int b) {
        SeekBar brightnessBar = (SeekBar) findViewById(R.id.brightnessBar);
        final int max = brightnessBar.getMax();
        final double min = max * 0.2;
        int rawBrightness = brightnessBar.getProgress();
        Log.i(this.getClass().getSimpleName(), "" + min + ".." + max + " " + rawBrightness);
        double brightness = (rawBrightness + min) / (max + min);
        int rAdj = (int) (r * brightness);
        int gAdj = (int) (g * brightness);
        int bAdj = (int) (b * brightness);
        Log.i(this.getClass().getSimpleName(), "RGB " + r + " " + g + " " + b + " * " + brightness + " => " + rAdj + " " + gAdj + " " + bAdj);
        if (rAdj + gAdj + bAdj > 10) {

            String formatted = String.format("%02x%02x%02x", rAdj, gAdj, bAdj);

            // Touch events tend to produce multiple duplicates in a short time
            final boolean needToSend;
            if (formatted.equals(lastSentColourString)) {
                if (lastSentTimestamp + DEBOUNCE_INTERVAL_MS < System.currentTimeMillis()) {
                    needToSend = true;
                } else {
                    needToSend = false;
                }
            } else {
                needToSend = true;
            }

//            Log.i(this.getClass().getSimpleName(), formatted);
            if (needToSend) {
                Map<String, String> map = new HashMap<>();
                map.put(Commands.COMMAND_DIRECT, formatted);
                connectorService.queueLast(map);
                lastSentTimestamp = System.currentTimeMillis();
                lastSentColourString = formatted;
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.getId() == R.id.imageRGBCircle) {
            // Absolute to screen
            final float touchScreenX = event.getRawX();
            final float touchScreenY = event.getRawY();
            Log.d(this.getClass().getSimpleName(), "Touch screen abs coords:  " + touchScreenX + "," + touchScreenY);

            int[] location = new int[2];
            v.getLocationOnScreen(location);
            final float inImageViewX = touchScreenX - location[0];
            final float inImageViewY = touchScreenY - location[1];
            Log.d(this.getClass().getSimpleName(), "Coords in view:  " + inImageViewX + "," + inImageViewY);

            ImageView imageView = (ImageView) v;
            Drawable drawable = imageView.getDrawable();
            final Bitmap bitmap = ((BitmapDrawable)drawable).getBitmap();
            final float bitmapOffsetX = (v.getWidth() - bitmap.getWidth()) / 2;
            final float bitmapOffsetY = (v.getHeight() - bitmap.getHeight()) / 2;
            Log.d(this.getClass().getSimpleName(), "Bitmap offset in view:  " + bitmapOffsetX + "," + bitmapOffsetY);

            final float inBitmapX = inImageViewX - bitmapOffsetX;
            final float inBitmapY = inImageViewY - bitmapOffsetY;
            Log.d(this.getClass().getSimpleName(), "Coords in bitmap:  " + inBitmapX + "," + inBitmapY);

            if (inBitmapX >= 0 && inBitmapX < bitmap.getWidth() && inBitmapY >= 0 && inBitmapY < bitmap.getHeight()) {
                int pixel = bitmap.getPixel((int) inBitmapX, (int) inBitmapY);
                if ((pixel & 0xffffff) > 0) {
                    r = Color.red(pixel);
                    g = Color.green(pixel);
                    b = Color.blue(pixel);
                    Log.d(this.getClass().getSimpleName(), "Color @ touch " + r + " " + g + " " + b);
                    sendRGB(r, g, b);
                } else {
                    Log.d(this.getClass().getSimpleName(), "Pixel was black");
                }
            } else {
                Log.d(this.getClass().getSimpleName(), "Coords out of range, " + inImageViewX + "," + inImageViewY
                        + " > " + bitmap.getWidth() + "," + bitmap.getHeight());
            }
        }
        return true;
    }
}
