package dev.rock.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.PunishmentType;
import dev.rock.api.domain.RockPunishment;
import dev.rock.api.domain.owner.SystemOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.events.player.PlayerChatEvent;
import dev.rock.api.events.punishment.PlayerBanEvent;
import dev.rock.api.events.punishment.PunishmentAppliedEvent;
import dev.rock.api.services.AuditService;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.loader.PlayerSessionBridge;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.audit.DefaultAuditService;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.moderation.DefaultPunishmentService.PunishmentVetoedException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full-stack moderation tests: punishments, mute enforcement, ban join gate, audit trail. */
class DefaultPunishmentServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private EventBus eventBus;
    private DefaultServiceRegistry registry;
    private DefaultPunishmentService service;
    private MuteEnforcementListener muteListener;

    private final UUID griefer = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("moderation.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        registry = new DefaultServiceRegistry();
        registry.register(AuditService.class, new DefaultAuditService(data));
        service = new DefaultPunishmentService(data, eventBus, registry);
        service.onEnable();
        registry.register(dev.rock.api.services.PunishmentService.class, service);
        muteListener = new MuteEnforcementListener(eventBus, service);
        muteListener.onEnable();
    }

    @AfterEach
    void tearDown() {
        muteListener.onDisable();
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void banAppliesPersistsAuditsAndGatesJoin() {
        AtomicReference<PunishmentAppliedEvent> applied = new AtomicReference<>();
        eventBus.subscribe(PunishmentAppliedEvent.class, applied::set);

        RockPunishment ban = service.punish(
                PunishmentType.BAN, griefer, SystemOwner.server(), "griefing spawn", null).join();

        assertEquals(ban.id(), applied.get().punishment().id());
        assertTrue(service.activeCached(griefer, PunishmentType.BAN).isPresent());

        // The join gate the loader adapters consult:
        PlayerSessionBridge bridge = new PlayerSessionBridge(registry, eventBus);
        String denial = bridge.joinDenialReason(griefer).orElseThrow();
        assertTrue(denial.contains("griefing spawn"));
        assertTrue(bridge.joinDenialReason(admin).isEmpty(), "unbanned players join freely");

        // Audited (TRS §9).
        var audit = registry.require(AuditService.class).findByTarget("PLAYER", griefer, 10).join();
        assertEquals(1, audit.size());
    }

    @Test
    void unbanRestoresJoin() {
        RockPunishment ban = service.punish(
                PunishmentType.BAN, griefer, SystemOwner.server(), "oops", null).join();
        service.revoke(ban.id(), admin).join();

        assertTrue(service.activeCached(griefer, PunishmentType.BAN).isEmpty());
        PlayerSessionBridge bridge = new PlayerSessionBridge(registry, eventBus);
        assertTrue(bridge.joinDenialReason(griefer).isEmpty());
    }

    @Test
    void muteSilencesChatUntilRevokedOrExpired() throws Exception {
        RockPunishment mute = service.punish(
                PunishmentType.MUTE, griefer, SystemOwner.server(), "spam", null).join();

        PlayerChatEvent muted = eventBus.publish(new PlayerChatEvent(griefer, "Griefer", "buy gold"));
        assertTrue(muted.cancelled(), "muted player's chat is cancelled at FIRST priority");
        PlayerChatEvent other = eventBus.publish(new PlayerChatEvent(admin, "Admin", "hello"));
        assertFalse(other.cancelled());

        service.revoke(mute.id(), admin).join();
        assertFalse(eventBus.publish(new PlayerChatEvent(griefer, "Griefer", "sorry")).cancelled());

        // Temporary mute expires on its own (lazy cache expiry).
        service.punish(PunishmentType.MUTE, griefer, SystemOwner.server(), "brief", Duration.ofMillis(60)).join();
        assertTrue(eventBus.publish(new PlayerChatEvent(griefer, "Griefer", "x")).cancelled());
        Thread.sleep(100);
        assertFalse(eventBus.publish(new PlayerChatEvent(griefer, "Griefer", "y")).cancelled());
    }

    @Test
    void banCanBeVetoedByListener() {
        eventBus.subscribe(PlayerBanEvent.class, EventPriority.FIRST, PlayerBanEvent::cancel);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.punish(PunishmentType.BAN, griefer, SystemOwner.server(), "vetoed", null).join());

        assertInstanceOf(PunishmentVetoedException.class, thrown.getCause());
        assertTrue(service.activeCached(griefer, PunishmentType.BAN).isEmpty());
    }

    @Test
    void cacheSurvivesRestartAndSkipsExpired() throws Exception {
        service.punish(PunishmentType.BAN, griefer, SystemOwner.server(), "permanent", null).join();
        service.punish(PunishmentType.MUTE, admin, SystemOwner.server(), "brief", Duration.ofMillis(50)).join();
        Thread.sleep(80);

        DefaultPunishmentService restarted = new DefaultPunishmentService(data, eventBus, registry);
        restarted.onEnable();

        assertTrue(restarted.activeCached(griefer, PunishmentType.BAN).isPresent());
        assertTrue(restarted.activeCached(admin, PunishmentType.MUTE).isEmpty(), "expired not reloaded");
        assertEquals(1, restarted.history(admin).join().size(), "history keeps expired rows");
    }
}
