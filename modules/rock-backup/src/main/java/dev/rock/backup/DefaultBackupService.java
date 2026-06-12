package dev.rock.backup;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.scheduler.Scheduler;
import dev.rock.api.scheduler.TaskHandle;
import dev.rock.api.services.BackupService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zip-archive backups of the ROCK data directory (TRS §14): database, config,
 * everything under the platform root except the backups themselves. Runs on a
 * dedicated virtual thread — never the tick thread.
 */
@RockInternal
@Singleton
public final class DefaultBackupService implements BackupService, dev.rock.api.lifecycle.LifecycleAware {

    static final String BACKUP_DIR = "backups";
    static final String STAGING_DIR = "restore-staging";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static final Logger log = LoggerFactory.getLogger(DefaultBackupService.class);

    private final Path dataDirectory;
    private final Scheduler scheduler;
    private final BackupSettings settings;
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("rock-backup-", 0).factory());
    private TaskHandle scheduledTask;

    @Inject
    public DefaultBackupService(
            @Named("rock.config.dir") Path configDirectory, Scheduler scheduler, BackupSettings settings) {
        // The config dir is <data>/config; backups live beside it under <data>/backups.
        this.dataDirectory = configDirectory.getParent();
        this.scheduler = scheduler;
        this.settings = settings;
    }

    /** Test constructor with an explicit data directory and no schedule. */
    public DefaultBackupService(Path dataDirectory, BackupSettings settings) {
        this.dataDirectory = dataDirectory;
        this.scheduler = null;
        this.settings = settings;
    }

    private Path backupDir() {
        return dataDirectory.resolve(BACKUP_DIR);
    }

    @Override
    public CompletableFuture<BackupInfo> createBackup(String label) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(backupDir());
                String id = "rock-" + STAMP.format(Instant.now()) + "-" + sanitize(label);
                Path archive = backupDir().resolve(id + ".zip");
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    try (Stream<Path> paths = Files.walk(dataDirectory)) {
                        for (Path path : paths.filter(Files::isRegularFile).toList()) {
                            Path relative = dataDirectory.relativize(path);
                            // Never recurse into our own archives or staging output.
                            if (relative.startsWith(BACKUP_DIR) || relative.startsWith(STAGING_DIR)) {
                                continue;
                            }
                            zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                            try (InputStream in = Files.newInputStream(path)) {
                                in.transferTo(zip);
                            }
                            zip.closeEntry();
                        }
                    }
                }
                BackupInfo info = new BackupInfo(id, Instant.now(), Files.size(archive));
                log.info("Backup {} created ({} bytes)", info.id(), info.sizeBytes());
                return info;
            } catch (IOException e) {
                throw new UncheckedIOException("Backup failed", e);
            }
        }, executor);
    }

    private static String sanitize(String label) {
        return label.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    @Override
    public CompletableFuture<List<BackupInfo>> list() {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isDirectory(backupDir())) {
                return List.of();
            }
            try (Stream<Path> paths = Files.list(backupDir())) {
                List<BackupInfo> backups = new ArrayList<>();
                for (Path path : paths.filter(p -> p.toString().endsWith(".zip")).toList()) {
                    String id = path.getFileName().toString().replaceFirst("\\.zip$", "");
                    backups.add(new BackupInfo(id,
                            Files.getLastModifiedTime(path).toInstant(), Files.size(path)));
                }
                backups.sort(Comparator.comparing(BackupInfo::created).reversed());
                return List.copyOf(backups);
            } catch (IOException e) {
                throw new UncheckedIOException("Listing backups failed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> prune(int keep) {
        return list().thenApply(backups -> {
            int removed = 0;
            for (BackupInfo backup : backups.stream().skip(keep).toList()) {
                try {
                    Files.deleteIfExists(backupDir().resolve(backup.id() + ".zip"));
                    removed++;
                } catch (IOException e) {
                    log.error("Could not prune backup {}", backup.id(), e);
                }
            }
            if (removed > 0) {
                log.info("Pruned {} old backup(s), keeping {}", removed, keep);
            }
            return removed;
        });
    }

    @Override
    public CompletableFuture<Path> restoreToStaging(String backupId) {
        return CompletableFuture.supplyAsync(() -> {
            Path archive = backupDir().resolve(backupId + ".zip");
            if (!Files.isRegularFile(archive)) {
                throw new IllegalArgumentException("No backup named " + backupId);
            }
            Path staging = dataDirectory.resolve(STAGING_DIR).resolve(backupId);
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path target = staging.resolve(entry.getName()).normalize();
                    if (!target.startsWith(staging)) {
                        throw new IOException("Archive entry escapes staging dir: " + entry.getName());
                    }
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        zip.transferTo(out);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Restore failed", e);
            }
            log.info("Backup {} unpacked to {} — stop the server and swap to apply", backupId, staging);
            return staging;
        }, executor);
    }

    @Override
    public void onEnable() {
        if (scheduler != null && settings.intervalMinutes() > 0) {
            Duration interval = Duration.ofMinutes(settings.intervalMinutes());
            scheduledTask = scheduler.runRepeating(
                    () -> createBackup("scheduled")
                            .thenCompose(info -> prune(settings.retention()))
                            .exceptionally(e -> {
                                log.error("Scheduled backup failed", e);
                                return null;
                            }),
                    interval, interval);
            log.info("Scheduled backups every {} min (retention {})",
                    settings.intervalMinutes(), settings.retention());
        }
    }

    @Override
    public void onDisable() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
        executor.shutdown();
    }
}
