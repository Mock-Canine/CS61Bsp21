package gitlet;

import java.io.File;
import java.util.List;
import static gitlet.Main.abort;
import static gitlet.Utils.sha1;

/** Represents gitlet repository filesystem and provide IO operations
 * .gitlet/ filesystem
 * .gitlet/ -- gitlet repository
 *    - objects/ -- folder containing blobs and commits
 *       - commits/ -- folder containing serialized commits named by its hash
 *       - blobs/ -- folder containing blobs named by its hash
 *    - refs/ -- folder tracking the local and remote branch header
 *       - heads/ -- folder tracking local branch header
 *          - master -- file containing a string of branch hash
 *          - xx -- other branches
 *    - HEAD -- file containing the branch name under refs/heads/ head pointer points to
 *    - index -- file(staging area) tracking files for addition or removal
 */
public class GitletIO {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    private static final File GITLET = Utils.join(CWD, ".gitlet");
    /** Initial directories of gitlet filesystem */
    private static final File OBJECTS = Utils.join(GITLET, "objects");
    private static final File REFS = Utils.join(GITLET, "refs");
    private static final File COMMITS = Utils.join(OBJECTS, "commits");
    private static final File BLOBS = Utils.join(OBJECTS, "blobs");
    private static final File HEADS = Utils.join(REFS, "heads");
    /** Initial files of gitlet filesystem */
    private static final File HEAD = Utils.join(GITLET, "HEAD");
    private static final File INDEX = Utils.join(GITLET, "index");


    /**
     * Init the gitlet filesystem
     */
    public static void initFilesystem() {
        if (GITLET.exists()) {
            abort("A Gitlet version-control system already exists in the current directory.");
        }
        // Create required folders, order of creating matters
        mkdir(GITLET);
        mkdir(OBJECTS);
        mkdir(REFS);
        mkdir(COMMITS);
        mkdir(BLOBS);
        mkdir(HEADS);
        // Create clean staging area
        Index.resetIndex();
    }

    /**
     * Check if in a gitlet repo
     */
    public static void isInRepo() {
        if (!GITLET.exists()) {
            abort("Not in an initialized Gitlet directory.");
        }
    }

    /* IO for commit operations, call the corresponding version in Commit to get and save */
    private static final int HASH_LEN = 40;

    /**
     * Retrieve a commit object from file
     * @param commitHash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     * Abort the program if provide invalid hash
     */
    public static Commit getCommit(String commitHash) {
        File fp = parsePath(commitHash);
        if (fp == null) {
            abort("No commit with that id exists.");
        }
        return Utils.readObject(fp, Commit.class);
    }

    /**
     * Return the file pointer if input represents a commit, null otherwise
     * @param hash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     */
    private static File parsePath(String hash) {
        if (!hash.matches("^[0-9a-f]{4,40}$")) {
            return null;
        }
        if (hash.length() == HASH_LEN) {
            File fp = Utils.join(COMMITS, hash);
            return fp.exists() ? fp : null;
        }
        int num = 0;
        String fullHash = null;
        for (String commitHash : listFiles(COMMITS)) {
            if (commitHash.startsWith(hash)) {
                num++;
                fullHash = commitHash;
            }
        }
        if (num == 1) {
            return Utils.join(COMMITS, fullHash);
        }
        return null;
    }

    /**
     * Save the serialized commit object to filesystem
     */
    public static void saveCommit(byte[] content) {
        // Only need the content as param, use hash as the name is just the design choice
        String hash = sha1((Object) content);
        File fp = Utils.join(COMMITS, hash);
        Utils.writeContents(fp, (Object) content);
    }

    /**
     * Returns the hash of all commits in the repo
     */
    public static List<String> getCommits() {
        return listFiles(COMMITS);
    }

    /* IO for index operations, call the corresponding version in Index to get and save */
    public static Index getIndex() {
        return Utils.readObject(INDEX, Index.class);
    }

    public static void saveIndex(Index index) {
        Utils.writeObject(INDEX, index);
    }

    /* IO for branch operations */
    /**
     * Update the branch pointer to a commit.
     * Create new branch if not exists.
     * Assume valid commitHash.
     */
    public static void updateBranch(String branchName, String commitHash) {
        File fp = Utils.join(HEADS, branchName);
        Utils.writeContents(fp, commitHash);
    }

    /**
     * Check if the name represents a valid branch
     */
    public static boolean isBranch(String branchName) {
        // Do not use listFiles(), this is in O(1) time
        return Utils.join(HEADS, branchName).exists();
    }

    /**
     * Return branch hash
     * Assume branchName is valid
     */
    public static String getBranch(String branchName) {
        File fp = Utils.join(HEADS, branchName);
        return Utils.readContentsAsString(fp);
    }

    /**
     * Remove branch
     * Assume branchName is valid
     */
    public static void rmBranch(String branchName) {
        File fp = Utils.join(HEADS, branchName);
        fp.delete();
    }

    /**
     * Return all the branch names of this repo
     */
    public static List<String> getBranches() {
        return listFiles(HEADS);
    }

    /**
     * Set head pointer to the branch
     * Assume branchName is valid
     */
    public static void setHead(String branchName) {
        Utils.writeContents(HEAD, branchName);
    }

    /**
     * Return the hash of the head of current branch
     */
    public static String headHash() {
        return getBranch(head());
    }

    /**
     * Return the branch name head pointer point to
     */
    public static String head() {
        return Utils.readContentsAsString(HEAD);
    }

    /* IO for blobs and working directory files */
    /**
     * Check whether the file is in the CWD
     */
    public static boolean inCWD(String fileName) {
        File fp = Utils.join(CWD, fileName);
        return fp.exists();
    }

    /**
     * remove file from the CWD
     */
    public static void rmCWD(String fileName) {
        File fp = Utils.join(CWD, fileName);
        Utils.restrictedDelete(fp);
    }

    /**
     * create or overwrite file in the CWD with a file tracked by a commit
     */
    public static void writeCWD(String fileName, String blobHash) {
        File bp = Utils.join(BLOBS, blobHash);
        File fp = Utils.join(CWD, fileName);
        Utils.writeContents(fp, (Object) Utils.readContents(bp));
    }

    /**
     * get files in the CWD
     */
    public static List<String> getCWD() {
        return listFiles(CWD);
    }

    /**
     * Save the file in CWD to blobs
     */
    public static void saveBlob(String fileHash, byte[] content) {
        File blob = Utils.join(BLOBS, fileHash);
        Utils.writeContents(blob, (Object) content);
    }

    /**
     * Return the content of a blob by its hash
     */
    public static String getBlob(String blobHash) {
        File fp = Utils.join(BLOBS, blobHash);
        return Utils.readContentsAsString(fp);
    }

    /**
     * Return the file names inside a folder in the gitlet repo
     */
    private static List<String> listFiles(File dir) {
        List<String> files = Utils.plainFilenamesIn(dir);
        if (files == null) {
            abort("File system is broken, init a new gitlet repo!");
        }
        return files;
    }

    /**
     * Create a directory for filesystem
     */
    private static void mkdir(File dir) {
        if (!dir.mkdir()) {
            abort("Fail to construct gitlet filesystem");
        }
    }

}
