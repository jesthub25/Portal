package org.jestron.portal.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncu basina bekleme sureleri.
 *  - teleport: iki isinlanma arasi (ard arda portal kullanimini engeller)
 *  - warning : ayni uyari mesajinin tekrar gonderilmesi (spam engeli)
 */
public final class CooldownManager {

    private final Map<UUID, Long> teleportUntil = new HashMap<>();
    private final Map<UUID, Long> warningUntil = new HashMap<>();

    // ---------------------------------------------------------------- isinlanma

    public boolean isTeleportReady(UUID id) {
        Long until = teleportUntil.get(id);
        return until == null || System.currentTimeMillis() >= until;
    }

    public void startTeleport(UUID id, double seconds) {
        teleportUntil.put(id, System.currentTimeMillis() + toMillis(seconds));
    }

    /** Kalan sure (yukari yuvarlanmis saniye, en az 1). */
    public long teleportRemainingSeconds(UUID id) {
        Long until = teleportUntil.get(id);
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        return ms <= 0 ? 0 : Math.max(1L, (ms + 999L) / 1000L);
    }

    // ---------------------------------------------------------------- uyari

    /** Uyari gonderilebiliyorsa true doner ve bekleme suresini baslatir. */
    public boolean tryWarn(UUID id, double seconds) {
        long now = System.currentTimeMillis();
        Long until = warningUntil.get(id);
        if (until != null && now < until) return false;
        warningUntil.put(id, now + toMillis(seconds));
        return true;
    }

    // ---------------------------------------------------------------- temizlik

    public void clear(UUID id) {
        teleportUntil.remove(id);
        warningUntil.remove(id);
    }

    public void clearAll() {
        teleportUntil.clear();
        warningUntil.clear();
    }

    private static long toMillis(double seconds) {
        if (Double.isNaN(seconds) || seconds <= 0) return 0L;
        return (long) Math.ceil(seconds * 1000.0);
    }
}
