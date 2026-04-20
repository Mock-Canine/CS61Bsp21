package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Main.abort;
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
 *       - remotes/ -- folder for remote repos
 *          - xx/ -- folder named by remote repo
 *             - path -- file containing the path to the remote repo
 *             - xx -- file containing a string of remote branch hash
 *    - HEAD -- file containing the branch name under refs/heads/ head pointer points to
 *    - index -- file(staging area) tracking files for addition or removal
 */
public class FileSystem {
    /** The .gitlet directory. */
    private final File gitlet;
    /** Initial directories of gitlet filesystem */
    private final File objects;
    private final File refs;
    private final File commits;
    private final File blobs;
    private final File heads;
    /** Initial files of gitlet filesystem */
    private final File head;
    private final File index;
    /** directory for remote repos */
    private final File remotes;

    /**
     * Create a gitlet version control system under the root directory
     */
    public FileSystem(File root) {
        gitlet = Utils.join(root, ".gitlet");
        objects = Utils.join(gitlet, "objects");
        refs = Utils.join(gitlet, "refs");
        commits = Utils.join(objects, "commits");
        blobs = Utils.join(objects, "blobs");
        heads = Utils.join(refs, "heads");
        head = Utils.join(gitlet, "HEAD");
        index = Utils.join(gitlet, "index");
        remotes = Utils.join(refs, "remotes");
    }

    /**
     * Init the gitlet filesystem
     */
    public void init() {
        if (gitlet.exists()) {
            abort("A Gitlet version-control system already exists in the current directory.");
        }
        // Create repo folders, order of creating matters
        mkdir(gitlet);
        mkdir(objects);
        mkdir(refs);
        mkdir(commits);
        mkdir(blobs);
        mkdir(heads);
        mkdir(remotes);
        // Create clean staging area
        Index.resetIndex();
    }

    /**
     * Check if in a gitlet repo
     */
    public void isInRepo() {
        if (!gitlet.exists()) {
            abort("Not in an initialized Gitlet directory.");
        }
    }

    /* IO for commit operations, commit hash is the identifier. */
    /**
     * Return the file pointer if input represents a commit
     * @param hash valid format: 4-40 characters long, each represent a
     *             lower case hex number, without any prefix like 0x, 0X, etc.
     * With valid format, it should also indicate a unique commit without ambiguity
     * Abort the program if provide invalid hash
     */
    public File commitPath(String hash) {
        if (!hash.matches("^[0-9a-f]{4,40}$")) {
            abort("No commit with that id exists.");
        }
        int hashLen = 40;
        if (hash.length() == hashLen) {
            File fp = Utils.join(commits, hash);
            if (fp.exists()) {
                return fp;
            } else {
                abort("No commit with that id exists.");
            }
        }
        int num = 0;
        String fullHash = "";
        for (String commitHash : listFiles(commits)) {
            if (commitHash.startsWith(hash)) {
                num++;
                fullHash = commitHash;
            }
        }
        if (num == 1) {
            return Utils.join(commits, fullHash);
        }
        abort("No commit with that id exists.");
        return null;
    }

    /**
     * Save the serialized commit object to filesystem
     */
    public void saveCommit(String hash, byte[] content) {
        File fp = Utils.join(commits, hash);
        Utils.writeContents(fp, (Object) content);
    }

    /**
     * Returns the hash of all commits in the repo
     */
    public List<String> getCommits() {
        return listFiles(commits);
    }

    /**
     * Check whether the 40 character string represents a valid commit in this repo
     */
    public boolean isCommit(String hash) {
        File fp = Utils.join(commits, hash);
        return fp.exists();
    }

    /* IO for index operations */
    public File indexPath() {
        return index;
    }

    /* IO for branch operations */
    /**
     * Update the branch pointer to a commit.
     * Create new branch if not exists.
     * Assume local branchName and valid commitHash.
     */
    public void updateBranch(String branchName, String hash) {
        File fp = Utils.join(heads, branchName);
        Utils.writeContents(fp, hash);
    }

