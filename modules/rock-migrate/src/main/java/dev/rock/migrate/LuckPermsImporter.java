package dev.rock.migrate;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ContextSet;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports a LuckPerms SQLite database (luckperms-sqlite.db): groups with
 * weight→priority and prefix meta, group permissions, player permissions
 * (values, server/world contexts, expiry), memberships (group.<name> nodes),
 * and primary groups. The source file is opened read-only and never modified.
 *
 * <p>Mapping notes: LP weight ascends with seniority while ROCK priority
 * descends (lower = higher), so priority = max(0, 1000 - 10·weight). Group
 * temporary permissions have no ROCK API yet and are skipped with a warning.
 */
@RockInternal
@Singleton
public final class LuckPermsImporter implements RmgImporter {

    private static final Logger log = LoggerFactory.getLogger(LuckPermsImporter.class);

    // Sibling-module service: resolved through the registry at run time, never
    // constructor-injected (DIS anti-pattern 4 — modules don't share injectors).
    private final ServiceRegistry services;

    @Inject
    public LuckPermsImporter(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "luckperms";
    }

    @Override
    public String description() {
        return "LuckPerms SQLite database (groups, permissions, contexts, expiry, meta)";
    }

    @Override
    public CompletableFuture<ImportReport> run(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Not a file: " + source);
            }
            PermissionService permissions = services.require(PermissionService.class);
            Map<String, Integer> imported = new TreeMap<>();
            List<String> warnings = new ArrayList<>();
            // Read-only connection to the SOURCE database — this is not
            // platform storage; the TRS DriverManager rule protects our own DB.
            try (Connection lp = DriverManager.getConnection("jdbc:sqlite:" + source + "?open_mode=1")) {
                Map<String, RockGroup> groupsByName = importGroups(lp, permissions, imported, warnings);
                importGroupPermissions(lp, permissions, groupsByName, imported, warnings);
                importPlayers(lp, permissions, groupsByName, imported, warnings);
            } catch (SQLException e) {
                throw new IllegalStateException("LuckPerms import failed: " + e.getMessage(), e);
            }
            permissions.reload().join();
            log.info("LuckPerms import done: {} ({} warning(s))", imported, warnings.size());
            return new ImportReport(imported, warnings);
        });
    }

    private Map<String, RockGroup> importGroups(
            Connection lp, PermissionService permissions, Map<String, Integer> imported, List<String> warnings) throws SQLException {
        Map<String, RockGroup> byName = new HashMap<>();
        Map<String, Integer> weights = new HashMap<>();
        try (Statement st = lp.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT name, permission FROM luckperms_group_permissions WHERE permission LIKE 'weight.%'")) {
            while (rs.next()) {
                try {
                    weights.put(rs.getString(1), Integer.parseInt(rs.getString(2).substring("weight.".length())));
                } catch (NumberFormatException ignored) {
                    warnings.add("Unparseable weight node for group " + rs.getString(1));
                }
            }
        }
        try (Statement st = lp.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM luckperms_groups")) {
            int count = 0;
            while (rs.next()) {
                String name = rs.getString(1);
                int priority = Math.max(0, 1000 - 10 * weights.getOrDefault(name, 0));
                RockGroup group = permissions.createGroup(name, priority).join();
                byName.put(name.toLowerCase(), group);
                count++;
            }
            imported.put("groups", count);
        }
        return byName;
    }

    private void importGroupPermissions(Connection lp, PermissionService permissions, Map<String, RockGroup> groups,
            Map<String, Integer> imported, List<String> warnings) throws SQLException {
        int nodes = 0;
        int meta = 0;
        try (Statement st = lp.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT name, permission, value, server, world, expiry
                        FROM luckperms_group_permissions
                        """)) {
            while (rs.next()) {
                RockGroup group = groups.get(rs.getString("name").toLowerCase());
                if (group == null) {
                    continue;
                }
                String node = rs.getString("permission");
                if (node.startsWith("weight.")) {
                    continue; // already folded into priority
                }
                if (node.startsWith("prefix.") || node.startsWith("suffix.")) {
                    String kind = node.startsWith("prefix.") ? "prefix" : "suffix";
                    String text = node.substring(node.indexOf('.', kind.length() + 1) + 1);
                    permissions.setGroupOption(group.id(), kind, text).join();
                    meta++;
                    continue;
                }
                if (node.startsWith("meta.")) {
                    String[] parts = node.split("\\.", 3);
                    if (parts.length == 3) {
                        permissions.setGroupOption(group.id(), parts[1], parts[2]).join();
                        meta++;
                    }
                    continue;
                }
                if (rs.getLong("expiry") > 0) {
                    warnings.add("Skipped temporary group permission " + node + " on " + group.name()
                            + " (no group-temporary API yet)");
                    continue;
                }
                if (!rs.getBoolean("value")) {
                    warnings.add("Skipped group DENY " + node + " on " + group.name()
                            + " (use player-level deny after import)");
                    continue;
                }
                permissions.grantGroup(group.id(), node, contextOf(rs)).join();
                nodes++;
            }
        }
        imported.put("group-permissions", nodes);
        imported.put("group-meta", meta);
    }

    private void importPlayers(Connection lp, PermissionService permissions, Map<String, RockGroup> groups,
            Map<String, Integer> imported, List<String> warnings) throws SQLException {
        int memberships = 0;
        int playerNodes = 0;
        try (Statement st = lp.createStatement();
                ResultSet rs = st.executeQuery("SELECT uuid, primary_group FROM luckperms_players")) {
            while (rs.next()) {
                RockGroup primary = groups.get(rs.getString("primary_group").toLowerCase());
                if (primary != null) {
                    permissions.assignGroup(UUID.fromString(rs.getString("uuid")), primary.id()).join();
                    memberships++;
                }
            }
        }
        try (Statement st = lp.createStatement();
                ResultSet rs = st.executeQuery("""
                        SELECT uuid, permission, value, server, world, expiry
                        FROM luckperms_user_permissions
                        """)) {
            while (rs.next()) {
                UUID player = UUID.fromString(rs.getString("uuid"));
                String node = rs.getString("permission");
                if (node.startsWith("group.")) {
                    RockGroup group = groups.get(node.substring("group.".length()).toLowerCase());
                    if (group != null) {
                        permissions.assignGroup(player, group.id()).join();
                        memberships++;
                    }
                    continue;
                }
                long expiry = rs.getLong("expiry");
                if (expiry > 0) {
                    Instant expires = Instant.ofEpochSecond(expiry);
                    if (expires.isBefore(Instant.now())) {
                        continue; // already expired — nothing to carry over
                    }
                    permissions.grantTemporary(player, node,
                            Duration.between(Instant.now(), expires)).join();
                    playerNodes++;
                    continue;
                }
                if (rs.getBoolean("value")) {
                    permissions.grant(player, node, contextOf(rs)).join();
                } else {
                    permissions.deny(player, node, contextOf(rs)).join();
                }
                playerNodes++;
            }
        }
        imported.put("memberships", memberships);
        imported.put("player-permissions", playerNodes);
    }

    private static ContextSet contextOf(ResultSet rs) throws SQLException {
        Map<String, String> pairs = new HashMap<>();
        String server = rs.getString("server");
        String world = rs.getString("world");
        if (server != null && !server.isBlank() && !"global".equals(server)) {
            pairs.put("server", server);
        }
        if (world != null && !world.isBlank() && !"global".equals(world)) {
            pairs.put("world", world);
        }
        return pairs.isEmpty() ? ContextSet.empty() : new ContextSet(pairs);
    }
}
