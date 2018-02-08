package biz.ftsdesign.iseethelight;

/**
 * Commands set for the device. Unless specially mentioned otherwise, commands
 * should not be combined together within the same GET request. Commands also should not
 * be sent concurrently. The app must receive the response from the previous command
 * before sending out the next one.
 *
 * Device response sample. First line is a marker string, this is how we know that the device
 * responded is our device. The rest of the response follows the standard properties file
 * convention. All properties shown below are always present in the response.
 *
 * # Let there be light!
 * name=Light-4140
 * uptime=32557
 * state=1
 * mode=0
 * direct.color=360,244,4
 * timer.state=0
 * timer.durationMinutes=0
 * timer.remainingMinutes=0
 * firmware=6
 * sequence=708030
 * amblightsensor.enabled=0
 * amblightsensor.value=79
 * amblightsensor.state=1
 * brownout.flag=0
 * power.reduction=0
 *
 * name - name of device's WiFi hotspot, password is present only in the manual for security
 * uptime - uptime in ms, not currently used in UI
 * state - 1=on, 0=off
 * mode - 0=LightID, 1=lamp
 * direct.color - RGB color in lamp mode
 * timer.state - 1=running, 0=not running
 * timer.durationMinutes - timer duration set by the user, in minutes
 * timer.remainingMinutes - self explanatory
 * firmware - firmware version, not currently used in UI
 * sequence - the last argument sent with COMMAND_SEQ
 * amblightsensor.enabled - 1=enabled, 0=disabled
 * amblightsensor.value - current sensor reading, range from 0 to 1023
 * amblightsensor.state - 0=night (light on), 1=day (light off)
 * brownout.flag - 0=battery power is ok, 1=we had to reduce power at least once, thus have
 *      to show low battery sign
 * power.reduction - 0=device operates at full power, any positive value shows how many times
 *      device had to reduce power already. Can use this for battery level indicator. At 9
 *      device shuts down completely.
 */
public abstract class Commands {
    /**
     * Sets light sequence for Light ID mode. Value is encoded sequence.
     * Should be used together with state=1.
     * Example: while flash 0.5s, pause 3s: 705030
     * Full command: http:/.../?s=705030&state=1
     */
    public static final String COMMAND_SEQ = "s";

    /**
     * Value is 1 for on and 0 for off. Can be used alone or together with COMMAND_SEQ.
     */
    public static final String COMMAND_STATE = "state";

    /**
     * Value is 1 to turn ambient light sensor on, 0 to turn it off.
     */
    public static final String COMMAND_AMB_LIGHT = "ambient";

    /**
     * Value is integer number of minutes, e.g. 60. Positive value sets the timer and starts
     * the countdown immediately, zero value stops the timer. On/off state is not affected
     * by this command.
     */
    public static final String COMMAND_TIMER = "timer";

    /**
     * Value is RGB encoded as hex string, e.g. FF0011. Sending this command
     * causts the device to switch to "lamp" mode and turn on with the chosen colour.
     */
    public static final String COMMAND_DIRECT = "d";
}
