package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.GitletIO.CWD;
import static gitlet.Main.abort;
import static gitlet.Utils.sha1;

public class Repo {
    /** Initial branch name */
    public static final String DEFAULT_BRANCH = "master";

    /**
     * Init gitlet repo and make initial commit
     */
    public static void init() {
        GitletIO.initFilesystem();
        GitletIO.setHead(DEFAULT_BRANCH);
        String hash = initialCommit();
        GitletIO.updateBranch(GitletIO.head(), hash);
    }

    /**
     * Add a file to staging area
     */
    public static void add(String fileName) {
        if (!GitletIO.inCWD(fileName)) {
            abort("File does not exist.");
        }
        Commit curr = Commit.fromFile(GitletIO.headHash());
        Index index = Index.fromFile();
        index.unstageForRemoval(fileName);
        if (sameAs(curr, fileName)) {
            index.unstageForAddition(fileName);
        } else {
            index.stageForAddition(fileName);
        }
        index.saveIndex();
    }

    /**
     * Make a commit
     */
    public static void commit(String message) {
        String hash = makeCommit(message, "");
        GitletIO.updateBranch(GitletIO.head(), hash);
    }

    /**
     * Remove a file from staging area
     */
    public static void rm(String fileName) {
        Commit curr = Commit.fromFile(GitletIO.headHash());
        Index index = Index.fromFile();
        boolean isTracked = curr.isTracked(fileName);
        boolean isStaged = index.isStaged(fileName);
        if (!isTracked && !isStaged) {
            abort("No reason to remove the file.");
        }
        index.unstageForAddition(fileName);
        if (isTracked) {
            GitletIO.rmCWD(fileName);
            index.stageForRemoval(fileName);
        }
        index.saveIndex();
    }

    public static void log() {
        Commit.printHistory(GitletIO.headHash());
    }

    public static void globalLog() {
        for (String hash : GitletIO.getCommits()) {
            Commit commit = Commit.fromFile(hash);
            System.out.print(commit);
        }
    }

    public static void find(String message) {
        boolean isFound = false;
        for (String hash : GitletIO.getCommits()) {
            Commit commit = Commit.fromFile(hash);
            if (commit.getMessage().equals(message)) {
                isFound = true;
                System.out.println(hash);
            }
        }
        if (!isFound) {
            abort("Found no commit with that message.");
        }
    }

    public static void status() {
        printBranches();
        printStagingArea();
        printNotStaged();
        printUntracked();
    }

    public static void checkout(String[] args) {
        Map<String, String> params = parseCheckout(args);
        String branchName = params.get("branchName");
        if (branchName == null) {
            String commitHash = params.get("commitHash");
            if (commitHash == null) {
                commitHash = GitletIO.headHash();
            }
            checkoutFile(commitHash, params.get("fileName"));
        } else {
            checkoutBranch(branchName);
        }
    }

    public static void branch(String branchName) {
        if (GitletIO.isBranch(branchName)) {
            abort("A branch with that name already exists.");
        }
        GitletIO.updateBranch(branchName, GitletIO.headHash());
    }

    public static void rmBranch(String branchName) {
        if (!GitletIO.isBranch(branchName)) {
            abort("A branch with that name does not exist.");
        }
        if (GitletIO.head().equals(branchName)) {
            abort("Cannot remove the current branch.");
        }
        GitletIO.rmBranch(branchName);
    }

    public static void reset(String commitHash) {
        replaceCWD(commitHash);
        GitletIO.updateBranch(GitletIO.head(), commitHash);
        Index.resetIndex();
    }

