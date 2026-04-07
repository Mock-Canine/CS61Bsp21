package gitlet;

import java.io.File;
import java.util.Map;

import static gitlet.Utils.*;

// TODO: try modify the objects/ to mimic git later
/** Represents a gitlet repository and manipulates file operations
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
public class Repo {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The objects, refs directory and HEAD, index file. */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File HEAD_FI = join(GITLET_DIR, "HEAD");
    public static final File INDEX_FI = join(GITLET_DIR, "index");
    /** The subdirectories of objects/ and refs/. */
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");

    /**
     * Init .gitlet filesystem and make initial commit
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            message("A Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }
        if (!GITLET_DIR.mkdir() || !OBJECTS_DIR.mkdir() || !COMMITS_DIR.mkdir() || !BLOBS_DIR.mkdir()
            || !REFS_DIR.mkdir() || !HEADS_DIR.mkdir()) {
            message("Fail to construct gitlet filesystem");
            System.exit(0);
        }
        // Default HEAD, point to branch master
        // TODO: may be use a func for switch to handle HEAD file
        writeContents(Repo.HEAD_FI, "master");
        // Build empty staging area
        Index index = new Index();
        index.saveIndex();
        makeCommit("initial commit");
    }

    public static void add(String f) {
        isInRepo();
        File file = join(CWD, f);
        if (!file.exists()) {
            message("File does not exist.");
            System.exit(0);
        }
        Commit curr = Commit.fromFile(Commit.headHash());
        Index index = Index.fromFile();
        byte[] content = readContents(file);
        String fileHash = sha1((Object) content);
        // File may not be tracked by the commit
        String blobHash = curr.blobHash(f);
        // do not use state machine, use rule-based method
        // And the ADT often support both create or overwrite operation in one function(like remove)
        index.unstageForRemoval(f);
        if (fileHash.equals(blobHash)) {
            index.unstageForAddition(f);
        } else {
            index.stageForAddition(f, fileHash);
            File blob = join(BLOBS_DIR, fileHash);
            writeContents(blob, (Object) content);
        }
        index.saveIndex();
    }

    public static void commit(String message) {
        isInRepo();
        makeCommit(message);
    }

    public static void rm(String f) {
        isInRepo();
        Commit curr = Commit.fromFile(Commit.headHash());
        Index index = Index.fromFile();
        boolean inCommit = curr.inCommit(f);
        boolean stageForAddition = index.isStaged(f);
        if (!inCommit && !stageForAddition) {
            message("No reason to remove the file.");
            System.exit(0);
        }
        index.unstageForAddition(f);
        if (inCommit) {
            // Remove from work dir
            restrictedDelete(f);
            index.stageForRemoval(f);
        }
        // TODO: saveIndex every time is like bad feeling
        index.saveIndex();
    }

    public static void log() {
        isInRepo();
        Commit.printHistory(Commit.headHash());
    }

    public static void globalLog() {
        isInRepo();
        for (String name : plainFilenamesIn(COMMITS_DIR)) {
            Commit commit = Commit.fromFile(name);
            System.out.println(commit);
        }
    }

    /**
     * Handle three usages of checkout, input params must be valid for usages
     * takes a map which may contains keys [branchName, commitId, fileName],
     */
    public static void checkout(Map<String, String> args) {
        // TODO: handle branch later
        isInRepo();
        if (args.get("branchName") == null) {
            String hash = args.get("commitId");
            if (hash == null) {
                hash = Commit.headHash();
            }
            String name = args.get("fileName");
            Commit commit = Commit.fromFile(hash);
            String blobHash = commit.blobHash(name);
            if (blobHash == null) {
                message("File does not exist in that commit.");
                System.exit(0);
            }
            File blob = join(BLOBS_DIR, blobHash);
            File file = join(CWD, name);
            writeContents(file, (Object) readContents(blob));
        }
    }

    /**
     * Create a commit object and save it to gitlet filesystem
     */
    private static void makeCommit(String message) {
        new Commit(message);
    }

    private static void isInRepo() {
        if (!GITLET_DIR.exists()) {
            message("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
}
