package biz.ftsdesign.iseethelight;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.util.Properties;

public class LightState {
    private static final String SIGNAL_STRING = "Let there be light!";
    public static final String PROP_NAME = "name";
    public static final String PROP_UPTIME = "uptime";
    public static final String PROP_STATE = "state";
    public static final String PROP_TIMER_STATE = "timer.state";
    public static final String PROP_TIMER_DURATION_MINUTES = "timer.durationMinutes";
    public static final String PROP_TIMER_REMAINING_MINUTES = "timer.remainingMinutes";
    public static final String PROP_FIRMWARE = "firmware";
    public static final String PROP_AMBLIGHTSENSOR_ENABLED = "amblightsensor.enabled";
    public static final String PROP_AMBLIGHTSENSOR_VALUE = "amblightsensor.value";
    public static final String PROP_AMBLIGHTSENSOR_STATE = "amblightsensor.state";
    public static final String PROP_BROWNOUT_FLAG = "brownout.flag";
    public static final String PROP_MODE = "mode";

    private final long uptimeMs;
    private final boolean isOn;
    private final boolean isTimerOn;
    private final int timerDurationMinutes;
    private final int timerRemainingMinutes;
    private final String name;
    private final boolean ambientLightSensorEnabled;
    private final int ambientLightSensorReading;
    private final boolean day;
    private final int firmware;
    private final boolean brownout;
    private final Mode mode;

    public LightState(final String s) {
        try {
            if (!s.contains(SIGNAL_STRING))
                throw new IllegalArgumentException("Signal string not found");
            Log.d(this.getClass().getSimpleName(), s);
            Properties p = new Properties();
            p.load(new ByteArrayInputStream(s.getBytes()));

            this.uptimeMs = Long.valueOf(p.getProperty(PROP_UPTIME));
            this.isOn = Integer.valueOf(p.getProperty(PROP_STATE)) == 1;
            this.isTimerOn = Integer.valueOf(p.getProperty(PROP_TIMER_STATE)) == 1;
            this.timerDurationMinutes = Integer.valueOf(p.getProperty(PROP_TIMER_DURATION_MINUTES));
            this.timerRemainingMinutes = Integer.valueOf(p.getProperty(PROP_TIMER_REMAINING_MINUTES));

            // v.2 additions
            this.name = p.containsKey(PROP_NAME) ? p.getProperty(PROP_NAME) : "";
            this.ambientLightSensorEnabled = p.containsKey(PROP_AMBLIGHTSENSOR_ENABLED) && Integer.valueOf(p.getProperty(PROP_AMBLIGHTSENSOR_ENABLED)) == 1;
            this.ambientLightSensorReading = p.containsKey(PROP_AMBLIGHTSENSOR_VALUE) ? Integer.valueOf(p.getProperty(PROP_AMBLIGHTSENSOR_VALUE)) : 0;
            this.firmware = p.containsKey(PROP_FIRMWARE) ? Integer.valueOf(p.getProperty(PROP_FIRMWARE)) : 0;
            this.brownout = p.containsKey(PROP_BROWNOUT_FLAG) && Integer.valueOf(p.getProperty(PROP_BROWNOUT_FLAG)) == 1;

            // Firmware v.4 additions
            this.day = p.containsKey(PROP_AMBLIGHTSENSOR_STATE) && Integer.valueOf(p.getProperty(PROP_AMBLIGHTSENSOR_STATE)) == 1;
            if (p.containsKey(PROP_MODE) && Integer.valueOf(p.getProperty(PROP_MODE)) == 1) {
                mode = Mode.DIRECT;
            } else {
                mode = Mode.SEQUENCE;
            }
            Log.d(this.getClass().getSimpleName(), s);

        } catch (Exception e) {
            String substring = s != null && s.length() > 100 ? s.substring(0, 100) : s;
            throw new IllegalArgumentException("Not a valid state: " + substring, e);
        }
    }

    public boolean isOn() {
        return isOn;
    }

    public boolean isTimerOn() {
        return isTimerOn;
    }

    public int getTimerDurationMinutes() {
        return timerDurationMinutes;
    }

    public int getTimerRemainingMinutes() {
        return timerRemainingMinutes;
    }

    public long getUptimeMs() {
        return uptimeMs;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "LightState{" +
                "isOn=" + isOn +
                ", name=" + name +
                ", firmware=" + firmware +
                ", uptimeMs=" + uptimeMs +
                ", isTimerOn=" + isTimerOn +
                ", timerDurationMinutes=" + timerDurationMinutes +
                ", timerRemainingMinutes=" + timerRemainingMinutes +
                ", ambientLightSensorEnabled=" + ambientLightSensorEnabled +
                ", ambientLightSensorReading=" + ambientLightSensorReading +
                ", day=" + day +
                ", brownout=" + brownout +
                ", mode=" + mode +
                '}';
    }

    public boolean isAmbientLightSensorEnabled() {
        return ambientLightSensorEnabled;
    }

    public int getAmbientLightSensorReading() {
        return ambientLightSensorReading;
    }

    public int getFirmware() {
        return firmware;
    }

    public String getName() {
        return name;
    }

    public boolean isBrownout() {
        return brownout;
    }

    public boolean isDay() {
        return day;
    }
}
