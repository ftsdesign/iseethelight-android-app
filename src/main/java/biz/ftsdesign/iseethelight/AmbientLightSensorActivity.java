package biz.ftsdesign.iseethelight;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.ftstrading.iseethelight.R;

import java.util.HashMap;
import java.util.Map;

public class AmbientLightSensorActivity extends AbstractActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ambient_light_sensor);
        setupActionBar();

        setInitialState();
    }

    private void setInitialState() {
        if (connectorService != null) {
            lightState = connectorService.getLatestStateReceived();

            if (lightState != null) {
                View switchView = findViewById(R.id.switchAmbLight);
                if (switchView != null) {
                    ((Switch) switchView).setChecked(lightState.isAmbientLightSensorEnabled());
                }

                updateAmbLightReading();
            }
        }
    }

    private void updateAmbLightReading() {
        View readingView = findViewById(R.id.textViewSensorReading);
        if (readingView != null && lightState != null) {
            ((TextView)readingView).setText((lightState.isDay() ? "Day" : "Night") + " (" + String.valueOf(lightState.getAmbientLightSensorReading()) + ")");
        }
    }

    public void onSwitchAmbLightSensor(View v) {
        if (v.getId() == R.id.switchAmbLight) {
            Switch sw = (Switch) v;
            boolean isOn = sw.isChecked();
            if (isOn) {
                setOnOffVisual(false);
            }
            Map<String, String> request = new HashMap<>();
            request.put(Commands.COMMAND_AMB_LIGHT, isOn ? "1" : "0");
            connectorService.send(request);
        }
    }

    private void updateAmbLight() {
        final View v = findViewById(R.id.switchAmbLight);
        if (v != null) {
            Switch sw = (Switch) v;
            boolean enabled = connectorService.isConnected();
            sw.setEnabled(enabled);
            sw.setChecked(lightState != null && lightState.isAmbientLightSensorEnabled());
        } else {
            Log.e(this.getClass().getSimpleName(), "switchAmbLight is null");
        }
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
                updateAmbLightReading();
                boolean state = enableListeners(false);
                updateAmbLight();
                enableListeners(state);
            }
        });
    }
}
