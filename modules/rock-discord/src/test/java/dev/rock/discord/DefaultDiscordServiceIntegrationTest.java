package dev.rock.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.domain.RockDiscordLink;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Link persistence over real SQLite + DataService; delivery via fake gateway. */
class DefaultDiscordServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private DefaultDiscordService service;
    private final List<String> sent = new CopyOnWriteArrayList<>();

    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("discord.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));
        DiscordGateway gateway = (channel, content) -> {
            sent.add(channel + ":" + content);
            return CompletableFuture.completedFuture(null);
        };
        service = new DefaultDiscordService(data, new DiscordMessageQueue(gateway, Duration.ofMillis(1)));
    }

    @AfterEach
    void tearDown() {
        service.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void linkRoundTripsAndIsCurrent() {
        service.link(player, "discord-123").join();

        RockDiscordLink link = service.linkOf(player).join().orElseThrow();
        assertEquals("discord-123", link.discordId());
        assertTrue(link.linked());
    }

    @Test
    void unlinkKeepsHistoryButMarksUnlinked() {
        service.link(player, "discord-123").join();
        service.unlink(player).join();

        RockDiscordLink link = service.linkOf(player).join().orElseThrow();
        assertFalse(link.linked());
    }

    @Test
    void relinkReplacesPreviousLink() {
        service.link(player, "old-id").join();
        service.link(player, "new-id").join();

        assertEquals("new-id", service.linkOf(player).join().orElseThrow().discordId());
    }

    @Test
    void sendMessageGoesThroughTheQueue() {
        service.sendMessage("chan-1", "hello").join();

        assertEquals(List.of("chan-1:hello"), sent);
    }
}
