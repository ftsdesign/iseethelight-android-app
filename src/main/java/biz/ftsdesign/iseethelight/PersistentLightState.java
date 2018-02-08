package biz.ftsdesign.iseethelight;

import java.io.Serializable;
import java.util.List;

public class PersistentLightState implements Serializable {
    private String name;
    private long lastModified;
    private List<RecorderSeqItem> lastUploadedSequence;
    private String givenName;
    private transient boolean dirty = false;

    public PersistentLightState(String name) {
        this.name = name;
        this.givenName = name;
        onChange();
    }

    private void onChange() {
        lastModified = System.currentTimeMillis();
        dirty = true;
    }

    public void setLastUploadedSequence(List<RecorderSeqItem> lastUploadedSequence) {
        this.lastUploadedSequence = lastUploadedSequence;
        onChange();
    }

    public List<RecorderSeqItem> getLastUploadedSequence() {
        return lastUploadedSequence;
    }

    public String getName() {
        return name;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
        onChange();
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    boolean isDirty() {
        return dirty;
    }

    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
