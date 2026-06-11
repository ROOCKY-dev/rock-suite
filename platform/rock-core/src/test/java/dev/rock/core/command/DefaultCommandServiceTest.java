package dev.rock.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandSender;
import dev.rock.api.command.CommandSpec;
import java.util.ArrayList;
import java.util.List;
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
    void duplicateRegistrationFails() {
        commands.register(new CommandSpec(List.of("version"), "v", "", ctx -> CommandResult.SUCCESS));

        assertThrows(IllegalStateException.class, () -> commands.register(
                new CommandSpec(List.of("version"), "v2", "", ctx -> CommandResult.SUCCESS)));
    }
}
