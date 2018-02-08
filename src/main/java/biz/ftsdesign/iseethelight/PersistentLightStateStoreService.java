package biz.ftsdesign.iseethelight;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PersistentLightStateStoreService extends Service {
    private static final String PERSISTENCE_FILE_NAME = "light-persist.dat";
    private static final int FORMAT_VERSION_1 = 1;
    private final SortedMap<String,PersistentLightState> nameToState = new TreeMap<>();
    private final IBinder binder = new LocalBinder();
    private ScheduledExecutorService scheduledExecutorService;
    private static final int PERSIST_RATE_MS = 100;

    public PersistentLightStateStoreService() {
        Log.d(this.getClass().getSimpleName(), "Instance created");
    }

    public class LocalBinder extends Binder {
        public PersistentLightStateStoreService getService() {
            return PersistentLightStateStoreService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        restore();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            int instances = 0;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Persister-" + (++instances));
            }
        });
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                persistIfNeeded();
            }
        }, 0, PERSIST_RATE_MS, TimeUnit.MILLISECONDS);
        Log.i(this.getClass().getSimpleName(), "Service ready");
    }

    public void save(LightState lightState, List<RecorderSeqItem> recorded) {
        save(getName(lightState), recorded);
    }

    private synchronized void persistIfNeeded() {
        boolean isDirty = false;
        for (PersistentLightState state : nameToState.values()) {
            if (state.isDirty()) {
                isDirty = true;
                break;
            }
        }
        if (isDirty) {
            persist();
        }
    }

    private void save(String name, List<RecorderSeqItem> recorded) {
        if (name != null) {
            PersistentLightState state = getOrCreateState(name);
            state.setLastUploadedSequence(recorded);
            persist();
        } else {
            Log.w(this.getClass().getSimpleName(), "No light ID");
        }
    }

    private PersistentLightState getOrCreateState(String name) {
        final PersistentLightState state;
        synchronized (nameToState) {
            if (nameToState.containsKey(name)) {
                state = nameToState.get(name);
            } else {
                state = new PersistentLightState(name);
                nameToState.put(name, state);
            }
        }
        return state;
    }

    private String getName(LightState lightState) {
        final String name;
        if (lightState != null && lightState.getName() != null && !lightState.getName().trim().isEmpty())
            name = lightState.getName();
        else
            name = null;
        return name;
    }

    private synchronized void persist() {
        try {
            FileOutputStream fos = openFileOutput(PERSISTENCE_FILE_NAME, MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeInt(FORMAT_VERSION_1);

            persistV1(oos);

            oos.close();
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Cannot persist", e);
        }
    }

    private void persistV1(ObjectOutputStream oos) throws IOException {
        oos.writeInt(nameToState.size());
        for (PersistentLightState state : nameToState.values()) {
            persistStateV1(state, oos);
            state.setDirty(false);
        }
        Log.i(this.getClass().getSimpleName(), "State persisted v1");
    }

    private void persistStateV1(PersistentLightState state, ObjectOutputStream oos) throws IOException {
        oos.writeUTF(state.getName());
        oos.writeUTF(state.getGivenName());
        oos.writeObject(state.getLastUploadedSequence());
        oos.writeLong(state.getLastModified());
    }

    private synchronized void restore() {
        Log.d(this.getClass().getSimpleName(), "Restoring the state...");
        ObjectInputStream ois = null;
        try {
            FileInputStream fis = openFileInput(PERSISTENCE_FILE_NAME);
            ois = new ObjectInputStream(fis);
            final int version = ois.readInt();
            switch (version) {
                case FORMAT_VERSION_1:
                    restoreV1(ois);
                    break;
                default:
                    throw new IOException("Unknown format version: " + version);
            }
            ois.close();
            Log.i(this.getClass().getSimpleName(), "State restored: " + nameToState.size());
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Cannot restore", e);
        } finally {
            try {
                if (ois != null)
                    ois.close();
            } catch (Exception e) {
                Log.e(this.getClass().getSimpleName(), "Cannot close", e);
            }
        }
    }

    private void restoreV1(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        nameToState.clear();
        int lightsCount = ois.readInt();
        for (int i = 0; i < lightsCount; i++) {
            PersistentLightState persistentLightState = restoreLightStateV1(ois);
            nameToState.put(persistentLightState.getName(), persistentLightState);
        }
    }

    private PersistentLightState restoreLightStateV1(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        String name = ois.readUTF();
        String givenName = ois.readUTF();
        List<RecorderSeqItem> recorded = (List<RecorderSeqItem>) ois.readObject();
        long lastModified = ois.readLong();

        PersistentLightState persistentLightState = new PersistentLightState(name);
        persistentLightState.setGivenName(givenName);
        persistentLightState.setLastUploadedSequence(recorded);
        persistentLightState.setLastModified(lastModified);
        persistentLightState.setDirty(false);
        return persistentLightState;
    }

    public PersistentLightState getLastModified() {
        long latestTimestamp = 0;
        PersistentLightState state = null;
        synchronized (nameToState) {
            for (PersistentLightState pls : nameToState.values()) {
                if (pls.getLastModified() > latestTimestamp) {
                    state = pls;
                    latestTimestamp = pls.getLastModified();
                }
            }
        }
        return state;
    }

    public PersistentLightState getPersistentLightState(LightState lightState) {
        final PersistentLightState persistentLightState;
        if (lightState != null) {
            persistentLightState = nameToState.get(lightState.getName());
        } else {
            persistentLightState = null;
        }
        return persistentLightState;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        scheduledExecutorService.shutdown();
        Log.i(this.getClass().getSimpleName(), "Destroyed");
    }
}
