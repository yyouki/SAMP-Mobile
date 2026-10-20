package com.samp.mobile.launcher.util;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/** Imports only required manifest-listed game files from a user-selected local folder. */
public final class LocalGameDataImporter {
    private LocalGameDataImporter() {}

    public static int importMissingFiles(Context context, Uri treeUri,
            ArrayList<String> relativePaths, long[] expectedSizes) throws IOException {
        if (context == null || treeUri == null || relativePaths == null || expectedSizes == null
                || expectedSizes.length != relativePaths.size()) {
            throw new IOException("A source folder and the complete required-file list are needed.");
        }
        File targetRoot = context.getExternalFilesDir(null);
        if (targetRoot == null) throw new IOException("The app's game-data directory is unavailable.");
        if (!targetRoot.exists() && !targetRoot.mkdirs()) throw new IOException("Cannot prepare the app's game-data directory.");

        DocumentFile sourceRoot = DocumentFile.fromTreeUri(context, treeUri);
        if (sourceRoot == null || !sourceRoot.isDirectory()) throw new IOException("The selected source is not a readable folder.");
        String targetPrefix = targetRoot.getCanonicalPath() + File.separator;
        int copiedFiles = 0;

        for (int i = 0; i < relativePaths.size(); i++) {
            String relativePath = normalizeRelativePath(relativePaths.get(i));
            if (relativePath == null || expectedSizes[i] < 0L) continue;

            DocumentFile sourceFile = resolve(sourceRoot, relativePath);
            if (sourceFile == null || !sourceFile.isFile()) continue;
            long sourceLength = sourceFile.length();
            if (sourceLength > 0L && sourceLength != expectedSizes[i]) continue;

            File targetFile = new File(targetRoot, relativePath);
            if (!targetFile.getCanonicalPath().startsWith(targetPrefix)) continue;
            File parent = targetFile.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs()) || !parent.isDirectory()) continue;
            File tempFile = new File(parent, targetFile.getName() + ".local-importing");
            if (tempFile.exists() && !tempFile.delete()) continue;

            long copiedBytes = 0L;
            try (InputStream input = context.getContentResolver().openInputStream(sourceFile.getUri());
                 FileOutputStream output = new FileOutputStream(tempFile, false)) {
                if (input == null) throw new IOException("Cannot open selected file " + relativePath);
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    copiedBytes += count;
                }
                output.flush();
            } catch (IOException e) {
                tempFile.delete();
                continue;
            }

            if (copiedBytes != expectedSizes[i] || !tempFile.isFile()
                    || tempFile.length() != expectedSizes[i]) {
                tempFile.delete();
                continue;
            }
            // Keep a prior destination intact until a complete, size-verified temporary copy exists.
            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete();
                continue;
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete();
                continue;
            }
            copiedFiles++;
        }
        return copiedFiles;
    }

    private static DocumentFile resolve(DocumentFile root, String path) {
        DocumentFile current = root;
        for (String part : path.split("/")) {
            current = current.findFile(part);
            if (current == null) return null;
        }
        return current;
    }

    private static String normalizeRelativePath(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) return null;
        for (String part : normalized.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return null;
        }
        return normalized;
    }
}
