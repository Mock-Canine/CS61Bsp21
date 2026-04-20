package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

import static gitlet.Repo.REPO;

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

    /* Methods for retrieve commit object from filesystem */
    /**
     * Retrieve a commit object from local repo
     * @param commitHash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     * Abort the program if provide invalid hash
     */
    public static Commit fromFile(String commitHash) {
        return fromFile(commitHash, REPO);
    }

    /**
     * Retrieve a commit object from any valid gitlet repository
     */
    public static Commit fromFile(String commitHash, FileSystem repo) {
        Commit commit = Utils.readObject(repo.commitPath(commitHash), Commit.class);
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
            System.out.print(commit);
            commitHash = commit.parent1Hash;
        }
    }

    /* Constructors */
    /**
     * Constructor for initial commit, no parent
     */
    public Commit(String msg, Date date) {
        this(msg, "", "", new HashMap<>(), date);
    }

    /**
     * Constructor for non-merge commit
     * Provide current commit as its parent
     */
    public Commit(String msg, String parent, Map<String, String> blobs, Date date) {
        this(msg, parent, "", blobs, date);
    }

    /**
     * Constructor for merge commit
     * Provide current commit and mergedIn commit as its two parents in order
     */
    public Commit(String msg, String parent1, String parent2, Map<String, String> blobs, Date date) {
        message = msg;
        parent1Hash = parent1;
        parent2Hash = parent2;
        this.blobs = new HashMap<>(blobs);
        this.date = date;
        byte[] serialized = Utils.serialize(this);
        hash = Utils.sha1((Object) serialized);
        REPO.saveCommit(hash, serialized);
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
    public String getBlobHash(String fileName) {
        // Avoid null return value
        String fileHash =  blobs.get(fileName);
        return fileHash == null ? "" : fileHash;
    }

    /**
     * Return the file names tracked by the commit
     */
    public Set<String> getFiles() {
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

    @Override
    public int hashCode() {
        return hash.hashCode();
    }
}
