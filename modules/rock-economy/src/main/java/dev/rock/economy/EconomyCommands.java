package dev.rock.economy;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.config.RockConfig;
import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.domain.RockTransaction;
import dev.rock.api.domain.TransactionStatus;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.EconomyService;
import dev.rock.api.services.PlayerService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * /rock pay|balance|baltop — the EssentialsX-parity player command surface,
 * backed by the audited ledger. Currency formatting is configurable
 * (rock-economy.toml) and live-reloadable like all module config.
 */
@RockInternal
@Singleton
public final class EconomyCommands implements LifecycleAware {

    static final String DEFAULT_CONFIG = """
            # ROCK economy settings
            [currency]
            symbol = "$"
            decimals = 2
            """;

    private final CommandService commands;
    private final EconomyService economy;
    private final PlayerService players;
    private final ConfigEngine configEngine;

    private volatile String symbol = "$";
    private volatile int decimals = 2;

    @Inject
    public EconomyCommands(CommandService commands, EconomyService economy,
            PlayerService players, ConfigEngine configEngine) {
        this.commands = commands;
        this.economy = economy;
        this.players = players;
        this.configEngine = configEngine;
    }

    String format(BigDecimal amount) {
        return symbol + amount.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public void onEnable() {
        RockConfig config = configEngine.loadModuleConfig("rock-economy", DEFAULT_CONFIG);
        symbol = config.getString("currency.symbol", "$");
        decimals = config.getInt("currency.decimals", 2);

        commands.register(new CommandSpec(List.of("balance"), "Shows your balance",
                "rock.economy.balance", ctx -> {
            UUID playerId = ctx.sender().playerId();
            if (playerId == null) {
                return CommandResult.USAGE_ERROR;
            }
            RockEconomyAccount account =
                    economy.openAccount(new PlayerOwner(playerId), AccountType.PLAYER).join();
            ctx.sender().sendMessage("Balance: " + format(economy.balance(account.id()).join()));
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("pay"), "Pays another player",
                "rock.economy.pay", ctx -> {
            UUID playerId = ctx.sender().playerId();
            if (playerId == null || ctx.args().size() < 2) {
                ctx.sender().sendMessage("Usage: /rock pay <player> <amount>");
                return CommandResult.USAGE_ERROR;
            }
            var target = players.findByUsername(ctx.args().get(0)).join();
            if (target.isEmpty()) {
                ctx.sender().sendMessage("Unknown player: " + ctx.args().get(0));
                return CommandResult.USAGE_ERROR;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(ctx.args().get(1));
                if (amount.signum() <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                ctx.sender().sendMessage("Invalid amount: " + ctx.args().get(1));
                return CommandResult.USAGE_ERROR;
            }
            // Both accounts exist after this (idempotent opens).
            economy.openAccount(new PlayerOwner(playerId), AccountType.PLAYER).join();
            economy.openAccount(new PlayerOwner(target.get().id()), AccountType.PLAYER).join();
            RockTransaction tx = economy.transfer(new PlayerOwner(playerId),
                    new PlayerOwner(target.get().id()), amount,
                    "/rock pay by " + ctx.sender().name()).join();
            if (tx.status() != TransactionStatus.COMPLETED) {
                ctx.sender().sendMessage("Payment failed: insufficient funds.");
                return CommandResult.FAILURE;
            }
            ctx.sender().sendMessage("Paid " + format(amount) + " to " + target.get().username() + ".");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("baltop"), "Richest players",
                "rock.economy.baltop", ctx -> {
            List<RockEconomyAccount> top = economy.topBalances(10).join();
            ctx.sender().sendMessage("Top balances:");
            int rank = 1;
            for (RockEconomyAccount account : top) {
                String name = players.findById(account.owner().id()).join()
                        .map(p -> p.username()).orElse(account.owner().id().toString());
                ctx.sender().sendMessage("  #" + rank++ + " " + name + " — " + format(account.balance()));
            }
            return CommandResult.SUCCESS;
        }));
    }

    @Override
    public void onDisable() {
        for (String name : List.of("balance", "pay", "baltop")) {
            commands.unregister(List.of(name));
        }
    }
}
