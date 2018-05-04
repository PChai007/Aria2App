package com.gianlu.aria2app.NetIO.Aria2;

import android.os.Build;
import android.support.annotation.NonNull;

import com.gianlu.aria2app.NetIO.AbstractClient;
import com.gianlu.commonutils.Adapters.Filterable;
import com.gianlu.commonutils.Logging;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Comparator;
import java.util.Objects;

public class DownloadWithUpdate extends Download implements Filterable<Download.Status> {
    private final Object lock = new Object();
    private SmallUpdate update;

    private DownloadWithUpdate(@NonNull String gid, @NonNull AbstractClient client) {
        super(gid, client);
    }

    @NonNull
    public static DownloadWithUpdate create(AbstractClient client, JSONObject obj, boolean small) throws JSONException {
        DownloadWithUpdate download = new DownloadWithUpdate(obj.getString("gid"), client);
        download.update(obj, small);
        return download;
    }

    @NonNull
    public DownloadWithUpdate update(JSONObject obj, boolean small) throws JSONException {
        synchronized (lock) {
            if (small) this.update = new SmallUpdate(obj);
            else this.update = new BigUpdate(obj);
            return this;
        }
    }

    public void update(@NonNull SmallUpdate update) {
        synchronized (lock) {
            this.update = update;
        }
    }

    @NonNull
    public SmallUpdate update() {
        synchronized (lock) {
            return update;
        }
    }

    @Override
    @NonNull
    public Status getFilterable() {
        return update().status;
    }

    @NonNull
    public BigUpdate bigUpdate() {
        return (BigUpdate) update;
    }

    private abstract static class UpdateComparator implements Comparator<DownloadWithUpdate> {

        @Override
        public final int compare(DownloadWithUpdate o1, DownloadWithUpdate o2) {
            return compare(o1.update(), o2.update());
        }

        protected abstract int compare(SmallUpdate o1, SmallUpdate o2);
    }

    public static class StatusComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            if (o1.status == o2.status) return 0;
            else if (o1.status.ordinal() < o2.status.ordinal()) return -1;
            else return 1;
        }
    }

    public static class DownloadSpeedComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            if (Objects.equals(o1.downloadSpeed, o2.downloadSpeed)) return 0;
            else if (o1.downloadSpeed > o2.downloadSpeed) return -1;
            else return 1;
        }
    }

    public static class UploadSpeedComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            if (Objects.equals(o1.uploadSpeed, o2.uploadSpeed)) return 0;
            else if (o1.uploadSpeed > o2.uploadSpeed) return -1;
            else return 1;
        }
    }

    public static class LengthComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            if (Objects.equals(o1.length, o2.length)) return 0;
            else if (o1.length > o2.length) return -1;
            else return 1;
        }
    }

    public static class NameComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            return o1.getName().compareToIgnoreCase(o2.getName());
        }
    }

    public static class CompletedLengthComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            if (Objects.equals(o1.completedLength, o2.completedLength)) return 0;
            else if (o1.completedLength > o2.completedLength) return -1;
            else return 1;
        }
    }

    public static class ProgressComparator extends UpdateComparator {
        @Override
        public int compare(SmallUpdate o1, SmallUpdate o2) {
            return Integer.compare((int) o2.getProgress(), (int) o1.getProgress());
        }
    }

    public class BigUpdate extends SmallUpdate {
        public final String bitfield;
        public final long verifiedLength;
        public final boolean verifyIntegrityPending;

        // BitTorrent only
        public final boolean seeder;
        public final String infoHash;

        BigUpdate(JSONObject obj) throws JSONException {
            super(obj);

            // Optional
            bitfield = obj.optString("bitfield", null);
            verifiedLength = obj.optLong("verifiedLength", 0);
            verifyIntegrityPending = obj.optBoolean("verifyIntegrityPending", false);

            if (isTorrent()) {
                infoHash = obj.getString("infoHash");
                seeder = obj.optBoolean("seeder", false);
            } else {
                seeder = false;
                infoHash = null;
            }
        }
    }

    public class SmallUpdate {
        public final long completedLength;
        public final long uploadLength;
        public final int connections;
        public final Download.Status status;
        public final int downloadSpeed;
        public final int uploadSpeed;
        public final AriaFiles files;
        public final int errorCode;
        public final String errorMessage;
        public final String followedBy;
        public final String dir;
        public final int numPieces;
        public final long pieceLength;
        public final long length;

        // BitTorrent only
        public final int numSeeders;
        public final String following;
        public final String belongsTo;
        public final BitTorrent torrent;

        private String name = null;

        SmallUpdate(JSONObject obj) throws JSONException {
            length = obj.getLong("totalLength");
            pieceLength = obj.getLong("pieceLength");
            numPieces = obj.getInt("numPieces");
            dir = obj.getString("dir");
            torrent = BitTorrent.create(obj);

            try {
                status = Status.parse(obj.getString("status"));
            } catch (ParseException ex) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) throw new JSONException(ex);
                else throw new JSONException(ex.getMessage());
            }

            completedLength = obj.getLong("completedLength");
            uploadLength = obj.getLong("uploadLength");
            downloadSpeed = obj.getInt("downloadSpeed");
            uploadSpeed = obj.getInt("uploadSpeed");
            connections = obj.getInt("connections");
            files = new AriaFiles(obj.getJSONArray("files"));

            // Optional
            followedBy = obj.optString("followedBy", null);
            following = obj.optString("following", null);
            belongsTo = obj.optString("belongsTo", null);


            if (isTorrent()) numSeeders = obj.getInt("numSeeders");
            else numSeeders = 0;

            if (obj.has("errorCode")) {
                errorCode = obj.getInt("errorCode");
                errorMessage = obj.optString("errorMessage", null);
            } else {
                errorCode = -1;
                errorMessage = null;
            }
        }

        @NonNull
        public DownloadWithUpdate download() {
            return DownloadWithUpdate.this;
        }

        public boolean isTorrent() {
            return torrent != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Download download = (Download) o;
            return gid.equals(download.gid);
        }

        public float shareRatio() {
            if (completedLength == 0) return 0f;
            return ((float) uploadLength) / ((float) completedLength);
        }

        @NonNull
        public String getName() {
            if (name == null) name = getNameInternal();
            return name;
        }

        public boolean isMetadata() {
            return getName().startsWith("[METADATA]");
        }

        @NonNull
        private String getNameInternal() {
            try {
                if (torrent != null && torrent.name != null) return torrent.name;
                String[] splitted = files.get(0).path.split("/");
                if (splitted.length >= 1) return splitted[splitted.length - 1];
            } catch (Exception ex) {
                Logging.log(ex);
            }

            return "Unknown";
        }

        public float getProgress() {
            return ((float) completedLength) / ((float) length) * 100;
        }

        public long getMissingTime() {
            if (downloadSpeed == 0) return 0;
            return (length - completedLength) / downloadSpeed;
        }

        public boolean canDeselectFiles() {
            return isTorrent() && files.size() > 1 && status != Status.REMOVED && status != Status.ERROR;
        }
    }
}
