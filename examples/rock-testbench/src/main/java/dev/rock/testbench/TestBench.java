package dev.rock.testbench;

import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandSender;
import dev.rock.api.command.CommandService;
import dev.rock.api.data.DataService;
import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.RockServer;
import dev.rock.api.domain.RockTransaction;
import dev.rock.api.domain.ServerType;
import dev.rock.api.domain.TransactionStatus;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.module.ModuleState;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.services.ClaimService;
import dev.rock.api.services.DiscordService;
import dev.rock.api.services.EconomyService;
import dev.rock.api.services.LogQuery;
import dev.rock.api.services.PermissionService;
import dev.rock.api.services.PlayerService;
import dev.rock.api.services.WorldLogService;
import dev.rock.api.world.WorldMutator;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.loader.LoaderBootstrap;
import dev.rock.data.DatabaseSettings;
import dev.rock.data.RockDataModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime testbench: boots the ROCK platform exactly the way a loader adapter
 * does (ServiceLoader module discovery, contributed RockDataModule, simulated
 * tick thread), connects two simulated clients through the same
 * PlayerSessionBridge the Fabric/NeoForge adapters call, then exercises every
 * service end-to-end. Exit code 0 = everything ran cleanly.
 */
public final class TestBench {

    private static final Logger log = LoggerFactory.getLogger(TestBench.class);

