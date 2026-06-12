package dev.rock.teams;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.RockTeam;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.team.TeamCreatedEvent;
import dev.rock.api.events.team.TeamDisbandedEvent;
import dev.rock.api.events.team.TeamMemberJoinedEvent;
import dev.rock.api.events.team.TeamMemberLeftEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.TeamService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team identity engine (FTB Teams / Argonauts answer). Backed by DataService;
 * an in-memory membership cache serves tick-thread reads — claims protection
 * consults it on every block change in team-owned claims.
 */
@RockInternal
@Singleton
public final class DefaultTeamService implements TeamService, LifecycleAware {

    private static final RowMapper<RockTeam> TEAM_MAPPER = row -> new RockTeam(
            row.getUuid("id"), row.getString("name"), row.getInstant("created"), row.getInstant("deleted_at"));

    private final DataService data;
    private final EventBus eventBus;

    // playerId → teamId; teamId → (playerId → role); teamId → team
    private final Map<UUID, UUID> teamByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, TeamRole>> membersByTeam = new ConcurrentHashMap<>();
    private final Map<UUID, RockTeam> teams = new ConcurrentHashMap<>();

    @Inject
    public DefaultTeamService(DataService data, EventBus eventBus) {
        this.data = data;
        this.eventBus = eventBus;
    }

    /** Raised when a team operation cannot proceed. */
    public static final class TeamException extends RuntimeException {
        public TeamException(String message) {
            super(message);
        }
    }

    @Override
    public void onEnable() {
        Map<UUID, RockTeam> loadedTeams = new HashMap<>();
        data.query("SELECT * FROM rock_teams WHERE deleted_at IS NULL", Map.of(), TEAM_MAPPER).join()
                .forEach(team -> loadedTeams.put(team.id(), team));
        teams.putAll(loadedTeams);

        data.query("SELECT team_id, player_id, role FROM rock_team_members", Map.of(), row -> new Object[] {
                row.getUuid("team_id"), row.getUuid("player_id"), TeamRole.valueOf(row.getString("role"))})
                .join()
                .forEach(row -> cacheMember((UUID) row[0], (UUID) row[1], (TeamRole) row[2]));
    }

    @Override
    public void onDisable() {
        teamByPlayer.clear();
        membersByTeam.clear();
        teams.clear();
    }

