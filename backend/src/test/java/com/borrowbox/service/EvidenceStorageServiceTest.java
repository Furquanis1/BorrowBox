package com.borrowbox.service;

import com.borrowbox.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceStorageServiceTest {

    @TempDir
    Path tempDir;

    private EvidenceStorageService service() {
        return new EvidenceStorageService(tempDir.toString());
    }

    @Test
    void storeWritesUuidOnlyFileNameAndLoadReadsItBack() throws IOException {
        EvidenceStorageService storage = service();

        String ref = storage.store(new byte[]{1, 2, 3});

        assertThat(ref).matches("^[0-9a-fA-F-]{36}$");
        assertThat(storage.load(ref)).containsExactly(1, 2, 3);
        assertThat(Files.readAllBytes(Paths.get(tempDir.toString(), ref))).containsExactly(1, 2, 3);
    }

    @Test
    void deleteRemovesStoredFile() {
        EvidenceStorageService storage = service();
        String ref = storage.store(new byte[]{1});

        storage.delete(ref);

        assertThat(Paths.get(tempDir.toString(), ref)).doesNotExist();
        assertThatThrownBy(() -> storage.load(ref))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteOfMissingOrNonUuidReferenceIsNoOp() {
        EvidenceStorageService storage = service();

        assertThatCode(() -> storage.delete("missing-ref")).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete("../../outside")).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
    }

    @Test
    void loadRejectsNonUuidReference() {
        EvidenceStorageService storage = service();

        assertThatThrownBy(() -> storage.load("../../outside"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> storage.load(null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void loadMissingUuidReferenceThrowsNotFound() {
        EvidenceStorageService storage = service();

        assertThatThrownBy(() -> storage.load("11111111-2222-3333-4444-555555555555"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAlsoCatchesStoreFailuresByLeavingNothingBehind() {
        EvidenceStorageService storage = service();
        String ref = storage.store(new byte[]{7});
        storage.delete(ref);
        assertThat(Paths.get(tempDir.toString(), ref)).doesNotExist();
    }
}