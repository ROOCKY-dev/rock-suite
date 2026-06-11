package dev.rock.api.module;

import java.util.List;
import java.util.Objects;

/**
 * Module identity and dependency declaration (RPS §3).
 *
 * @param id           unique module id, e.g. {@code rock-claims}
 * @param name         display name
 * @param version      module version (official modules follow the platform version)
 * @param apiVersion   rock-api major.minor this module was built against
 * @param authors      module authors
 * @param dependencies module ids that must be RUNNING before this module starts
 */
public record ModuleManifest(
        String id,
        String name,
        String version,
        String apiVersion,
        List<String> authors,
        List<String> dependencies) {

    public ModuleManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(apiVersion, "apiVersion");
        authors = List.copyOf(authors);
        dependencies = List.copyOf(dependencies);
    }
}
