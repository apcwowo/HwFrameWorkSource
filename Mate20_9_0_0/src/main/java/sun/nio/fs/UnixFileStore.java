package sun.nio.fs;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Properties;

abstract class UnixFileStore extends FileStore {
    private static final Object loadLock = new Object();
    private static volatile Properties props;
    private final long dev;
    private final UnixMountEntry entry;
    private final UnixPath file;

    enum FeatureStatus {
        PRESENT,
        NOT_PRESENT,
        UNKNOWN
    }

    abstract UnixMountEntry findMountEntry() throws IOException;

    private static long devFor(UnixPath file) throws IOException {
        try {
            return UnixFileAttributes.get(file, true).dev();
        } catch (UnixException x) {
            x.rethrowAsIOException(file);
            return 0;
        }
    }

    UnixFileStore(UnixPath file) throws IOException {
        this.file = file;
        this.dev = devFor(file);
        this.entry = findMountEntry();
    }

    UnixFileStore(UnixFileSystem fs, UnixMountEntry entry) throws IOException {
        this.file = new UnixPath(fs, entry.dir());
        this.dev = entry.dev() == 0 ? devFor(this.file) : entry.dev();
        this.entry = entry;
    }

    UnixPath file() {
        return this.file;
    }

    long dev() {
        return this.dev;
    }

    UnixMountEntry entry() {
        return this.entry;
    }

    public String name() {
        return this.entry.name();
    }

    public String type() {
        return this.entry.fstype();
    }

    public boolean isReadOnly() {
        return this.entry.isReadOnly();
    }

    private UnixFileStoreAttributes readAttributes() throws IOException {
        try {
            return UnixFileStoreAttributes.get(this.file);
        } catch (UnixException x) {
            x.rethrowAsIOException(this.file);
            return null;
        }
    }

    public long getTotalSpace() throws IOException {
        UnixFileStoreAttributes attrs = readAttributes();
        return attrs.blockSize() * attrs.totalBlocks();
    }

    public long getUsableSpace() throws IOException {
        UnixFileStoreAttributes attrs = readAttributes();
        return attrs.blockSize() * attrs.availableBlocks();
    }

    public long getUnallocatedSpace() throws IOException {
        UnixFileStoreAttributes attrs = readAttributes();
        return attrs.blockSize() * attrs.freeBlocks();
    }

    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> view) {
        if (view != null) {
            return (FileStoreAttributeView) null;
        }
        throw new NullPointerException();
    }

    public Object getAttribute(String attribute) throws IOException {
        if (attribute.equals("totalSpace")) {
            return Long.valueOf(getTotalSpace());
        }
        if (attribute.equals("usableSpace")) {
            return Long.valueOf(getUsableSpace());
        }
        if (attribute.equals("unallocatedSpace")) {
            return Long.valueOf(getUnallocatedSpace());
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("'");
        stringBuilder.append(attribute);
        stringBuilder.append("' not recognized");
        throw new UnsupportedOperationException(stringBuilder.toString());
    }

    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        if (type != null) {
            boolean z = true;
            if (type == BasicFileAttributeView.class) {
                return true;
            }
            if (type != PosixFileAttributeView.class && type != FileOwnerAttributeView.class) {
                return false;
            }
            if (checkIfFeaturePresent("posix") == FeatureStatus.NOT_PRESENT) {
                z = false;
            }
            return z;
        }
        throw new NullPointerException();
    }

    public boolean supportsFileAttributeView(String name) {
        if (name.equals("basic") || name.equals("unix")) {
            return true;
        }
        if (name.equals("posix")) {
            return supportsFileAttributeView(PosixFileAttributeView.class);
        }
        if (name.equals("owner")) {
            return supportsFileAttributeView(FileOwnerAttributeView.class);
        }
        return false;
    }

    public boolean equals(Object ob) {
        boolean z = true;
        if (ob == this) {
            return true;
        }
        if (!(ob instanceof UnixFileStore)) {
            return false;
        }
        UnixFileStore other = (UnixFileStore) ob;
        if (!(this.dev == other.dev && Arrays.equals(this.entry.dir(), other.entry.dir()))) {
            z = false;
        }
        return z;
    }

    public int hashCode() {
        return ((int) (this.dev ^ (this.dev >>> 32))) ^ Arrays.hashCode(this.entry.dir());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(Util.toString(this.entry.dir()));
        sb.append(" (");
        sb.append(this.entry.name());
        sb.append(")");
        return sb.toString();
    }

    FeatureStatus checkIfFeaturePresent(String feature) {
        if (props == null) {
            synchronized (loadLock) {
                if (props == null) {
                    props = (Properties) AccessController.doPrivileged(new PrivilegedAction<Properties>() {
                        public Properties run() {
                            return UnixFileStore.loadProperties();
                        }
                    });
                }
            }
        }
        String value = props.getProperty(type());
        if (value != null) {
            for (String s : value.split("\\s")) {
                String s2 = s2.trim().toLowerCase();
                if (s2.equals(feature)) {
                    return FeatureStatus.PRESENT;
                }
                if (s2.startsWith("no") && s2.substring(2).equals(feature)) {
                    return FeatureStatus.NOT_PRESENT;
                }
            }
        }
        return FeatureStatus.UNKNOWN;
    }

    private static Properties loadProperties() {
        Properties result = new Properties();
        String fstypes = new StringBuilder();
        fstypes.append(System.getProperty("java.home"));
        fstypes.append("/lib/fstypes.properties");
        ReadableByteChannel rbc;
        try {
            rbc = Files.newByteChannel(Paths.get(fstypes.toString(), new String[0]), new OpenOption[0]);
            result.load(Channels.newReader(rbc, "UTF-8"));
            if (rbc != null) {
                rbc.close();
            }
        } catch (IOException e) {
        } catch (Throwable th) {
            r4.addSuppressed(th);
        }
        return result;
    }
}
