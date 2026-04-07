package gitlet;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.TreeMap;

import static gitlet.Utils.*;

/**
 * Represents a gitlet commit object.
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private final String message;
    /** The timestamp of this commit. */
    private final Date date;
    // TODO: handle multiple parents here
    /** The parents of commit. */
    // public transient Commit parent;
    private final String parentHash;
    /** The hash of the commit, will be set when initialized or retrieved from file */
    private transient String myHash;
    /** the branch of the commit */
    private final String branch;
    /** The blobs tracked by the commit */
    private final TreeMap<String, String> blobs;

    /**
     * Retrieve a commit object from file
     * @param hash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     * Abort the program if provide invalid hash
     */
    public static Commit fromFile(String hash) {
        if (fullHash(hash) == null) {
            message("No commit with that id exists.");
            System.exit(0);
        }
        // Retrieve it from objects/commits/
        File name = join(Repo.COMMITS_DIR, hash);
        Commit commit = readObject(name, Commit.class);
        commit.myHash = hash;
        return commit;
    }

    /**
     * Return the hash of the head of current branch
     */
    public static String headHash() {
        // Return the branch name under refs/heads/ HEAD points to
        String branch = readContentsAsString(Repo.HEAD_FI);
        // Return the branch hash
        File head = join(Repo.HEADS_DIR, branch);
        return readContentsAsString(head);
    }

    /**
     * Print history a commit, if there are multiple branches,
     * just print the history of current branch and extra merge information
     * @param hash valid commit hash
     */
    public static void printHistory(String hash) {
        // TODO: add logic for merge later
        // Hit initial commit's parent
        while (!hash.isEmpty()) {
            Commit commit = fromFile(hash);
            System.out.println(commit);
            hash = commit.parentHash;
        }
    }

    /**
     * Return the 40 character hash if input represents a commit, null otherwise
     * @param hash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     */
    private static String fullHash(String hash) {
        if (!hash.matches("^[0-9a-f]{4,40}$")) {
            return null;
        }
        int num = 0;
        String fullHash = null;
        for (String f : plainFilenamesIn(Repo.COMMITS_DIR)) {
            if (f.startsWith(hash)) {
                num++;
                fullHash = f;
            }
        }
        if (num == 1) {
            return fullHash;
        }
        return null;
    }

    /**
     * Create a commit and save it in the filesystem
     */
    public Commit(String message) {
        if (message.isEmpty()) {
            message("Please enter a commit message.");
            System.exit(0);
        }
        this.message = message;
        // If no branch inside heads/, treat as initial commit
        if (plainFilenamesIn(Repo.HEADS_DIR).isEmpty()) {
            parentHash = "";
            blobs = new TreeMap<>();
            date = Date.from(Instant.EPOCH);
            branch = "master";
        } else {
            Commit parent = fromFile(headHash());
            parentHash = parent.myHash;
            // Update blobs
            blobs = parent.blobs;
            Index index = Index.fromFile();
            if (index.isEmpty()) {
                message("No changes added to the commit.");
                System.exit(0);
            }
            index.updateBlob(blobs);
            index.saveIndex();
            date = Date.from(Instant.now());
            branch = parent.branch;
        }
        // TODO: may be separate
        saveCommit();
    }

    /**
     * Return whether the file tracked by the commit
     */
    public boolean inCommit(String name) {
        return blobs.containsKey(name);
    }

    /**
     * Return hash of the file being tracked, null if not being tracked
     */
    public String blobHash(String name) {
        return blobs.get(name);
    }

    /**
     * Return the hash of this commit
     */
    public String hash() {
        return myHash;
    }

    @Override
    public String toString() {
        // TODO: add logic for merge later
        return """
            ===
            commit %s
            Date: %ta %<tb %<te %<tT %<tY %<tz
            %s
            """.formatted(myHash, date, message);
    }

    /**
     * Save the commit to objects/commits/, update the hash in the
     * refs/heads/branch, and assign the hash field of this commit
     */
    // TODO: this may be set private, as long as there is no mutator for the class
    // TODO: this func does to many things, may shorter
    private void saveCommit() {
        byte[] serialized = serialize(this);
        String hash = sha1((Object) serialized);
        myHash = hash;
        // Use hash as the file name
        File content = join(Repo.COMMITS_DIR, hash);
        writeContents(content, (Object) serialized);
        // Create or overwrite the branch pointer
        File head = join(Repo.HEADS_DIR, branch);
        writeContents(head, hash);
    }

}