    public static void merge(String branchName) {
        /* Handle exceptions */
        if (!GitletIO.isBranch(branchName)) {
            abort("A branch with that name does not exist.");
        } else if (GitletIO.head().equals(branchName)) {
            abort("Cannot merge a branch with itself.");
        }
        Index index = Index.fromFile();
        if (!index.isEmpty()) {
            abort("You have uncommitted changes.");
        }

        /* Easy cases */
        Commit curr = Commit.fromFile(GitletIO.headHash());
        String branchHash = GitletIO.getBranch(branchName);
        Commit mergedIn = Commit.fromFile(branchHash);
        Commit ancestor = latestAncestor(curr, mergedIn);
        untrackedAbort(curr, mergedIn);
        if (ancestor.equals(mergedIn)) {
            abort("Given branch is an ancestor of the current branch.");
        } else if (ancestor.equals(curr)) {
            reset(branchHash);
            abort("Current branch fast-forwarded.");
        }

        /* Complex case, compare fileHash between the three */
        Set<String> fileNames = new HashSet<>();
        fileNames.addAll(curr.trackedFiles());
        fileNames.addAll(mergedIn.trackedFiles());
        fileNames.addAll(ancestor.trackedFiles());
        boolean hasConflict = false;
        for (String f : fileNames) {
            /* Indicate the pair with same file state */
            boolean sameCurrMerge = sameBlob(curr, mergedIn, f);
            boolean sameCurrAnc = sameBlob(curr, ancestor, f);
            boolean sameMergeAnc = sameBlob(mergedIn, ancestor, f);
            /* Keep current work:
             * Only current branch create/modify/delete the file
             * No change for both of them
             * */
            if (sameMergeAnc) {
                continue;
            }
            /* Take in other branch's work:
             * Only other branch create/modify/delete the file
             * */
            if (sameCurrAnc) {
                if (mergedIn.isTracked(f)) {
                    GitletIO.writeCWD(f, mergedIn.fileHash(f));
                    index.stageForAddition(f);
                } else {
                    GitletIO.rmCWD(f);
                    index.stageForRemoval(f);
                }
            /* Both change the work */
            } else {
                /* Same way change -> nothing to do */
                /* Different way change */
                if (!sameCurrMerge) {
                    conflictFile(curr, mergedIn, f);
                    index.stageForAddition(f);
                    hasConflict = true;
                }

            }
        }
        // Necessary, because manipulating staging area + make commit will happen in one command,
        // but makeCommit() will retrieve index from file
        index.saveIndex();
        String hash = makeCommit("Merged " + branchName + " into " + GitletIO.head() + ".", branchHash);
        GitletIO.updateBranch(GitletIO.head(), hash);
        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * Modify the conflict file content for merge
     */
    private static void conflictFile(Commit curr, Commit other, String fileName) {
        String content = "";
        if (curr.isTracked(fileName)) {
            // Rely on the newline of the file itself
            content += """
                <<<<<<< HEAD
                %s\
                """.formatted(GitletIO.getBlob(curr.fileHash(fileName)));
        } else {
            content += """
                <<<<<<< HEAD
                """;
        }
        if (other.isTracked(fileName)) {
            content += """
                =======
                %s\
                >>>>>>>
                """.formatted(GitletIO.getBlob(other.fileHash(fileName)));
        } else {
            content += """
                =======
                >>>>>>>
                """;
        }
        File fp = Utils.join(CWD, fileName);
        Utils.writeContents(fp, content);
    }

    /**
     * Check whether file contents in two commits are the same or untracked.
     */
    private static boolean sameBlob(Commit one, Commit other, String fileName) {
        return one.fileHash(fileName).equals(other.fileHash(fileName));
    }

    /**
     * Find the latest ancestor for two commits in a commit DAG
     */
    private static Commit latestAncestor(Commit one, Commit other) {
        TreeSet<Commit> ancestorsOfOne = findAncestors(one);
        TreeSet<Commit> ancestorsOfOther = findAncestors(other);
        TreeSet<Commit> commonAncestors = new TreeSet<>(ancestorsOfOne);
        commonAncestors.retainAll(ancestorsOfOther);
        return commonAncestors.first();
    }

    /**
     * Find all the ancestors(include itself) for a commit
     * Return the tree set of its ancestors
     */
    private static TreeSet<Commit> findAncestors(Commit target) {
        // Smallest item in the set has the newest date
        TreeSet<Commit> ancestors = new TreeSet<>(
                Comparator.comparing(Commit::getDate, Comparator.reverseOrder())
        );
        Queue<Commit> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty()) {
            Commit commit = queue.poll();
            ancestors.add(commit);
            for (String parentHash : commit.getParents()) {
                Commit parent = Commit.fromFile(parentHash);
                if (!ancestors.contains(parent)) {
                    queue.add(parent);
                }
            }
        }
        return ancestors;
    }

