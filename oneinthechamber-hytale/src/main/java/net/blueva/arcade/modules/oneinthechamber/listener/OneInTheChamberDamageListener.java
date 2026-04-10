package net.blueva.arcade.modules.oneinthechamber.listener;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.oneinthechamber.game.OneInTheChamberGameManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Damage listener for One In The Chamber minigame.
 * Handles player-vs-player combat using Hytale's ECS system.
 *
 * Mirrors the functionality of OneInTheChamberListener from Minecraft edition.
 */
public class OneInTheChamberDamageListener extends EntityEventSystem<EntityStore, Damage> {

    private final OneInTheChamberGameManager gameManager;

    public OneInTheChamberDamageListener(OneInTheChamberGameManager gameManager) {
        super(Damage.class);
        this.gameManager = gameManager;
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        // Skip if damage is already cancelled
        if (damage.isCancelled()) {
            return;
        }

        // Get victim reference and components
        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        if (victimRef == null) {
            return;
        }

        PlayerRef victimPlayerRef = store.getComponent(victimRef, PlayerRef.getComponentType());
        Player victimPlayer = store.getComponent(victimRef, Player.getComponentType());
        if (victimPlayerRef == null || victimPlayer == null) {
            return;
        }

        // Check if victim is in a game
        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context =
                gameManager.getGameContext(victimPlayer);
        if (context == null || !context.isPlayerPlaying(victimPlayer)) {
            return;
        }

        // Only process damage during PLAYING phase
        if (context.getPhase() != GamePhase.PLAYING) {
            damage.setCancelled(true);
            return;
        }

        // Get attacker from damage source
        Player attackerPlayer = resolveAttacker(damage, store);

        // Handle environmental damage (fall, fire, etc.)
        if (attackerPlayer == null) {
            // Let environmental damage happen naturally
            // The player will be eliminated if they die from fall/fire/etc.
            return;
        }

        // Verify attacker is also playing
        if (!context.isPlayerPlaying(attackerPlayer)) {
            damage.setCancelled(true);
            return;
        }

        // Prevent self-damage but still handle as elimination
        if (attackerPlayer.equals(victimPlayer)) {
            damage.setCancelled(true);
            gameManager.handlePlayerElimination(context, victimPlayer, null);
            return;
        }

        // Record hit for statistics
        gameManager.handleHit(attackerPlayer);

        // Check damage cause
        DamageCause cause = damage.getCause();
        boolean isProjectile = cause == DamageCause.PROJECTILE;

        // In One In The Chamber: arrows are instant kill
        if (isProjectile) {
            damage.setCancelled(true);
            gameManager.handleKillCredit(context, attackerPlayer);
            gameManager.handlePlayerElimination(context, victimPlayer, attackerPlayer);
            return;
        }

        // For melee attacks: allow damage to proceed naturally
        // If the damage kills the player, it will be handled separately
        // (Hytale doesn't easily expose health tracking to predict if damage is fatal)
    }

    @Nullable
    private Player resolveAttacker(@Nonnull Damage damage, @Nonnull Store<EntityStore> store) {
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource)) {
            return null;
        }

        Damage.EntitySource entitySource = (Damage.EntitySource) source;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return null;
        }

        Store<EntityStore> attackerStore = attackerRef.getStore();
        if (attackerStore == null) {
            return null;
        }

        // Check if attacker is a player
        Player attackerPlayer = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer != null) {
            return attackerPlayer;
        }

        // For projectiles, the attacker ref should be the player who shot it
        // Hytale's Damage.EntitySource should already resolve this
        return null;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
