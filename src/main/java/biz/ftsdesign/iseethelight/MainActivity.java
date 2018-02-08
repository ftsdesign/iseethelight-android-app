package biz.ftsdesign.iseethelight;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.ftstrading.iseethelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biz.ftsdesign.iseethelight.ui.LightNameChangedListener;
import biz.ftsdesign.iseethelight.ui.RenameDialogFragment;

public class MainActivity extends AbstractActivity implements View.OnTouchListener,
        RecorderListener, ConnectorListener, LightNameChangedListener {
    private static final int INTERVAL_MIN = 1;
    private static final int INTERVAL_MAX = 30;
    private static final int INTERVAL_DEFAULT = 3;
    private Player player = null;
    private PersistentLightStateStoreService persistentLightStateStoreService;

    private ServiceConnection persistenceServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            PersistentLightStateStoreService.LocalBinder binder = (PersistentLightStateStoreService.LocalBinder) service;
            persistentLightStateStoreService = binder.getService();
            restoreLastSequence();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            persistentLightStateStoreService = null;
        }
    };

    public MainActivity() {
//        persistentLightStateStoreService = new PersistentLightStateStoreService(this);
        enableListeners(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupActionBar();

        populateAndSetIntervalSpinner();
        TextView connectedView = (TextView) findViewById(R.id.connected);
        registerForContextMenu(connectedView);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Recorder.getInstance().setRecorderListener(this);

        setupListeners();

        Intent intent = new Intent(this, PersistentLightStateStoreService.class);
        bindService(intent, persistenceServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void restoreLastSequence() {
        PersistentLightState lastModified = null;

        if (persistentLightStateStoreService != null) {
            lastModified = persistentLightStateStoreService.getLastModified();
        } else {
            Log.e(this.getClass().getSimpleName(), "PersistentLightStateStoreService is null");
        }

        if (lastModified != null) {
            Recorder.getInstance().setRecording(lastModified.getLastUploadedSequence());
        }
    }

    private void setupListeners() {
        boolean state = enableListeners(false);

        Spinner intervalSpinner = (Spinner) findViewById(R.id.spinnerInterval);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isListenersEnabled())
                    onIntervalChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        Button redButton = (Button) findViewById(R.id.buttonRed);
        redButton.setOnTouchListener(this);

        Button greenButton = (Button) findViewById(R.id.buttonGreen);
        greenButton.setOnTouchListener(this);

        Button blueButton = (Button) findViewById(R.id.buttonBlue);
        blueButton.setOnTouchListener(this);

        Button whiteButton = (Button) findViewById(R.id.buttonWhite);
        whiteButton.setOnTouchListener(this);

        Button yellowButton = (Button) findViewById(R.id.buttonYellow);
        yellowButton.setOnTouchListener(this);

        Button magentaButton = (Button) findViewById(R.id.buttonMagenta);
        magentaButton.setOnTouchListener(this);

        Button cyanButton = (Button) findViewById(R.id.buttonCyan);
        cyanButton.setOnTouchListener(this);

        enableListeners(state);
    }

    private void onIntervalChange() {
        if (Recorder.getInstance().hasRecorded()) {
            int interval = getSelectedInterval();
            sendSequence(Recorder.getInstance().getRecording(), interval);
        }
    }

    private void populateAndSetIntervalSpinner() {
        View v = findViewById(R.id.spinnerInterval);
        if (v != null) {
            Spinner spinner = (Spinner) v;
            ArrayList<String> values = new ArrayList<>();
            for (int i = INTERVAL_MIN; i <= INTERVAL_MAX; i++) {
                values.add(String.valueOf(i));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, values);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setSelection(INTERVAL_DEFAULT - 1); // 0-based index
        }
    }

    @Override
    public void onHasRecorded(final boolean recorded) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enablePlay(recorded);
                updateUpload();
            }
        });
    }

    private void enablePlay(boolean b) {
        final View v = findViewById(R.id.buttonPlay);
        if (v != null) {
            v.setEnabled(b);
        }
    }

    private void updateUpload() {
        if (connectorService != null) {
            final View v = findViewById(R.id.buttonUpload);
            if (v != null) {
                boolean enabled = Recorder.getInstance().hasRecorded() && connectorService.isConnected();
                v.setEnabled(enabled);
            }
        }
    }

    @Override
    public void onStateReceived(final LightState state) {
        String oldLightName = lightState != null ? lightState.getName() : null;
        super.onStateReceived(state);
        updateOnUiThread();
        if (lightState != null && (oldLightName == null || !lightState.getName().equals(oldLightName))) {
            Log.i(this.getClass().getSimpleName(), "Light changed " + oldLightName + " => " + lightState.getName());
            PersistentLightState persistentLightState = persistentLightStateStoreService.getPersistentLightState(lightState);
            if (persistentLightState != null) {
                Recorder.getInstance().setRecording(persistentLightState.getLastUploadedSequence());
            }
        }
    }

    private void updateOnUiThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean prevState = enableListeners(false);
                updateUpload();
                enableListeners(prevState);
                updateConnected();
            }
        });
    }

    private void updateConnected() {
        String text;
        int color;
        boolean enabled;
        if (connectorService.isConnected() && lightState != null) {
            text = lightState.getName();
            if (persistentLightStateStoreService != null) {
                PersistentLightState persistentLightState = persistentLightStateStoreService.getPersistentLightState(lightState);
                if (persistentLightState != null) {
                    text = persistentLightState.getGivenName();
                }
            }
            color = Color.GREEN;
            enabled = true;
        } else {
            text = "Not connected";
            color = Color.GRAY;
            enabled = false;
        }
        TextView v = (TextView) findViewById(R.id.connected);
        if (v != null) {
            v.setText(text);
            v.setEnabled(enabled);
            v.setTextColor(color);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        Recorder.getInstance().setRecorderListener(null);
        unbindService(persistenceServiceConnection);
    }

    private void light(ColorCode cc, boolean on) {
        if (on) {
            light(cc.getColor());
            Recorder.getInstance().lightOn(cc);
        } else {
            light(ColorCode.BLACK.getColor());
            Recorder.getInstance().lightOff();
        }
    }

    public void light(final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View v = findViewById(R.id.light);
                if (v != null)
                    v.setBackgroundColor(color);
            }
        });
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final boolean on;
        if (event.getAction() == MotionEvent.ACTION_DOWN)
            on = true;
        else if (event.getAction() == MotionEvent.ACTION_UP)
            on = false;
        else
            return true;

        ColorCode cc = ColorCode.BLACK;
        if (on) {
            switch (v.getId()) {
                case R.id.buttonRed:
                    cc = ColorCode.RED;
                    break;
                case R.id.buttonGreen:
                    cc = ColorCode.GREEN;
                    break;
                case R.id.buttonBlue:
                    cc = ColorCode.BLUE;
                    break;
                case R.id.buttonWhite:
                    cc = ColorCode.WHITE;
                    break;
                case R.id.buttonYellow:
                    cc = ColorCode.YELLOW;
                    break;
                case R.id.buttonMagenta:
                    cc = ColorCode.MAGENTA;
                    break;
                case R.id.buttonCyan:
                    cc = ColorCode.CYAN;
                    break;
            }
        }
        light(cc, on);
        return true;
    }

    public void onPlay(View view) {
        replay();
    }

    public void replay() {
        Recorder.getInstance().stopRecording();
        if (player != null && player.isPlaying()) {
            player.stop();
        } else {
            List<RecorderSeqItem> recorded = Recorder.getInstance().getRecording();
            if (recorded != null && recorded.size() > 0) {
                player = new Player(recorded, this);
                player.start();
            }
        }
    }

    public void onUpload(View view) {
        Recorder.getInstance().stopRecording();
        List<RecorderSeqItem> recorded = Recorder.getInstance().getRecording();
        if (recorded != null && recorded.size() > 0) {
            int interval = getSelectedInterval();
            sendSequence(recorded, interval);
        }
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

    private void sendSequence(List<RecorderSeqItem> recorded, int interval) {
        String s = toRequest(recorded, interval);
        Map<String, String> request = new HashMap<>();
        request.put(Commands.COMMAND_SEQ, s);
        request.put(Commands.COMMAND_STATE, "1");
        connectorService.send(request);
        setOnOffVisual(true);
        persistentLightStateStoreService.save(connectorService.getLatestStateReceived(), recorded);
    }

    private int getSelectedInterval() {
        int interval = INTERVAL_DEFAULT;
        View v = findViewById(R.id.spinnerInterval);
        if (v != null) {
            Spinner sp = (Spinner) v;
            Object o = sp.getSelectedItem();
            try {
                interval = Integer.valueOf(o.toString());
            } catch (Exception e) {
                Log.wtf(this.getClass().getSimpleName(), e);
            }
        }
        return interval;
    }

    private String toRequest(List<RecorderSeqItem> recorded, int interval) {
        StringBuilder sb = new StringBuilder();
        sb.append(getSequenceRequest(recorded));
        // Add interval pause in the end
        sb.append(getIntervalRequest(interval));
        return sb.toString();
    }

    private String getSequenceRequest(List<RecorderSeqItem> recorded) {
        StringBuilder sb = new StringBuilder();
        for (RecorderSeqItem item : recorded) {
            long durationMs = item.getDurationMs();
            int tenthSecond = (int) Math.round(durationMs / 100d);
            if (tenthSecond < 1)
                tenthSecond = 1;
            do {
                sb.append(item.getColorCode().getCode());

                int duration = Math.min(99, tenthSecond);
                tenthSecond -= duration;
                if (duration < 10)
                    sb.append("0");
                sb.append(duration);
            } while (tenthSecond > 0);
        }
        return sb.toString();
    }

    private String getIntervalRequest(int interval) {
        StringBuilder sb = new StringBuilder();
        // Interval may be longer than max 9.9 in one packet, so split if needed
        int intervalToAdd = interval;
        while (intervalToAdd > 0) {
            int i = Math.min(intervalToAdd, 9);
            intervalToAdd -= i;
            sb.append("0").append(i).append("0");
        }
        return sb.toString();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.connected) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_context_connect, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.rename_light_menu_item) {
            PersistentLightState persistentLightState = persistentLightStateStoreService.getPersistentLightState(lightState);
            if (persistentLightState != null) {
                RenameDialogFragment dialog = new RenameDialogFragment();
                dialog.setPersistentLightState(persistentLightState);
                dialog.setLightNameChangedListener(this);
                dialog.show(getFragmentManager(), "RenameDialogFragment");
                return true;
            } else {
                Log.e(this.getClass().getSimpleName(), "Can't get persistent state for " + lightState);
                return false;
            }
        } else {
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onLightNameChanged(String lightName, String newGivenName) {
        updateConnected();
    }
}
