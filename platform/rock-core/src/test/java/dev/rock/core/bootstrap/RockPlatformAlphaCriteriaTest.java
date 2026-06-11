package dev.rock.core.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandSender;
import dev.rock.api.domain.PlayerStatus;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.domain.RockServer;
import dev.rock.api.domain.ServerType;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.ModuleState;
import dev.rock.api.module.RockModule;
import dev.rock.api.service.RockService;
import dev.rock.core.module.ModuleLoader;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Validates the REH §18 Alpha success criteria end to end:
 * module loads/unloads, PlayerJoinEvent received, /rock version executes,
 * TestService registers through DI, and crash isolation works.
 */
class RockPlatformAlphaCriteriaTest {

    @TempDir
    Path dataDir;

    private RockPlatform platform;

    // --- test module -------------------------------------------------------

    public interface TestService extends RockService {
        String ping();
    }

    public static final class DefaultTestService implements TestService, LifecycleAware {
        static final List<String> lifecycle = new ArrayList<>();

        @Inject
        public DefaultTestService() {
        }

        @Override
        public String ping() {
            return "pong";
        }

        @Override
        public void onEnable() {
            lifecycle.add("enable");
        }

        @Override
        public void onDisable() {
            lifecycle.add("disable");
        }
    }

    private static final class TestRockModule implements RockModule {
        @Override
        public ModuleManifest manifest() {
            return new ModuleManifest("rock-test-module", "Rock Test Module", "1.0.0", "1.0",
                    List.of("Ahmed"), List.of("rock-core"));
        }

        @Override
        public Object guiceModule() {
            return new AbstractModule() {
                @Override
                protected void configure() {
                    bind(TestService.class).to(DefaultTestService.class).in(Scopes.SINGLETON);
                    bind(DefaultTestService.class).in(Scopes.SINGLETON);
                }
            };
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }

    private static final class CrashingModule implements RockModule {
        @Override
        public ModuleManifest manifest() {
            return new ModuleManifest("rock-crashing-module", "Crash", "1.0.0", "1.0", List.of(), List.of());
        }

        @Override
        public Object guiceModule() {
            return new AbstractModule() {
            };
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("simulated module crash");
        }

        @Override
        public void onDisable() {
        }
    }

    private static final class FakeSender implements CommandSender {
        final List<String> messages = new ArrayList<>();

        @Override
        public UUID playerId() {
            return null;
        }

        @Override
        public String name() {
            return "console";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public boolean hasPermission(String node) {
            return true;
        }
    }

    private PlatformEnvironment environment() {
        return new PlatformEnvironment() {
            @Override
            public java.util.concurrent.Executor mainThreadExecutor() {
                return Runnable::run;
            }

            @Override
            public Path dataDirectory() {
                return dataDir;
            }

            @Override
            public RockServer serverInfo() {
                return new RockServer(UUID.randomUUID(), "test", "1.21.11", ServerType.UNKNOWN);
            }
        };
    }

    @BeforeEach
    void boot() {
        DefaultTestService.lifecycle.clear();
        platform = RockPlatform.boot(
                environment(), List.of(), List.of(new TestRockModule(), new CrashingModule()));
    }

    @AfterEach
    void shutdown() {
        platform.close();
    }

    @Test
    void testModuleLoadsAndTestServiceResolvesThroughDi() {
        TestService service = platform.services().require(TestService.class);
        assertEquals("pong", service.ping());
        assertEquals(ModuleState.RUNNING, platform.moduleLoader().states().get("rock-test-module"));
        assertEquals(List.of("enable"), DefaultTestService.lifecycle);
    }

    @Test
    void rockVersionCommandExecutes() {
        FakeSender sender = new FakeSender();

        CommandResult result = platform.injector().getInstance(dev.rock.api.command.CommandService.class)
                .dispatch(sender, List.of("version"));

        assertEquals(CommandResult.SUCCESS, result);
        assertEquals(List.of("ROCK SUITE v" + RockPlatform.VERSION), sender.messages);
    }

    @Test
    void playerJoinEventFiresAndIsReceived() {
        EventBus bus = platform.services().require(EventBus.class);
        AtomicReference<PlayerJoinEvent> received = new AtomicReference<>();
        bus.subscribe(PlayerJoinEvent.class, received::set);

        RockPlayer player = new RockPlayer(UUID.randomUUID(), "Ahmed", Locale.ENGLISH,
                Instant.now(), Instant.now(), PlayerStatus.ACTIVE, null);
        bus.publish(new PlayerJoinEvent(player, true));

        assertEquals(player, received.get().player());
        assertTrue(received.get().firstJoin());
    }

    @Test
    void crashingModuleIsIsolatedAndOthersKeepRunning() {
        ModuleLoader loader = platform.moduleLoader();

        assertEquals(ModuleState.FAILED, loader.states().get("rock-crashing-module"));
        assertEquals(ModuleState.RUNNING, loader.states().get("rock-test-module"));
    }

    @Test
    void moduleUnloadsThroughFullLifecycle() {
        platform.moduleLoader().disableAll();

        assertEquals(ModuleState.UNLOADED, platform.moduleLoader().states().get("rock-test-module"));
        assertEquals(List.of("enable", "disable"), DefaultTestService.lifecycle);
    }
}
