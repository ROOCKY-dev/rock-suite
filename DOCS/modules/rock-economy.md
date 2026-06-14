# rock-economy

> A server economy backed by an **audited transaction ledger** — balances, pay,
> leaderboard, with configurable currency formatting. EssentialsX-parity player
> surface; every balance change is a recorded, auditable transaction.

- **Module id:** `rock-economy` · **Depends:** rock-core, rock-data, rock-permissions
- **Service:** `dev.rock.api.services.EconomyService`
- **Status:** functional; **not yet public-polished** (see §8).

## 1. What it does
Per-owner accounts holding a `BigDecimal` balance, mutated only through audited
**transactions** (transfer/grant). Owners are `OwnerReference`s, so accounts work
for players today and teams/system later. The wallet feed projects to the
rock-client HUD / rock-web dashboard.

## 2. Concepts
- **Account** (`RockEconomyAccount`): id, owner, `AccountType` (`PLAYER`, …),
  balance. Opened idempotently (`openAccount`).
- **Transaction** (`RockTransaction`) + `TransactionStatus` (`COMPLETED`,
  `FAILED`/insufficient funds, …) — the ledger entry, written to `rock_transactions`.

## 3. Commands
| Command | Permission | Behaviour |
|---|---|---|
| `/rock balance` (alias `/balance`, `/bal`) | `rock.economy.balance` | Your balance, currency-formatted. |
| `/rock pay <player> <amount>` (alias `/pay`) | `rock.economy.pay` | Transfers funds; fails on non-positive amount or insufficient funds. |
| `/rock baltop` (alias `/baltop`) | `rock.economy.baltop` | Top 10 balances. |

**[QoL gaps]:** no player-name/amount tab-completion; plain text (no clickable
baltop / pay buttons); **no admin economy ops** (`give`/`take`/`set`/`reset`);
single currency only; no per-page baltop; no transaction history command.

## 4. Permissions
`rock.economy.balance` · `.pay` · `.baltop` · `rock.economy.*`.

## 5. Configuration (`rock-economy.toml`, live-reloadable)
```toml
[currency]
symbol = "$"
decimals = 2
```

## 6. Data & events
- **Data:** `rock_economy` accounts + `rock_transactions` (migration V004).
- **Events:** publishes `BalanceChangedEvent` → projected as `wallet.balance`
  (rock-protocol) to the client HUD / web dashboard.

## 7. Use-flow scenarios
1. **Check & pay:** `/balance` → `/pay Alice 50` → audited transfer; Alice's HUD
   updates live.
2. **Leaderboard:** `/baltop` → top 10 with names + formatted amounts.

## 8. Status — public-readiness gaps
- Admin commands (`/rock eco give|take|set <player> <amt>`), confirm dialogs.
- Tab-completion + clickable baltop/pay (Command-Framework-v2).
- Multi-currency; interest/taxes; shop/chestshop integration; pay toggle.
- Transaction history command + web view.

## 9. API
```java
EconomyService eco = registry.require(EconomyService.class);
eco.openAccount(new PlayerOwner(uuid), AccountType.PLAYER);
eco.balance(accountId); eco.transfer(from, to, amount, reason);
eco.grant(owner, amount, reason); eco.topBalances(10);
```

## Roadmap / upgrade log
- _2026-06-14 · v2.0.0-insiders · documented baseline._
