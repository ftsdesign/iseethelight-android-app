package biz.ftsdesign.iseethelight;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;

public class ConnectorService extends Service {
    public static final String NETWORK_WIFI = "wlan0";
    private static final long CONN_CHECK_INTERVAL_MS = 30L * 1000L;
    private static final long MIN_CONN_CHECK_INTERVAL_MS = 10L * 1000L;
    public static final String CHARSET_UTF8 = "UTF-8";

    private final IBinder binder = new LocalBinder();

    private final Queue<Map<String,String>> allSendQueue = new ArrayBlockingQueue<>(10);
    private Map<String, String> queueLast = null;
    private final Object queueLastLock = new Object();

    private long lastConnStatus = 0;
    private InetAddress wifiGateway = null;
    private ConnectorListener listener = null;
    private final Object connectionTaskLock = new Object();
    private CheckConnectionTask runningConnectionTask = null;
    /**
     * Might be null if not connected.
     */
    private LightState latestStateReceived = null;
    /**
     * Is guaranteed to be not null if the state was successfully received at least once.
     */
    private LightState latestStateSuccessful = null;
    private Timer connectionMonitor;
    private QueueProcessor queueProcessor;
    private Thread queueProcessorThread;
    private final Object commandRunning = new Object();

    private WiFiEventReceiver wiFiEventReceiver;

    public class LocalBinder extends Binder {
        public ConnectorService getService() {
            return ConnectorService.this;
        }
    }

    public ConnectorService() {
        super();
    }

    @Override
    public void onCreate() {
        connectionMonitor = new Timer("ConnectionMonitor");
        TimerTask monitorTask = new TimerTask() {
            @Override
            public void run() {
                // No need to check if nobody is listening
                if (listener != null) {
                    Log.d(ConnectorService.class.getSimpleName(), "Timer tick");
                    checkConnection();
                }
            }
        };
        long delay = CONN_CHECK_INTERVAL_MS;
        long period = CONN_CHECK_INTERVAL_MS;
        connectionMonitor.scheduleAtFixedRate(monitorTask, delay, period);
        Log.i(this.getClass().getSimpleName(), "Timer started with delay " + delay + " and period " + period);
        this.queueProcessor = new QueueProcessor();
        queueProcessorThread = new Thread(queueProcessor, "QueueProcessor");
        queueProcessorThread.start();

        wiFiEventReceiver = new WiFiEventReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(wiFiEventReceiver, intentFilter);

        Log.i(this.getClass().getSimpleName(), "Service ready");
    }

    public void send(Map<String, String> request) {
        try {
            allSendQueue.add(request);
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Queue is full", e);
        }
    }

    public void queueLast(Map<String, String> map) {
        synchronized (queueLastLock) {
            queueLast = map;
        }
    }

    class QueueProcessor implements Runnable {
        @Override
        public void run() {
            Log.i(this.getClass().getSimpleName(), "Started");
            while (!Thread.interrupted()) {
                try {
                    Map<String, String> cmd;
                    cmd = allSendQueue.poll();
                    if (cmd == null) {
                        synchronized (queueLastLock) {
                            if (queueLast != null) {
                                cmd = queueLast;
                                queueLast = null;
                            }
                        }
                    }
                    if (cmd != null) {
                        new RequestResponseTask().execute(cmd);
                        synchronized (commandRunning) {
                            commandRunning.wait(5000);
                        }
                    } else {
                        // Nothing to do, sleep for a while, so that we don'queueProcessorThread loop nonstop
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Log.i(this.getClass().getSimpleName(), "Interrupted");
                } catch (Exception e) {
                    Log.e(this.getClass().getSimpleName(), "Error", e);
                }
            }
            Log.i(this.getClass().getSimpleName(), "Stopped");
        }
    }

    class RequestResponseTask extends AsyncTask<Map<String,String>, Void, Boolean> {
        private long ts;

        @Override
        protected Boolean doInBackground(final Map<String, String>... params) {
            ts = System.currentTimeMillis();
            boolean success = false;
            if (wifiGateway != null) {
                try {
                    String urlString = getBaseUrl(wifiGateway) + "?" + assembleQuery(params[0]);
                    Log.i(this.getClass().getSimpleName(), urlString);
                    LightState newState = sendRequest(urlString);
                    latestStateReceived = newState;
                    if (newState != null)
                        latestStateSuccessful = newState;
                    success = true;
                } catch (Exception e) {
                    Log.e(this.getClass().getSimpleName(), "Cannot send command", e);
                }
            } else {
                Log.e(this.getClass().getSimpleName(), "Not connected");
            }
            return success;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            ts = System.currentTimeMillis() - ts;
            Log.i(this.getClass().getSimpleName(), "Execution time " + ts + " ms");
            synchronized (commandRunning) {
                commandRunning.notifyAll();
            }
            if (listener != null)
                listener.onStateReceived(latestStateReceived);
        }
    }