    /**
     * Check if the name represents a valid branch
     * Accept remote branch with the format remoteName/branchName
     */
    public boolean isBranch(String branchName) {
        String[] parsed = branchName.split("/");
        if (parsed.length == 1) {
            // Do not use listFiles(), this is in O(1) time
            return Utils.join(heads, branchName).exists();
        }
        File fp = Utils.join(remotes, parsed[0], parsed[1]);
        return fp.exists();
    }

    /**
     * Return branch hash
     * Assume branchName is valid
     * Accept remote branch with the format remoteName/branchName
     */
    public String getBranch(String branchName) {
        String[] parsed = branchName.split("/");
        File fp;
        if (parsed.length == 1) {
            fp = Utils.join(heads, branchName);
        } else {
            fp = Utils.join(remotes, parsed[0], parsed[1]);
        }
        return Utils.readContentsAsString(fp);
    }

    /**
     * Remove local branch
     * Assume branchName is valid
     */
    public void rmBranch(String branchName) {
        File fp = Utils.join(heads, branchName);
        if (!fp.delete()) {
            abort("Fail to remove a branch.");
        }
    }

    /**
     * Return all the local branch names of this repo
     */
    public List<String> getBranches() {
        return listFiles(heads);
    }

    /**
     * Set head pointer to the branch
     * Assume branchName is valid
     * Accept remote branch with the format remoteName/branchName
     */
    public void setHead(String branchName) {
        Utils.writeContents(head, branchName);
    }

    /**
     * Return the hash of the head of current branch
     */
    public String headHash() {
        return getBranch(head());
    }

    /**
     * Return the branch name head pointer point to
     */
    public String head() {
        return Utils.readContentsAsString(head);
    }

    /* IO for blobs, use blob hash as identifier */
    /**
     * Save the content to blobs
     */
    public void saveBlob(String blobHash, byte[] content) {
        File fp = Utils.join(blobs, blobHash);
        Utils.writeContents(fp, (Object) content);
    }

    /**
     * Return the content of a blob by its hash
     */
    public byte[] getBlob(String blobHash) {
        File fp = Utils.join(blobs, blobHash);
        return Utils.readContents(fp);
    }

    /**
     * Check whether blobHash represents a blob in the repo
     */
    public boolean isBlob(String blobHash) {
        File fp = Utils.join(blobs, blobHash);
        return fp.exists();
    }

    /* IO for remote operations */
    private final String pathFile = "path";

    /**
     * Record the remote repo
     */
    public void addRemote(String remoteName, String path) {
        File fp = Utils.join(remotes, remoteName);
        if (!fp.exists()) {
            mkdir(fp);
        }
        File pp = Utils.join(fp, pathFile);
        Utils.writeContents(pp, path);
    }

    /**
     * Check whether the remote repo has been recorded
     */
    public boolean isRemote(String remoteName) {
        File fp = Utils.join(remotes, remoteName);
        return fp.exists();
    }

    /**
     * Return the path to the remote repo
     * Assume remote repo has been recorded
     */
    public String getRemote(String remoteName) {
        File fp = Utils.join(remotes, remoteName, pathFile);
        return Utils.readContentsAsString(fp);
    }

    /**
     * Unrecord the remote repo
     */
    public void rmRemote(String remoteName) {
        File fp = Utils.join(remotes, remoteName);
        if (fp.exists()) {
            // Can not recursively delete a folder
            for (String f : listFiles(fp)) {
                File subFile = Utils.join(fp, f);
                if (!subFile.delete()) {
                    abort("Fail to remove the remote repo.");
                }
            }
            if (!fp.delete()) {
                abort("Fail to remove the remote repo.");
            }
        }
    }

    /**
     * Update the remote branch to a commit in the local repo
     */
    public void updateRemoteBranch(String remoteName, String branchName, String hash) {
        File fp = Utils.join(remotes, remoteName, branchName);
        Utils.writeContents(fp, hash);
    }

    /**
     * Return the file names inside a folder in the gitlet repo
     */
    private List<String> listFiles(File dir) {
        List<String> files = Utils.plainFilenamesIn(dir);
        if (files == null) {
            abort("File system is broken, init a new gitlet repo!");
        }
        return files;
    }

    /**
     * Create a directory for filesystem
     */
    private void mkdir(File dir) {
        if (!dir.mkdir()) {
            abort("Fail to construct gitlet filesystem");
        }
    }
}
