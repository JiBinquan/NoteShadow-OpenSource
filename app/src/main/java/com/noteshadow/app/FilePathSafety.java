package com.noteshadow.app;

import java.io.File;
import java.io.IOException;

/**
 * Small API-23 compatible helpers for validating private storage paths.
 *
 * <p>Do not replace these checks with the newer path API: it was added after
 * the application's minSdk. Canonical paths resolve links while
 * the normalized absolute path retains the lexical path, which lets us spot
 * a link without following it.</p>
 */
final class FilePathSafety {
    private FilePathSafety() { }

    static boolean isSymbolicLink(File file) {
        if (file == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent == null) return false;
            // Canonicalize the parent first so aliases in an ancestor (for example the
            // Windows temporary-directory path used by JVM tests) do not make an ordinary
            // child look like a link. Canonicalizing the reconstructed child then resolves
            // only a link represented by the child itself.
            File childUnderCanonicalParent = new File(parent.getCanonicalFile(), file.getName());
            return !childUnderCanonicalParent.getAbsoluteFile()
                    .equals(childUnderCanonicalParent.getCanonicalFile());
        } catch (IOException e) {
            return true;
        }
    }

    static boolean isDirectChild(File child, File parent) {
        if (child == null || parent == null || isSymbolicLink(child)) return false;
        try {
            File canonicalParent = parent.getCanonicalFile();
            return child.getCanonicalFile().getParentFile().equals(canonicalParent);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isWithin(File root, File candidate) {
        if (root == null || candidate == null) return false;
        try {
            return isWithinCanonical(root.getCanonicalFile(), candidate.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isWithinCanonical(File root, File candidate) {
        String rootPath = root.getPath();
        String candidatePath = candidate.getPath();
        if (rootPath.equals(candidatePath)) return true;
        if (!rootPath.endsWith(File.separator)) rootPath += File.separator;
        return candidatePath.startsWith(rootPath);
    }

    static boolean validContainer(File directory, File expectedParent) {
        if (directory == null || expectedParent == null || isSymbolicLink(directory)) return false;
        try {
            if (!(directory.exists() || directory.mkdirs()) || !directory.isDirectory()) return false;
            return directory.getCanonicalFile().getParentFile()
                    .equals(expectedParent.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** Recursively validates a directory without following links. */
    static boolean safeTree(File root, File parent) {
        if (!isDirectChild(root, parent) || !root.isDirectory()) return false;
        return safeTreeInside(root, root);
    }

    private static boolean safeTreeInside(File file, File root) {
        if (isSymbolicLink(file) || !isWithin(root, file)) return false;
        if (!file.isDirectory()) return file.isFile();
        File[] children = file.listFiles();
        if (children == null) return false;
        for (File child : children) if (!safeTreeInside(child, root)) return false;
        return true;
    }

}
