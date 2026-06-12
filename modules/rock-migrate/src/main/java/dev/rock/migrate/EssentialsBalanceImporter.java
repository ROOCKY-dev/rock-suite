package dev.rock.migrate;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.EconomyService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports EssentialsX player balances from its userdata directory
 * (one {@code <uuid>.yml} per player; the {@code money:} field). Each balance
 * arrives as a SYSTEM-minted grant transaction — visible in history like any
 * other money. Source files are read-only.
 */
@RockInternal
@Singleton
public final class EssentialsBalanceImporter implements RmgImporter {

    private static final Pattern MONEY = Pattern.compile("^money:\\s*'?\"?([0-9]+(?:\\.[0-9]+)?)'?\"?\\s*$");

    private static final Logger log = LoggerFactory.getLogger(EssentialsBalanceImporter.class);

    // Sibling-module service: resolved through the registry at run time, never
    // constructor-injected (DIS anti-pattern 4 — modules don't share injectors).
    private final ServiceRegistry services;

    @Inject
    public EssentialsBalanceImporter(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "essentialsx";
    }

    @Override
    public String description() {
        return "EssentialsX userdata directory (player balances → SYSTEM grant transactions)";
    }

    @Override
    public CompletableFuture<ImportReport> run(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isDirectory(source)) {
                throw new IllegalArgumentException("Not a directory: " + source);
            }
            EconomyService economy = services.require(EconomyService.class);
            Map<String, Integer> imported = new TreeMap<>();
            List<String> warnings = new ArrayList<>();
            int balances = 0;
            try (Stream<Path> files = Files.list(source)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                    String name = file.getFileName().toString().replaceFirst("\\.yml$", "");
                    UUID player;
                    try {
                        player = UUID.fromString(name);
                    } catch (IllegalArgumentException e) {
                        warnings.add("Skipped non-uuid userdata file: " + file.getFileName());
                        continue;
                    }
                    BigDecimal balance = readMoney(file);
                    if (balance == null) {
                        warnings.add("No money field in " + file.getFileName());
                        continue;
                    }
                    PlayerOwner owner = new PlayerOwner(player);
                    economy.openAccount(owner, AccountType.PLAYER).join();
                    if (balance.signum() > 0) {
                        economy.grant(owner, balance, "EssentialsX import").join();
                    }
                    balances++;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("EssentialsX import failed", e);
            }
            imported.put("balances", balances);
            log.info("EssentialsX import done: {} balance(s), {} warning(s)", balances, warnings.size());
            return new ImportReport(imported, warnings);
        });
    }

    private static BigDecimal readMoney(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            Matcher matcher = MONEY.matcher(line.trim());
            if (matcher.matches()) {
                return new BigDecimal(matcher.group(1));
            }
        }
        return null;
    }
}
