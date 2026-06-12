package dev.rock.migrate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * RMG (Rock MiGration) importer SPI. Importers read an incumbent's data
 * (read-only — the source is never modified) and replay it through ROCK
 * services, so every imported row passes the same validation, events, and
 * audit trail as live operations.
 */
public interface RmgImporter {

    /** Importer id used by /rock migrate, e.g. "luckperms". */
    String id();

    String description();

    CompletableFuture<ImportReport> run(Path source);

    /**
     * @param imported counts by category, e.g. "groups" → 12
     * @param warnings human-readable skip reasons (unsupported features etc.)
     */
    record ImportReport(java.util.Map<String, Integer> imported, List<String> warnings) {

        public ImportReport {
            imported = java.util.Map.copyOf(imported);
            warnings = List.copyOf(warnings);
        }
    }
}
