package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.*;

/**
 * Represents a gitlet commit object.
 * Use SHA-1 hash as the identifier of each commit.
 * Use a map view (fileName, fileHash) to track files
 * Use 0, 1 or 2 parents to indicate initial, normal and merge commit
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private final String message;
    /** The parents of commit. Use empty string for missing parent */
    private final String parent1Hash;
    private final String parent2Hash;
    /** The blobs tracked by the commit */
    private final HashMap<String, String> blobs;
    /** The timestamp of this commit. */
    private final Date date;
    /** The hash of the commit, will be set when retrieved from file or initialized */
    private transient String hash;

    /* Methods for retrieve and save commit object from/to filesystem */
    /**
     * Retrieve a commit object from file
     * @param commitHash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     * Abort the program if provide invalid hash
     */
    public static Commit fromFile(String commitHash) {
        Commit commit = GitletIO.getCommit(commitHash);
        commit.hash = commitHash;
        return commit;
    }

    /**
     * Print history of a commit, if there are multiple parents,
     * just print the history of first parent
     */
    public static void printHistory(String commitHash) {
        while (!commitHash.isEmpty()) {
            Commit commit = Commit.fromFile(commitHash);
            System.out.println(commit);
            commitHash = commit.parent1Hash;
        }
    }

    /**
     * Save the commit
     */
    public void save() {
        byte[] serialized = Utils.serialize(this);
        GitletIO.saveCommit(serialized);
    }

    /* Constructors */
    /**
     * Constructor for initial commit, no parent
     */
    public Commit(String msg) {
        message = msg;
        parent1Hash = "";
        parent2Hash = "";
        blobs = new HashMap<>();
        date = Date.from(Instant.EPOCH);
        assignHash();
    }

    /**
     * Constructor for non-merge commit
     */
    public Commit(String msg, String curr, Map<String, String> blobs) {
        this(msg, curr, "", blobs);
    }

    /**
     * Constructor for merge commit
     * Provide current commit and mergedIn commit as its two parents
     */
    public Commit(String msg, String curr, String mergedIn, Map<String, String> blobs) {
        message = msg;
        parent1Hash = curr;
        parent2Hash = mergedIn;
        this.blobs = new HashMap<>(blobs);
        date = Date.from(Instant.now());
        assignHash();
    }

    /* Observer methods */
    /**
     * Return whether the file is tracked by the commit
     */
    public boolean isTracked(String fileName) {
        return blobs.containsKey(fileName);
    }

    /**
     * Return hash of the file being tracked, empty string if not tracked
     */
    public String fileHash(String fileName) {
        // Avoid null return value
        String fileHash =  blobs.get(fileName);
        return fileHash == null ? "" : fileHash;
    }

    /**
     * Return the file names tracked by the commit
     */
    public Set<String> trackedFiles() {
        // Cp content, not the reference
        return new HashSet<>(blobs.keySet());
    }

    public String getHash() {
        return hash;
    }

    public String getMessage() {
        return message;
    }

    public Date getDate() {
        return date;
    }

    /**
     * Return a map view of files tracked by the commit
     */
    public Map<String, String> getBlobs() {
        return new HashMap<>(blobs);
    }

    /**
     * Return the parents hash
     */
    public List<String> getParents() {
        List<String> parents = new ArrayList<>();
        if (!parent1Hash.isEmpty()) {
            parents.add(parent1Hash);
        }
        if (!parent2Hash.isEmpty()) {
            parents.add(parent2Hash);
        }
        return parents;
    }

    @Override
    public String toString() {
        String dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.ENGLISH).format(date);
        if (parent2Hash.isEmpty()) {
            return """
                    ===
                    commit %s
                    Date: %s
                    %s
                    """.formatted(hash, dateFormat, message);
        } else {
            return """
                    ===
                    commit %s
                    Merge: %s %s
                    Date: %s
                    %s
                    """.formatted(hash, parent1Hash.substring(0, 7), parent2Hash.substring(0, 7), dateFormat, message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Commit)) {
            return false;
        }
        Commit other = (Commit) o;
        return hash.equals(other.hash);
    }

    private void assignHash() {
        byte[] serialized = Utils.serialize(this);
        this.hash = Utils.sha1((Object) serialized);
    }
}
