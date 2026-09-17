package com.borrowbox.service;

import com.borrowbox.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * V2.2.6: stores evidence binaries on the server's configured media directory.
 * Each upload is written under a UUID-only filename (no user input reaches the
 * path) and loaded back by that reference. Evidence is immutable; there is no
 * overwrite path, and delete is reserved for rollback cleanup of a store that
 * the owning database transaction did not commit.
 */
@Service
public class EvidenceStorageService {

    private static final Pattern UUID_REF = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final Path root;

    public EvidenceStorageService(@Value("${borrowbox.media.dir:./media}") String mediaDir) {
        this.root = Paths.get(mediaDir);
    }

    public String store(byte[] content) {
        String ref = UUID.randomUUID().toString();
        Path path = root.resolve(ref);
        try {
            Files.createDirectories(root);
            Files.write(path, content);
        } catch (IOException ex) {
            delete(ref);
            throw new IllegalStateException("Could not store evidence", ex);
        }
        return ref;
    }

    public byte[] load(String fileRef) {
        if (fileRef == null || !UUID_REF.matcher(fileRef).matches()) {
            throw new ResourceNotFoundException("Evidence content not found");
        }
        Path path = root.resolve(fileRef);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Evidence content not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read evidence", ex);
        }
    }

    /**
     * Best-effort removal of a stored file. Never reaches beyond the media
     * directory with a user-controlled component because the fileRef is
     * validated as a UUID. Missing files are not an error.
     */
    public void delete(String fileRef) {
        if (fileRef == null || !UUID_REF.matcher(fileRef).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(fileRef));
        } catch (IOException ignored) {
            // Rollback cleanup is best-effort; the file name is a UUID-only
            // component that will never be re-served by a later upload.
        }
    }
}