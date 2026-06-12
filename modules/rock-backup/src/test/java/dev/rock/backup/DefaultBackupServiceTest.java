package dev.rock.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.services.BackupService.BackupInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultBackupServiceTest {

    @TempDir
    Path dataDir;

    private DefaultBackupService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(dataDir.resolve("config"));
        Files.writeString(dataDir.resolve("rock.db"), "fake-database-bytes");
        Files.writeString(dataDir.resolve("config/rock.toml"), "key = 1\n");
        service = new DefaultBackupService(dataDir, new BackupSettings(0, 5));
    }

    @Test
    void backupRoundTripsThroughStagingRestore() throws Exception {
        BackupInfo info = service.createBackup("test").join();
        assertTrue(info.sizeBytes() > 0);
        assertTrue(info.id().endsWith("-test"));

        Path staging = service.restoreToStaging(info.id()).join();

        assertEquals("fake-database-bytes", Files.readString(staging.resolve("rock.db")));
        assertEquals("key = 1\n", Files.readString(staging.resolve("config/rock.toml")));
    }

    @Test
    void backupsExcludeThemselvesAndStaging() throws Exception {
        service.createBackup("first").join();
        BackupInfo second = service.createBackup("second").join();

        Path staging = service.restoreToStaging(second.id()).join();

        assertTrue(Files.notExists(staging.resolve(DefaultBackupService.BACKUP_DIR)),
                "archives must not contain earlier archives");
        assertTrue(Files.notExists(staging.resolve(DefaultBackupService.STAGING_DIR)));
    }

    @Test
    void listIsNewestFirstAndPruneKeepsTheNewest() throws Exception {
        for (int i = 0; i < 4; i++) {
            service.createBackup("b" + i).join();
            Thread.sleep(15); // distinct mtimes
        }
        List<BackupInfo> all = service.list().join();
        assertEquals(4, all.size());
        assertTrue(all.get(0).created().isAfter(all.get(3).created()) || all.get(0).created().equals(all.get(3).created()));

        int removed = service.prune(2).join();

        assertEquals(2, removed);
        List<BackupInfo> kept = service.list().join();
        assertEquals(2, kept.size());
        assertTrue(kept.getFirst().id().endsWith("-b3"), "newest survives pruning");
    }

    @Test
    void restoringUnknownBackupFails() {
        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.restoreToStaging("nope").join());
        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
    }
}
