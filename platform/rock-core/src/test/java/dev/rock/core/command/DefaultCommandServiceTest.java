package dev.rock.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandSender;
import dev.rock.api.command.CommandSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultCommandServiceTest {

    private final DefaultCommandService commands = new DefaultCommandService();

    private static final class FakeSender implements CommandSender {
        final List<String> messages = new ArrayList<>();
        final List<String> grantedNodes;

        FakeSender(String... granted) {
            this.grantedNodes = List.of(granted);
        }

        @Override
        public UUID playerId() {
            return UUID.randomUUID();
        }

        @Override
        public String name() {
            return "tester";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public boolean hasPermission(String node) {
            return grantedNodes.contains(node);
        }
    }

    @Test
    void dispatchMatchesLongestPathAndPassesRemainingArgs() {
        AtomicReference<List<String>> seenArgs = new AtomicReference<>();
        commands.register(new CommandSpec(List.of("claims"), "claims root", "", ctx -> CommandResult.SUCCESS));
        commands.register(new CommandSpec(List.of("claims", "create"), "create a claim", "", ctx -> {
            seenArgs.set(ctx.args());
            return CommandResult.SUCCESS;
        }));

        CommandResult result = commands.dispatch(new FakeSender(), List.of("claims", "create", "MyTown"));

        assertEquals(CommandResult.SUCCESS, result);
        assertEquals(List.of("MyTown"), seenArgs.get());
    }

    @Test
    void permissionIsEnforced() {
        commands.register(new CommandSpec(
                List.of("admin"), "admin", "rock.admin", ctx -> CommandResult.SUCCESS));

        assertEquals(CommandResult.NO_PERMISSION, commands.dispatch(new FakeSender(), List.of("admin")));
        assertEquals(CommandResult.SUCCESS,
                commands.dispatch(new FakeSender("rock.admin"), List.of("admin")));
    }

    @Test
    void unknownCommandReturnsUsageError() {
        assertEquals(CommandResult.USAGE_ERROR, commands.dispatch(new FakeSender(), List.of("nope")));
    }

    @Test
    void throwingExecutorIsIsolatedAsFailure() {
        commands.register(new CommandSpec(List.of("boom"), "boom", "", ctx -> {
            throw new IllegalStateException("kaboom");
        }));

        assertEquals(CommandResult.FAILURE, commands.dispatch(new FakeSender(), List.of("boom")));
    }

    @Test
    void aliasExpandsIntoTheRockTree() {
        commands.register(new CommandSpec(List.of("ban"), "ban", "rock.moderation.ban",
                ctx -> CommandResult.SUCCESS));
        commands.registerAlias("ban", List.of("ban"));   // /ban x → /rock ban x
        commands.registerAlias("r", List.of());           // /r ban x → /rock ban x

        FakeSender admin = new FakeSender("rock.moderation.ban");
        assertEquals(CommandResult.SUCCESS, commands.dispatchAlias(admin, "ban", List.of("Griefer")));
        assertEquals(CommandResult.SUCCESS, commands.dispatchAlias(admin, "r", List.of("ban", "Griefer")));
        assertEquals(Set.of("ban", "r"), commands.aliases().keySet());
    }

    @Test
    void aliasStillEnforcesTheTargetPermission() {
        commands.register(new CommandSpec(List.of("ban"), "ban", "rock.moderation.ban",
                ctx -> CommandResult.SUCCESS));
        commands.registerAlias("ban", List.of("ban"));

        // An alias is pure routing — no permission bypass.
        assertEquals(CommandResult.NO_PERMISSION,
                commands.dispatchAlias(new FakeSender(), "ban", List.of("Griefer")));
    }

    @Test
    void duplicateRegistrationFails() {
        commands.register(new CommandSpec(List.of("version"), "v", "", ctx -> CommandResult.SUCCESS));

        assertThrows(IllegalStateException.class, () -> commands.register(
                new CommandSpec(List.of("version"), "v2", "", ctx -> CommandResult.SUCCESS)));
    }
}
