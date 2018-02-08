package biz.ftsdesign.iseethelight;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ftstrading.iseethelight.R;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractActivity extends AppCompatActivity implements ConnectorListener {
    private volatile boolean listenersEnabled = true;
    /*
    It is first set in onStart from whatever ConnectorService had at this point of time.
     */
    protected LightState lightState = null;
    protected int noResponseReceived = 0;
    private MenuItem batteryStatusMenuItem;
    private MenuItem connStatusMenuItem;
    private MenuItem onOffMenuItem;
    protected ConnectorService connectorService;

    private ServiceConnection connectorServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            ConnectorService.LocalBinder binder = (ConnectorService.LocalBinder) service;
            connectorService = binder.getService();

            lightState = connectorService.getLatestStateReceived();
            connectorService.registerConnectorListener(AbstractActivity.this);
            connectorService.checkConnection();
//        updateStatus();
            updateOnOff();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            connectorService.unregisterConnectorListener(AbstractActivity.this);
            connectorService = null;
        }
    };

    protected synchronized boolean isListenersEnabled() {
        return listenersEnabled;
    }

    protected synchronized boolean enableListeners(boolean enable) {
        boolean oldValue = listenersEnabled;
        listenersEnabled = enable;
        return oldValue;
    }

    protected void setupActionBar() {
        Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_menu, menu);
        super.onCreateOptionsMenu(menu);
        batteryStatusMenuItem = menu.findItem(R.id.batt_status_action_menu);
        connStatusMenuItem = menu.findItem(R.id.conn_status_action_menu);
        onOffMenuItem = menu.findItem(R.id.on_off_action_menu);
        updateStatus();
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.on_off_action_menu:
                handleOnOff();
                return true;

            case R.id.website_action_menu:
                handleWebsite();
                return true;

            case R.id.lamp_mode_action_menu:
                handleLampMode();
                return true;

            case R.id.light_id_mode_action_menu:
                handleLightIdMode();
                return true;

            case R.id.timer_action_menu:
                handleTimer();
                return true;

            case R.id.amb_light_action_menu:
                handleAmbLight();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handleTimer() {
        Intent intent = new Intent(this, TimerActivity.class);
        startActivity(intent);
    }

    private void handleAmbLight() {
        Intent intent = new Intent(this, AmbientLightSensorActivity.class);
        startActivity(intent);
    }

    private void handleLightIdMode() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private void handleLampMode() {
        Intent intent = new Intent(this, DirectActivity.class);
        startActivity(intent);
    }

    private void handleWebsite() {
        try {
            String url = getString(R.string.webpage);
            Log.i(this.getClass().getSimpleName(), "Opening " + url);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Log.w(this.getClass().getSimpleName(), "Cannot open the web page", e);
        }
    }

    private void handleOnOff() {
        LightState state = connectorService.getLatestStateReceived();
        Map<String,String> map = new HashMap<>();
        map.put(Commands.COMMAND_STATE, state != null && state.isOn() ? "0" : "1");
        connectorService.send(map);
    }

    private void setTextOrBgColor(View v, int color) {
        if (v instanceof TextView) {
            ((TextView)v).setTextColor(color);
        } else {
            v.setBackgroundColor(color);
        }
    }

    protected void updateOnOff() {
//        Log.i(this.getClass().getSimpleName(), "updateOnOff " + onOffMenuItem);
        final View v = findViewById(R.id.on_off_action_menu);
        if (v != null) {
            if (connectorService.isConnected()) {
                final int color;
                if (lightState != null && lightState.isOn()) {
                    color = Color.GREEN;
                } else {
                    color = Color.WHITE;
                }
                v.setEnabled(true);
                setTextOrBgColor(v, color);
            } else {
                setTextOrBgColor(v, Color.GRAY);
                v.setEnabled(false);
            }
        } else {
            Log.e(this.getClass().getSimpleName(), "on_off_action_menu is null");
        }
    }


    protected void setOnOffVisual(boolean b) {
        // TODO
//        View v = findViewById(R.id.switchOnOff);
//        if (v != null) {
//            Switch sw = (Switch) v;
//            sw.setChecked(b);
//        }
    }

    private void setConnectionStatusVisual(boolean connected) {
        if (connStatusMenuItem != null) {
            connStatusMenuItem.setIcon(connected ? R.drawable.ic_swap_horiz_white_24dp_g : R.drawable.ic_swap_horiz_white_24dp_r);
        } else {
            Log.e(this.getClass().getSimpleName(), "connStatusMenuItem is null5");
        }
    }

    private void setBatteryStatusVisual(BatteryStatus batteryStatus) {
        if (batteryStatusMenuItem != null) {
            final int c;
            switch (batteryStatus) {
                case GOOD:
                    batteryStatusMenuItem.setEnabled(true);
                    batteryStatusMenuItem.setIcon(R.drawable.ic_battery_status_g);
                    break;
                case LOW:
                    batteryStatusMenuItem.setEnabled(true);
                    batteryStatusMenuItem.setIcon(R.drawable.ic_battery_status_r);
                    break;
                default:
                    batteryStatusMenuItem.setEnabled(false);
                    batteryStatusMenuItem.setIcon(R.drawable.ic_battery_status);
                    break;
            }
        } else {
            Log.e(this.getClass().getSimpleName(), "batteryStatusMenuItem is null");
        }
    }

    @Override
    protected void onStart() {
        Log.d(this.getClass().getSimpleName(), "onStart");
        super.onStart();
        Intent intent = new Intent(this, ConnectorService.class);
        bindService(intent, connectorServiceConnection, Context.BIND_AUTO_CREATE);
        /*
        Sometimes we are already connected, so onServiceConnected will not be called,
         so we try setting listener here.
          */
        if (connectorService != null) {
            connectorService.registerConnectorListener(AbstractActivity.this);
        }
    }

    @Override
    protected void onStop() {
        Log.d(this.getClass().getSimpleName(), "onStop");
        super.onStop();
        connectorService.unregisterConnectorListener(this);
        unbindService(connectorServiceConnection);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        Log.i(this.getClass().getSimpleName(), "onUserInteraction");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStateReceived(final LightState state) {
        this.lightState = state;
        Log.i(this.getClass().getSimpleName(), "State received: " + state);
        updateOnUiThread();
        if (state != null) {
            noResponseReceived = 0;
        } else {
            if (noResponseReceived == 0) {
                Toast t = Toast.makeText(this, getString(R.string.no_response), Toast.LENGTH_SHORT);
                t.show();
            }
            noResponseReceived++;
        }
    }

    private void updateOnUiThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatus();
                AbstractActivity.this.listenersEnabled = false;
                updateOnOff();
                AbstractActivity.this.listenersEnabled = false;
            }
        });
    }

    private void updateStatus() {
        final boolean connected = lightState != null;
        final boolean brownout = lightState != null && lightState.isBrownout();
        setConnectionStatusVisual(connected);
        BatteryStatus batteryStatus;

        if (connected) {
            if (brownout) {
                batteryStatus = BatteryStatus.LOW;
            } else {
                batteryStatus = BatteryStatus.GOOD;
            }
        } else {
            batteryStatus = BatteryStatus.UNKNOWN;
        }
        setBatteryStatusVisual(batteryStatus);
    }
}
