package gitlet;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static gitlet.Utils.*;

/**
 * Represent the content inside index file(staging area)
 * Caution: every operation except saveIndex() will not reflect the change
 * to index file, manually invoking is needed after all possible operations.
 */
public class Index implements Serializable {
    /** Staging area for add containing (name, hash) for blobs. */
    private final TreeMap<String, String> addition = new TreeMap<>();
    /** Staging area for rm containing name for blobs. */
    private final TreeSet<String> removal = new TreeSet<>();

    /**
     * Retrieve content from index file
     */
    public static Index fromFile() {
        return readObject(Repo.INDEX_FI, Index.class);
    }

    /**
     * Create or overwrite index file
     */
    public void saveIndex() {
        writeObject(Repo.INDEX_FI, this);
    }

    /**
     * Stage file for addition
     */
    // TODO: the interface need hash as argument? or calculate inside it
    public void stageForAddition(String name, String hash) {
        addition.put(name, hash);
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
     * Integrate files tracked by staging areas into a commit, and clear staging areas
     * @param blobs (name, hash) blob map in commit, will mutate the map
     */
    public void updateBlob(Map<String, String> blobs) {
        blobs.putAll(addition);
        blobs.keySet().removeAll(removal);
        addition.clear();
        removal.clear();
    }
}
