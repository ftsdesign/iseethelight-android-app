package biz.ftsdesign.iseethelight;

import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

class Player {
    private final List<RecorderSeqItem> sequence;
    private final MainActivity mainActivity;
    private boolean isPlaying = false;
    private Timer timer;
    private int seqIndex = -1;

    public Player(List<RecorderSeqItem> recorded, MainActivity mainActivity) {
        this.sequence = recorded;
        this.mainActivity = mainActivity;
    }

    public void start() {
        Log.d(this.getClass().getSimpleName(), "Replay started");
        timer = new Timer("PlayerTimer");
        isPlaying = true;
        seqIndex = -1;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                onTimerTick();
            }
        }, 0);
    }

    private void onTimerTick() {
        seqIndex++;
        if (seqIndex >= sequence.size()) {
            stop();
        } else {
            final RecorderSeqItem rsi = sequence.get(seqIndex);
            mainActivity.light(rsi.getColorCode().getColor());
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onTimerTick();
                }
            }, rsi.getDurationMs());
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void stop() {
        timer.cancel();
        mainActivity.light(ColorCode.BLACK.getColor());
        isPlaying = false;
        Log.d(this.getClass().getSimpleName(), "Replay stopped");
    }
}
