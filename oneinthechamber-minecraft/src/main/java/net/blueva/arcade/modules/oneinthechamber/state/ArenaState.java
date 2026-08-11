package net.blueva.arcade.modules.oneinthechamber.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaState {

    private final int arenaId;
    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final Map<UUID, UUID> lastHitBy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitAt = new ConcurrentHashMap<>();
    private boolean ended;
    private UUID winner;
    private int supplyTicks;

    public ArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.arenaId = context.getArenaId();
        this.context = context;
    }

    public int getArenaId() {
        return arenaId;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean markEnded() {
        boolean previouslyEnded = this.ended;
        this.ended = true;
        return !previouslyEnded;
    }

    public UUID getWinner() {
        return winner;
    }

    public boolean setWinnerIfAbsent(UUID winner) {
        if (this.winner != null) {
            return false;
        }
        this.winner = winner;
        return true;
    }

    // Combat tag, so a player knocked to their death still credits whoever hit them last.
    public void recordHit(UUID victimId, UUID attackerId) {
        if (victimId == null || attackerId == null || victimId.equals(attackerId)) {
            return;
        }
        lastHitBy.put(victimId, attackerId);
        lastHitAt.put(victimId, System.currentTimeMillis());
    }

    public UUID getRecentAttacker(UUID victimId, long windowMillis) {
        if (victimId == null) {
            return null;
        }
        UUID attackerId = lastHitBy.get(victimId);
        Long hitAt = lastHitAt.get(victimId);
        if (attackerId == null || hitAt == null) {
            return null;
        }
        if (System.currentTimeMillis() - hitAt > windowMillis) {
            return null;
        }
        return attackerId;
    }

    public void clearCombatTag(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastHitBy.remove(playerId);
        lastHitAt.remove(playerId);
    }

    public int incrementSupplyTicks(int amount) {
        supplyTicks += amount;
        return supplyTicks;
    }

    public void resetSupplyTicks() {
        supplyTicks = 0;
    }
}
