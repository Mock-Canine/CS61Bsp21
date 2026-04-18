package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Utils.sha1;
import static gitlet.GitletIO.CWD;

/**
 * Represent the staging area
 * Caution: any operation except saveIndex(), resetIndex() will not reflect the change
 * to index file.
 * In other words, after all possible operations of adding/removing files from staging area or
 * making a commit operation, manually saving is needed.
 */
public class Index implements Serializable {
    /** Staging area for add containing (name, hash) for blobs. */
    private final Map<String, String> addition = new HashMap<>();
    /** Staging area for remove containing name for blobs. */
    private final Set<String> removal = new HashSet<>();

    /**
     * Retrieve content from index file
     */
    public static Index fromFile() {
        return GitletIO.getIndex();
    }

    /**
     * Clear staging area to empty and save to file
     */
    public static void resetIndex() {
        Index index = new Index();
        index.saveIndex();
    }

    /**
     * Create or overwrite index file
     */
    public void saveIndex() {
        GitletIO.saveIndex(this);
    }

    /**
     * Stage file for addition
     * Assume file exists in CWD
     */
    public void stageForAddition(String fileName) {
        File fp = Utils.join(CWD, fileName);
        byte[] content = Utils.readContents(fp);
        String hash = sha1((Object) content);
        addition.put(fileName, hash);
        GitletIO.saveBlob(hash, content);
    }

    /**
     * Unstage file for addition
     */
    public void unstageForAddition(String name) {
        addition.remove(name);
    }

    /**
     * Return whether the file has been staged for addition
     */
    public boolean isStaged(String name) {
        return addition.containsKey(name);
    }

    /**
     * Stage file for removal
     */
    public void stageForRemoval(String name) {
        removal.add(name);
    }

    /**
     * Unstage file for removal
     */
    public void unstageForRemoval(String name) {
        removal.remove(name);
    }

    /**
     * Return whether staging area(for addition and removal) is empty
     */
    public boolean isEmpty() {
        return addition.isEmpty() && removal.isEmpty();
    }

    /**
     * Integrate files in staging areas into a commit, and clear staging areas
     * Return the integrated result blobs
     */
    public Map<String, String> commit(Map<String, String> blobs) {
        Map<String, String> newBlobs = new HashMap<>(blobs);
        blobs.putAll(addition);
        blobs.keySet().removeAll(removal);
        addition.clear();
        removal.clear();
        return newBlobs;
    }

    @Override
    public String toString() {
        List<String> staged = new ArrayList<>(addition.keySet());
        List<String> removed = new ArrayList<>(removal);
        staged.sort(null);
        removed.sort(null);
        String content = "";
        if (!staged.isEmpty()) {
            content += """
                === Staged Files ===
                %s
            
                """.formatted(String.join("\n", staged));
        } else {
            content += """
                === Staged Files ===
            
                """;
        }
        if (!removed.isEmpty()) {
            content += """
                === Removed Files ===
                %s
            
                """.formatted(String.join("\n", removed));
        } else {
            content += """
                === Removed Files ===
            
                """;
        }
        return content;
    }
}