    private static int checks;

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new IllegalStateException("CHECK FAILED: " + description);
        }
        checks++;
        log.info("  ✔ {}", description);
    }

    /** Simulated client console sender. */
    private record BenchSender(UUID playerId, String name, List<String> inbox,
            PermissionService permissions) implements CommandSender {

        @Override
        public void sendMessage(String message) {
            inbox.add(message);
            log.info("  [chat → {}] {}", name, message);
        }

        @Override
        public boolean hasPermission(String node) {
            return playerId == null || permissions.has(playerId, node);
        }
    }

    public static void main(String[] args) throws Exception {
        Path runDir = Path.of("build", "testbench-run").toAbsolutePath();
        Files.createDirectories(runDir);

        // Simulated server tick thread, like MinecraftServer's event loop.
        ExecutorService tickThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "Server thread"));

        PlatformEnvironment environment = new PlatformEnvironment() {
            @Override
            public java.util.concurrent.Executor mainThreadExecutor() {
                return tickThread;
            }

            @Override
            public Path dataDirectory() {
                return runDir;
            }

            @Override
            public RockServer serverInfo() {
                return new RockServer(UUID.randomUUID(), "testbench", "1.21.11-sim", ServerType.FABRIC);
            }
        };

        log.info("==================================================================");
        log.info(" ROCK SUITE TESTBENCH — simulated dedicated server + 2 clients");
        log.info("==================================================================");

        log.info("[1] Booting platform (ServiceLoader discovery, SQLite at {})", runDir.resolve("rock.db"));
        DatabaseSettings settings = DatabaseSettings.sqliteDefault(runDir);
        LoaderBootstrap.BootResult boot = LoaderBootstrap.boot(environment, List.of(new RockDataModule(settings)));
        var platform = boot.platform();
        var services = platform.services();

        Map<String, ModuleState> states = platform.moduleLoader().states();
        log.info("[1] Module states: {}", states);
        check(states.size() == 5, "all five feature modules were discovered");
        states.forEach((id, state) -> check(state == ModuleState.RUNNING, "module " + id + " is RUNNING"));

        EventBus eventBus = services.require(EventBus.class);
        PermissionService permissions = services.require(PermissionService.class);
        ClaimService claims = services.require(ClaimService.class);
        EconomyService economy = services.require(EconomyService.class);
        DiscordService discord = services.require(DiscordService.class);
        PlayerService players = services.require(PlayerService.class);
        DataService data = services.require(DataService.class);
        check(true, "all six service contracts resolved from the ServiceRegistry");

        // ---------------------------------------------------------------
        log.info("[2] Two clients connecting (same path the loader adapters use)");
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        CountDownLatch joins = new CountDownLatch(2);
        eventBus.subscribe(PlayerJoinEvent.class, e -> {
            log.info("  [event] PlayerJoinEvent: {} (firstJoin={})", e.player().username(), e.firstJoin());
            joins.countDown();
        });
        boot.sessions().playerJoined(alice, "Alice");
        boot.sessions().playerJoined(bob, "Bob");
        check(joins.await(10, TimeUnit.SECONDS), "both PlayerJoinEvents fired and were received");
        check(players.findByUsername("Alice").join().isPresent(), "Alice persisted to rock_players");
        check(players.online().join().size() == 2, "2 players online");

        // ---------------------------------------------------------------
        log.info("[3] Commands: /rock version and /rock modules from Alice's client");
        BenchSender console = new BenchSender(null, "console", new java.util.ArrayList<>(), permissions);
        BenchSender aliceSender = new BenchSender(alice, "Alice", new java.util.ArrayList<>(), permissions);
        CommandService commands = platform.injector().getInstance(CommandService.class);

        check(commands.dispatch(aliceSender, List.of("version")) == CommandResult.SUCCESS,
                "/rock version succeeds for a client");
        check(aliceSender.inbox().getFirst().contains(dev.rock.core.bootstrap.RockPlatform.VERSION),
                "version reply contains the platform version");
        check(commands.dispatch(aliceSender, List.of("modules")) == CommandResult.NO_PERMISSION,
                "/rock modules denied without rock.admin.modules");
        permissions.grant(alice, "rock.admin.*").join();
        check(commands.dispatch(aliceSender, List.of("modules")) == CommandResult.SUCCESS,
                "/rock modules allowed after wildcard admin grant");

        // ---------------------------------------------------------------
        log.info("[4] Claims: Alice claims a chunk, Bob is rejected on overlap");
        UUID world = UUID.randomUUID();
        RockClaim base = claims.create("Alice's Base", new PlayerOwner(alice), ClaimType.PLAYER,
                new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0)))).join();
        check(base != null, "claim created and persisted");
        check(claims.claimAt(world, 8, 64, 8).join().orElseThrow().id().equals(base.id()),
                "claimAt resolves Alice's claim");
        boolean rejected = false;
        try {
            claims.create("Bob's Squat", new PlayerOwner(bob), ClaimType.PLAYER,
                    new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0)))).join();
        } catch (Exception e) {
            rejected = true;
        }
        check(rejected, "overlapping claim by Bob rejected");

        // ---------------------------------------------------------------
        log.info("[4b] Protection: Bob tries to grief Alice's claim (world-event layer)");
        var worldEvents = boot.worldEvents();
        boolean bobGrief = worldEvents.blockChange(bob, false, world, 5, 64, 5,
                BlockChangeType.BREAK, "minecraft:diamond_block", "minecraft:air");
        check(!bobGrief, "Bob's break inside Alice's claim is CANCELLED");
        boolean aliceBuild = worldEvents.blockChange(alice, false, world, 5, 64, 5,
                BlockChangeType.BREAK, "minecraft:stone", "minecraft:air");
        check(aliceBuild, "Alice (owner) may break in her own claim");
        boolean machineGrief = worldEvents.blockChange(bob, true, world, 6, 64, 6,
                BlockChangeType.PLACE, "minecraft:air", "minecraft:quarry");
        check(!machineGrief, "fake-player (machine) change inside a claim is CANCELLED");
        boolean wilderness = worldEvents.blockChange(bob, false, world, 500, 64, 500,
                BlockChangeType.BREAK, "minecraft:dirt", "minecraft:air");
        check(wilderness, "wilderness is unaffected");

        claims.trust(base.id(), bob, ClaimRole.BUILD).join();
        boolean bobTrusted = worldEvents.blockChange(bob, false, world, 5, 64, 5,
                BlockChangeType.BREAK, "minecraft:stone", "minecraft:air");
        check(bobTrusted, "Bob may build after Alice trusts him with BUILD");
        claims.untrust(base.id(), bob).join();

        // Claim flags (1.2): Alice opts her claim into automation.
        claims.setFlag(base.id(), dev.rock.api.domain.ClaimFlag.FAKE_PLAYERS, true).join();
        boolean machineAllowed = worldEvents.blockChange(bob, true, world, 6, 64, 6,
                BlockChangeType.PLACE, "minecraft:air", "minecraft:quarry");
        check(machineAllowed, "FAKE_PLAYERS flag lets Alice's machines run");
        claims.setFlag(base.id(), dev.rock.api.domain.ClaimFlag.FAKE_PLAYERS, false).join();

        // Permission contexts + meta + temporary (1.2)
        var nether = dev.rock.api.domain.ContextSet.of("world", "nether");
        permissions.grant(bob, "rock.claims.create", nether).join();
        check(!permissions.has(bob, "rock.claims.create"), "context-scoped grant inert globally");
        check(permissions.has(bob, "rock.claims.create", nether), "context-scoped grant applies in-world");
        permissions.setPlayerOption(alice, "prefix", "[Founder]").join();
        check(permissions.option(alice, "prefix").orElseThrow().equals("[Founder]"), "prefix meta resolves");
        permissions.grantTemporary(bob, "rock.event.vip", java.time.Duration.ofMillis(60)).join();
        check(permissions.has(bob, "rock.event.vip"), "temporary grant active");
        Thread.sleep(100);
        check(!permissions.has(bob, "rock.event.vip"), "temporary grant expired");

        // ---------------------------------------------------------------
        log.info("[4c] Block logging: query Alice's break, then roll the world back");
        Map<String, String> fakeWorld = new java.util.concurrent.ConcurrentHashMap<>();
        WorldMutator mutator = new WorldMutator() {
            @Override
            public java.util.concurrent.CompletableFuture<Void> setBlock(
                    UUID worldId, int x, int y, int z, String blockId) {
                fakeWorld.put(x + "," + y + "," + z, blockId);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public java.util.concurrent.CompletableFuture<String> getBlock(UUID worldId, int x, int y, int z) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        fakeWorld.getOrDefault(x + "," + y + "," + z, "minecraft:air"));
            }
        };
        services.register(WorldMutator.class, mutator);
        WorldLogService worldLog = services.require(WorldLogService.class);

        List<RockWorldLogEntry> aliceLog = worldLog.query(
                LogQuery.builder().world(world).actor(alice).build()).join();
        check(aliceLog.size() == 1, "Alice's approved break was logged (cancelled ones were not)");
        check(aliceLog.getFirst().blockBefore().equals("minecraft:stone"), "log captured block_before");

        int rolledBack = worldLog.rollback(
                LogQuery.builder().world(world).around(5, 64, 5, 2).build()).join();
        check(rolledBack == 3, "rollback reverted the two breaks and the machine place in the area");
        check(fakeWorld.get("5,64,5").equals("minecraft:stone"), "WorldMutator restored the original block");
        check(fakeWorld.get("6,64,6").equals("minecraft:air"), "machine-placed block removed by rollback");

        int restored = worldLog.restore(LogQuery.builder().world(world).around(5, 64, 5, 2).build()).join();
        check(restored == 3, "restore re-applied the rolled-back changes");
        check(fakeWorld.get("5,64,5").equals("minecraft:air"), "restore re-applied the break");

        // ---------------------------------------------------------------
        log.info("[5] Economy: accounts, funded transfer, insufficient funds, reversal");
        economy.openAccount(new PlayerOwner(alice), AccountType.PLAYER).join();
        economy.openAccount(new PlayerOwner(bob), AccountType.PLAYER).join();
        var aliceAccount = economy.findAccount(new PlayerOwner(alice)).join().orElseThrow();
        data.update("UPDATE rock_accounts SET balance = :b WHERE id = :id",
                Map.of("b", new BigDecimal("100.00"), "id", aliceAccount.id().toString())).join();

        RockTransaction payment = economy.transfer(
                new PlayerOwner(alice), new PlayerOwner(bob), new BigDecimal("25.00"), "rent").join();
        check(payment.status() == TransactionStatus.COMPLETED, "25.00 transfer Alice→Bob COMPLETED");
        check(economy.balance(aliceAccount.id()).join().compareTo(new BigDecimal("75.00")) == 0,
                "Alice balance is 75.00");

        RockTransaction tooMuch = economy.transfer(
                new PlayerOwner(bob), new PlayerOwner(alice), new BigDecimal("999.00"), "greed").join();
        check(tooMuch.status() == TransactionStatus.FAILED, "overdraft attempt FAILED without corruption");

        RockTransaction refund = economy.reverse(payment.id(), "refund").join();
        check(payment.id().equals(refund.reversalOf()), "reversal links to original transaction");
        check(economy.balance(aliceAccount.id()).join().compareTo(new BigDecimal("100.00")) == 0,
                "Alice restored to 100.00 after refund");

        // ---------------------------------------------------------------
        log.info("[6] Discord: identity link + queued send (no token → no-op gateway)");
        discord.link(alice, "alice#0001").join();
        check(discord.linkOf(alice).join().orElseThrow().linked(), "Alice linked to Discord");
        discord.sendMessage("123456789", "Alice claimed a base!").join();
        check(true, "message accepted by the rate-limited queue");

        // ---------------------------------------------------------------
        log.info("[7] Clients disconnecting");
        CountDownLatch leaves = new CountDownLatch(2);
        eventBus.subscribe(PlayerLeaveEvent.class, e -> {
            log.info("  [event] PlayerLeaveEvent: {}", e.player().username());
            leaves.countDown();
        });
        boot.sessions().playerLeft(alice, "Alice");
        boot.sessions().playerLeft(bob, "Bob");
        check(leaves.await(10, TimeUnit.SECONDS), "both PlayerLeaveEvents fired");
        check(players.online().join().isEmpty(), "no players online after disconnect");

        // ---------------------------------------------------------------
        log.info("[8] Platform shutdown sequence");
        platform.close();
        tickThread.shutdown();
        check(tickThread.awaitTermination(5, TimeUnit.SECONDS), "tick thread drained");

        log.info("==================================================================");
        log.info(" TESTBENCH PASSED — {} checks green, server + 2 clients clean", checks);
        log.info("==================================================================");
    }
}