    /**
     * Replace files in CWD with files tracked by a commit
     */
    private static void replaceCWD(String commitHash) {
        Commit curr = Commit.fromFile(GitletIO.headHash());
        Commit checkout = Commit.fromFile(commitHash);
        untrackedAbort(curr, checkout);
        // Only delete tracked files, not all workdir files(some files not tracked by both commits)
        for (String fileName : curr.trackedFiles()) {
            GitletIO.rmCWD(fileName);
        }
        for (String fileName : checkout.trackedFiles()) {
            GitletIO.writeCWD(fileName, checkout.fileHash(fileName));
        }
    }

    /**
     * Helper method to check untracked files when merging or checkout
     */
    private static void untrackedAbort(Commit curr, Commit other) {
        List<String> workFiles = GitletIO.getCWD();
        for (String fileName : workFiles) {
            if (!curr.isTracked(fileName) && other.isTracked(fileName)) {
                abort("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
    }

    /**
     * Print view of branches, mark current branch with *
     */
    private static void printBranches() {
        List<String> branches = GitletIO.getBranches();
        branches.sort(null);
        String head = GitletIO.head();
        System.out.println("=== Branches ===");
        for (String b : branches) {
            if (b.equals(head)) {
                System.out.println("*" + b);
            } else {
                System.out.println(b);
            }
        }
        System.out.println();
    }

    private static void printStagingArea() {
        Index index = Index.fromFile();
        System.out.print(index);
    }

    private static void printNotStaged() {
        System.out.print("""
                === Modifications Not Staged For Commit ===
                
                """);
    }

    private static void printUntracked() {
        System.out.print("""
                === Untracked Files ===
                
                """);
    }

    /**
     * Create and save initial commit to filesystem
     * Return the newly made commit hash
     */
    private static String initialCommit() {
        Commit commit = new Commit("initial commit");
        commit.save();
        return commit.getHash();
    }

    /**
     * Create and save a commit to filesystem
     * Provide mergedIn branch hash if you do a merge commit, empty string otherwise
     * Return the newly made commit hash
     */
    private static String makeCommit(String message, String mergedIn) {
        if (message.isEmpty()) {
            abort("Please enter a commit message.");
        }
        Commit parent = Commit.fromFile(GitletIO.headHash());
        // Cp content, not reference, this can not be detected by test
        // because the change to parent's blobs will not be saved to file
        Map<String, String> blobs = new HashMap<>(parent.getBlobs());
        Index index = Index.fromFile();
        if (index.isEmpty()) {
            abort("No changes added to the commit.");
        }
        index.commit(blobs);
        index.saveIndex();
        Commit commit;
        if (!mergedIn.isEmpty()) {
            commit = new Commit(message, GitletIO.headHash(), mergedIn, blobs);
        } else {
            commit = new Commit(message, GitletIO.headHash(), blobs);
        }
        commit.save();
        return commit.getHash();
    }

    /**
     * Check whether the file in the CWD is the same as in the commit
     * Assume file exists
     */
    private static boolean sameAs(Commit commit, String fileName) {
        File fp = Utils.join(CWD, fileName);
        byte[] content = Utils.readContents(fp);
        String fileHash = sha1((Object) content);
        String blobHash = commit.fileHash(fileName);
        return fileHash.equals(blobHash);
    }

    /**
     * Parse the arguments for checkout command,
     * returns a map which may contains keys [branchName, commitHash, fileName],
     */
    private static Map<String, String> parseCheckout(String[] args) {
        int len = args.length;
        Map<String, String> map = new HashMap<>();
        if (len == 3 && args[1].equals("--")) {
            map.put("fileName", args[2]);
        } else if (len == 4 && args[2].equals("--")) {
            map.put("commitHash", args[1]);
            map.put("fileName", args[3]);
        } else if (len == 2) {
            map.put("branchName", args[1]);
        } else {
            abort("Incorrect operands.");
        }
        return map;
    }

    private static void checkoutFile(String commitHash, String fileName) {
        Commit commit = Commit.fromFile(commitHash);
        String blobHash = commit.fileHash(fileName);
        if (!commit.isTracked(fileName)) {
            abort("File does not exist in that commit.");
        }
        GitletIO.writeCWD(fileName, blobHash);
    }

    private static void checkoutBranch(String branchName) {
        if (!GitletIO.isBranch(branchName)) {
            abort("No such branch exists.");
        } else if (branchName.equals(GitletIO.head())) {
            abort("No need to checkout the current branch.");
        } else {
            replaceCWD(GitletIO.getBranch(branchName));
            GitletIO.setHead(branchName);
            Index.resetIndex();
        }
    }
}