    private void cacheMember(UUID teamId, UUID playerId, TeamRole role) {
        teamByPlayer.put(playerId, teamId);
        membersByTeam.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>()).put(playerId, role);
    }

    private void uncacheMember(UUID teamId, UUID playerId) {
        teamByPlayer.remove(playerId);
        Map<UUID, TeamRole> members = membersByTeam.get(teamId);
        if (members != null) {
            members.remove(playerId);
        }
    }

    @Override
    public CompletableFuture<RockTeam> create(String name, UUID leader) {
        if (teamByPlayer.containsKey(leader)) {
            return CompletableFuture.failedFuture(new TeamException("Player already belongs to a team"));
        }
        RockTeam team = new RockTeam(UUID.randomUUID(), name, Instant.now(), null);
        return data.inTransaction(tx -> {
            boolean nameTaken = tx.queryOne("SELECT id FROM rock_teams WHERE name = :name AND deleted_at IS NULL",
                    Map.of("name", name), row -> row.getString("id")).isPresent();
            if (nameTaken) {
                throw new TeamException("Team name already taken: " + name);
            }
            tx.update("INSERT INTO rock_teams (id, name, created, deleted_at) VALUES (:id, :name, :created, NULL)",
                    Map.of("id", team.id().toString(), "name", name, "created", team.created().toEpochMilli()));
            tx.update("INSERT INTO rock_team_members (team_id, player_id, role) VALUES (:t, :p, :r)",
                    Map.of("t", team.id().toString(), "p", leader.toString(), "r", TeamRole.LEADER.name()));
            return team;
        }).thenApply(created -> {
            teams.put(created.id(), created);
            cacheMember(created.id(), leader, TeamRole.LEADER);
            eventBus.publish(new TeamCreatedEvent(created, leader));
            return created;
        });
    }

    @Override
    public CompletableFuture<Optional<RockTeam>> findByName(String name) {
        return data.queryOne("SELECT * FROM rock_teams WHERE name = :name AND deleted_at IS NULL",
                Map.of("name", name), TEAM_MAPPER);
    }

    @Override
    public CompletableFuture<Optional<RockTeam>> findById(UUID teamId) {
        return data.queryOne("SELECT * FROM rock_teams WHERE id = :id", Map.of("id", teamId.toString()), TEAM_MAPPER);
    }

    @Override
    public CompletableFuture<Void> addMember(UUID teamId, UUID playerId, TeamRole role) {
        RockTeam team = activeTeam(teamId);
        UUID existing = teamByPlayer.get(playerId);
        if (existing != null && !existing.equals(teamId)) {
            return CompletableFuture.failedFuture(new TeamException("Player already belongs to another team"));
        }
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_team_members WHERE player_id = :p", Map.of("p", playerId.toString()));
            tx.update("INSERT INTO rock_team_members (team_id, player_id, role) VALUES (:t, :p, :r)",
                    Map.of("t", teamId.toString(), "p", playerId.toString(), "r", role.name()));
            return null;
        }).thenRun(() -> {
            cacheMember(teamId, playerId, role);
            eventBus.publish(new TeamMemberJoinedEvent(team, playerId, role));
        });
    }

    @Override
    public CompletableFuture<Void> removeMember(UUID teamId, UUID playerId) {
        RockTeam team = activeTeam(teamId);
        return data.update("DELETE FROM rock_team_members WHERE team_id = :t AND player_id = :p",
                Map.of("t", teamId.toString(), "p", playerId.toString()))
                .thenAccept(rows -> {
                    if (rows > 0) {
                        uncacheMember(teamId, playerId);
                        eventBus.publish(new TeamMemberLeftEvent(team, playerId));
                    }
                });
    }

    @Override
    public CompletableFuture<Map<UUID, TeamRole>> membersOf(UUID teamId) {
        return data.query("SELECT player_id, role FROM rock_team_members WHERE team_id = :t",
                Map.of("t", teamId.toString()),
                row -> Map.entry(row.getUuid("player_id"), TeamRole.valueOf(row.getString("role"))))
                .thenApply(entries -> {
                    Map<UUID, TeamRole> members = new HashMap<>();
                    entries.forEach(e -> members.put(e.getKey(), e.getValue()));
                    return members;
                });
    }

    @Override
    public CompletableFuture<Void> disband(UUID teamId) {
        RockTeam team = activeTeam(teamId);
        return data.inTransaction(tx -> {
            tx.update("UPDATE rock_teams SET deleted_at = :now WHERE id = :id",
                    Map.of("now", Instant.now().toEpochMilli(), "id", teamId.toString()));
            tx.update("DELETE FROM rock_team_members WHERE team_id = :id", Map.of("id", teamId.toString()));
            return null;
        }).thenRun(() -> {
            Map<UUID, TeamRole> members = membersByTeam.remove(teamId);
            if (members != null) {
                members.keySet().forEach(teamByPlayer::remove);
            }
            teams.remove(teamId);
            eventBus.publish(new TeamDisbandedEvent(team));
        });
    }

    @Override
    public Optional<RockTeam> teamOfCached(UUID playerId) {
        UUID teamId = teamByPlayer.get(playerId);
        return teamId == null ? Optional.empty() : Optional.ofNullable(teams.get(teamId));
    }

    @Override
    public Optional<TeamRole> roleOfCached(UUID teamId, UUID playerId) {
        Map<UUID, TeamRole> members = membersByTeam.get(teamId);
        return members == null ? Optional.empty() : Optional.ofNullable(members.get(playerId));
    }

    private RockTeam activeTeam(UUID teamId) {
        RockTeam team = teams.get(teamId);
        if (team == null || !team.active()) {
            throw new TeamException("No active team with id " + teamId);
        }
        return team;
    }
}
