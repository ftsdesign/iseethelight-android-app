package biz.ftsdesign.iseethelight;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Recorder {
    private static final long REC_AUTO_STOP_MS = 5000;
    private static final Recorder instance = new Recorder();
    private boolean recording = false;
    private long timeOn = 0;
    private boolean isOn = false;
    private ColorCode currentColor = null;
    private final Timer timer;
    private TimerTask lastTimerTask;
    private final List<RecorderSeqItem> recorded = new ArrayList<>();
    private RecorderListener listener = null;

    private Recorder() {
        timer = new Timer("AutoStopRec");
    }

    public static Recorder getInstance() {
        return instance;
    }

    private void startRecording() {
        if (!recording) {
            Log.d(this.getClass().getSimpleName(), "Recording started");
            recording = true;
            recorded.clear();
        }
    }

    public void stopRecording() {
        if (recording) {
            recording = false;
            isOn = false;
            timeOn = 0;
            Log.d(this.getClass().getSimpleName(), "Recording stopped");
            printRecorded();
            updateListener();
        }
    }

    private void printRecorded() {
        Log.d(this.getClass().getSimpleName(), "Recorded:");
        for (RecorderSeqItem rsi : recorded) {
            Log.d(this.getClass().getSimpleName(), rsi.toString());
        }
    }

    public boolean hasRecorded() {
        return !recorded.isEmpty();
    }

    public void lightOn(ColorCode colorCode) {
        final long now = System.currentTimeMillis();
        startRecording();
        if (!isOn && timeOn > 0) {
            long pauseDuration = now - timeOn;
            Log.d(this.getClass().getSimpleName(), "Pause " + pauseDuration + " ms");
            RecorderSeqItem rsi = new RecorderSeqItem(ColorCode.BLACK, pauseDuration);
            recorded.add(rsi);
        }
        if (isOn && currentColor != null && colorCode != currentColor) {
            // Pressed new color before releasing previous
            off();
        }
        timeOn = now;
        isOn = true;
        currentColor = colorCode;
        if (lastTimerTask != null)
            lastTimerTask.cancel();
    }

    public void lightOff() {
        off();
        if (lastTimerTask != null)
            lastTimerTask.cancel();
        TimerTask autoStopRecTask = new TimerTask() {
            @Override
            public void run() {
                stopRecording();
            }
        };
        lastTimerTask = autoStopRecTask;
        timer.schedule(autoStopRecTask, REC_AUTO_STOP_MS);
    }

    private void off() {
        final long now = System.currentTimeMillis();
        final long durationMillis = now - timeOn;
        ColorCode cc = currentColor;
        RecorderSeqItem rsi = new RecorderSeqItem(cc, durationMillis);
        recorded.add(rsi);
        if (recorded.size() == 1)
            updateListener();

        isOn = false;
        timeOn = now;
        currentColor = null;

        Log.d(this.getClass().getSimpleName(), "Rec " + cc + " " + durationMillis);
    }

    public List<RecorderSeqItem> getRecording() {
        return new ArrayList<>(recorded); // Copy
    }

    public void setRecorderListener(RecorderListener listener) {
        this.listener = listener;
        updateListener();
    }

    private void updateListener() {
        if (listener != null) {
            listener.onHasRecorded(hasRecorded());
        }
    }

    public void setRecording(List<RecorderSeqItem> recording) {
        recorded.clear();
        recorded.addAll(recording);
        updateListener();
    }
}
