package dev.rock.protocol;

/**
 * Subscribable client capabilities (RFC-001). Each maps to a permission node;
 * the server only delivers projections for capabilities the player may see,
 * filtered at the edge.
 */
public enum Capability {
    /** Claim boundaries + entry/exit toasts. */
    CLAIMS("rock.client.claims"),
    /** Wallet HUD + balance-change toasts. */
    WALLET("rock.client.wallet"),
    /** Incoming teleport-request toasts. */
    TPA("rock.client.tpa"),
    /** Admin inspector overlay (block history, rollback preview). */
    ADMIN_INSPECTOR("rock.admin.inspector");

    private final String permission;

    Capability(String permission) {
        this.permission = permission;
    }

    public String permission() {
        return permission;
    }

    public static Capability fromWire(String name) {
        try {
            return Capability.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // unknown capability — ignored (forward-compatible)
        }
    }
}
