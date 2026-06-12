package dev.rock.api.services;

import dev.rock.api.events.world.BlockChangeType;
import java.util.Map;

/**
 * Dry-run summary of what a rollback would touch (Ledger's preview lesson —
 * and the projection rock-client will render as ghost blocks, RFC-001).
 *
 * @param entries       how many log entries would be reverted
 * @param byAction      entry count per change type
 * @param byBlockBefore entry count per block id that would be restored
 */
public record RollbackPreview(
        int entries,
        Map<BlockChangeType, Integer> byAction,
        Map<String, Integer> byBlockBefore) {

    public RollbackPreview {
        byAction = Map.copyOf(byAction);
        byBlockBefore = Map.copyOf(byBlockBefore);
    }

    public boolean empty() {
        return entries == 0;
    }
}