    private String assembleQuery(Map<String, String> param) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (Iterator<Map.Entry<String,String>> it = param.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String,String> entry = it.next();
            sb.append(entry.getKey());
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), CHARSET_UTF8));
            if (it.hasNext())
                sb.append("&");
        }
        return sb.toString();
    }

    public void checkConnection() {
        final long now = System.currentTimeMillis();
        synchronized (connectionTaskLock) {
            if (lastConnStatus + MIN_CONN_CHECK_INTERVAL_MS < now) {
                if (runningConnectionTask == null) {
                    runningConnectionTask = new CheckConnectionTask();
                    runningConnectionTask.execute();
                }
            }
        }
    }

    class CheckConnectionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                checkGateway();
            } finally {
                synchronized (connectionTaskLock) {
                    runningConnectionTask = null;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (listener != null) {
                listener.onStateReceived(latestStateReceived);
            } else {
                Log.w(this.getClass().getSimpleName(), "No listeners");
            }
        }
    }

    private void checkGateway() {
        Log.d(this.getClass().getSimpleName(), "Checking connection...");
        InetAddress wifiAddress = getWifiAddress();
        if (wifiAddress != null) {
            Log.d(this.getClass().getSimpleName(), "WiFi address is " + wifiAddress.getHostAddress());
            InetAddress gatewayAddress = getGatewayAddress(wifiAddress);
            Log.d(this.getClass().getSimpleName(), "Gateway is " + gatewayAddress.getHostAddress());
            lastConnStatus = System.currentTimeMillis();
            final LightState state = getGatewayResponse(gatewayAddress);
            if (state != null) {
                if (wifiGateway == null || !wifiGateway.equals(gatewayAddress)) {
                    Log.d(this.getClass().getSimpleName(), "Light connected");
                }
                wifiGateway = gatewayAddress;

            } else {
                Log.d(this.getClass().getSimpleName(), "Gateway is not light");
                wifiGateway = null;
            }
        } else {
            wifiGateway = null;
            latestStateReceived = null;
        }
    }

    public boolean isConnected() {
        return wifiGateway != null;
    }

    private LightState getGatewayResponse(final InetAddress addressToCheck) {
        LightState newState = null;
        if (addressToCheck != null) {
            String urlString = getBaseUrl(addressToCheck);
            newState = sendRequest(urlString);
        }
        latestStateReceived = newState;
        if (newState != null)
            latestStateSuccessful = newState;
        return this.latestStateReceived;
    }

    private LightState sendRequest(final String urlString) {
        Log.d(this.getClass().getSimpleName(), urlString);
        LightState state = null;

        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            final int statusCode = urlConnection.getResponseCode();
            if (statusCode == 200) {
                InputStream is = new BufferedInputStream(urlConnection.getInputStream());
                StringBuilder sb = new StringBuilder();
                String s;
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while ((s = br.readLine()) != null) {
                    sb.append(s).append("\n");
                }
                String result = sb.toString();
                is.close();
                try {
                    state = new LightState(result);
                    Log.d(this.getClass().getSimpleName(), state.toString());
                } catch (Exception e) {
                    Log.d(this.getClass().getSimpleName(), "Bad response", e);
                }
            } else {
                Log.e(this.getClass().getSimpleName(), "Bad response status: " + statusCode);
            }
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "HTTP request failed", e);
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
        return state;
    }

    @NonNull
    private String getBaseUrl(InetAddress address) {
        return "http://" + address.getHostAddress() + "/";
    }

    private InetAddress getGatewayAddress(final InetAddress inetAddress) {
        InetAddress gatewayAddress = null;
        try {
            // https://en.wikipedia.org/wiki/IPv4_subnetting_reference
            // https://en.wikipedia.org/wiki/List_of_assigned_/8_IPv4_address_blocks
            // https://en.wikipedia.org/wiki/Private_network
            final byte[] addressAsBytes = inetAddress.getAddress();
            addressAsBytes[3] = 1;
            gatewayAddress = InetAddress.getByAddress(addressAsBytes);
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Can'queueProcessorThread get gateway address", e);
        }
        return gatewayAddress;
    }

    private InetAddress getWifiAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface ni = en.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    if (ni.getName().equals(NETWORK_WIFI)) {
                        for (Enumeration<InetAddress> en2 = ni.getInetAddresses(); en2.hasMoreElements(); ) {
                            InetAddress ia = en2.nextElement();
                            if (ia instanceof Inet4Address) {
                                Log.d(this.getClass().getSimpleName(), ni + " " + ia.getHostAddress());
                                return ia;
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(this.getClass().getSimpleName(), "Cannot get addresses", e);
        }
        return null;
    }

    public void registerConnectorListener(final ConnectorListener listener) {
        if (listener != null) {
            this.listener = listener;
            Log.i(this.getClass().getSimpleName(), "Registered listener " + listener.getClass().getSimpleName());
        }
    }

    public void unregisterConnectorListener(final ConnectorListener listener) {
        if (listener != null && this.listener == listener) {
            this.listener = null;
            Log.i(this.getClass().getSimpleName(), "Unregistered listener " + listener.getClass().getSimpleName());
        }
    }

    public LightState getLatestStateReceived() {
        return latestStateReceived;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connectionMonitor.cancel();
        queueProcessorThread.interrupt();
        if (wiFiEventReceiver != null) {
            unregisterReceiver(wiFiEventReceiver);
        }
        Log.i(this.getClass().getSimpleName(), "Destroyed");
    }

    public class WiFiEventReceiver extends BroadcastReceiver {
        public WiFiEventReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(this.getClass().getSimpleName(), "CONNECTIVITY_CHANGE");
            checkConnection();
        }
    }
}
