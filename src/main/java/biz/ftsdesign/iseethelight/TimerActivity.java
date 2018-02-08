package biz.ftsdesign.iseethelight;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.ftstrading.iseethelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TimerActivity extends AbstractActivity {
    private static final int TIMER_MIN = 1;
    private static final int TIMER_MAX = 24;
    private static final int TIMER_DEFAULT = 12;

    public TimerActivity() {
        enableListeners(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableListeners(false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timer);
        populateAndSetTimerSpinner();
        setupListeners();
        setupActionBar();

//        enableListeners(true);
    }

    private void setupListeners() {
        Switch timerSwitch = (Switch) findViewById(R.id.switchTimer);
        timerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onTimerOnOff(buttonView);
            }
        });

        Spinner timerSpinner = (Spinner) findViewById(R.id.spinnerTimer);
        timerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i(TimerActivity.this.getClass().getSimpleName(), "onItemSelected " + Thread.currentThread().getName());
                onTimerDurationChange(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        /*
        This is a hack. Spinner triggers a delayed event when adapter is set, so we block
        all event handlers in this activity until user interacts with it in any way.
         */
        enableListeners(true);
        Log.i(this.getClass().getSimpleName(), "Listeners enabled " + Thread.currentThread().getName());
    }

    /**
     * Called on timer duration spinner change from UI
     * @param v
     */
    public void onTimerDurationChange(View v) {
        // We don't send duration updates when timer is not on
        if (isListenersEnabled() && isTimerOn()) {
            onTimerChange();
        }
    }

    /**
     * Called on timer switch change from UI
     * @param v
     */
    public void onTimerOnOff(View v) {
        if (isListenersEnabled())
            onTimerChange();
    }

    private void onTimerChange() {
        Log.i(this.getClass().getSimpleName(), "onTimerChange " + isListenersEnabled());
        Map<String, String> request = new HashMap<>();
        if (isTimerOn()) {
            int timerHours = getTimer();
            request.put(Commands.COMMAND_TIMER, String.valueOf(timerHours * 60));
        } else {
            TextView tv = (TextView) findViewById(R.id.textViewTimerRemaining);
            tv.setText("");
            request.put(Commands.COMMAND_TIMER, "0");
        }
        connectorService.send(request);
    }

    private boolean isTimerOn() {
        boolean isOn = false;
        View v = findViewById(R.id.switchTimer);
        if (v != null) {
            Switch sw = (Switch) v;
            isOn = sw.isChecked();
        } else {
            Log.e(this.getClass().getSimpleName(), "switchTimer is null");
        }
        return isOn;
    }

    private int getTimer() {
        int hours = 0;
        if (isTimerOn()) {
            View v = findViewById(R.id.spinnerTimer);
            if (v != null) {
                Spinner sp = (Spinner) v;
                Object o = sp.getSelectedItem();
                try {
                    hours = Integer.valueOf(o.toString());
                } catch (Exception e) {
                    Log.wtf(this.getClass().getSimpleName(), e);
                }
            }
        }
        return hours;
    }

    private void populateAndSetTimerSpinner() {
        View v = findViewById(R.id.spinnerTimer);
        if (v != null) {
            Log.i(this.getClass().getSimpleName(), "before adapter " + isListenersEnabled() + " " + Thread.currentThread().getName());
            Spinner spinner = (Spinner) v;
            spinner.setAdapter(createSpinnerAdapter()); // This is where onItemSelected is triggered
            spinner.setSelection(TIMER_DEFAULT - 1); // 0-based index
        } else {
            Log.e(this.getClass().getSimpleName(), "spinnerTimer is null");
        }
    }

    @NonNull
    private SpinnerAdapter createSpinnerAdapter() {
        ArrayList<String> values = new ArrayList<>();
        for (int i = TIMER_MIN; i <= TIMER_MAX; i++) {
            values.add(String.valueOf(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    @Override
    public void onStateReceived(LightState state) {
        super.onStateReceived(state);
        updateOnUiThread();
    }

    private void updateOnUiThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean state = enableListeners(false);
                updateTimer();
                enableListeners(state);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateTimer();
    }

    private void updateTimer() {

        final boolean isTimerOn = lightState != null && lightState.isTimerOn();
        View v = findViewById(R.id.switchTimer);
        if (v != null) {
            Switch sw = (Switch) v;
            sw.setChecked(isTimerOn);
            sw.setEnabled(connectorService != null && connectorService.isConnected());
        } else {
            Log.e(this.getClass().getSimpleName(), "switchTimer is null");
        }
        int timerDurationMinutes = 0;
        if (lightState != null)
            timerDurationMinutes = lightState.getTimerDurationMinutes();
        if (timerDurationMinutes > 0) {
            int hours = timerDurationMinutes / 60;
            if (hours >= TIMER_MIN && hours <= TIMER_MAX) {
                v = findViewById(R.id.spinnerTimer);
                if (v != null) {
                    Spinner sp = (Spinner) v;
                    sp.setSelection(hours - TIMER_MIN);
                } else {
                    Log.e(this.getClass().getSimpleName(), "spinnerTimer is null");
                }
            } else {
                Log.w(this.getClass().getSimpleName(), "Timer value out of range, ignoring: " + hours);
            }
        }

        v = findViewById(R.id.textViewTimerRemaining);
        if (v != null) {
            TextView tv = (TextView) v;
            if (!isTimerOn) {
                tv.setText("");
            } else {
                tv.setText(formatTimer(lightState.getTimerRemainingMinutes()));
            }
        } else {
            Log.e(this.getClass().getSimpleName(), "textViewTimerRemaining is null");
        }
    }

    private String formatTimer(int minutes) {
        StringBuilder sb = new StringBuilder();
        int m = minutes % 60;
        int h = (minutes - m) / 60;
        sb.append(h).append(":");
        if (m < 10)
            sb.append("0");
        sb.append(m);
        return sb.toString();
    }
}
