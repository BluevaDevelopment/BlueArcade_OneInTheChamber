package net.blueva.arcade.modules.oneinthechamber.listener;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.oneinthechamber.game.OneInTheChamberGameManager;

import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class OneInTheChamberDeathSystem extends DeathSystems.OnDeathSystem {

    private final OneInTheChamberGameManager gameManager;

    public OneInTheChamberDeathSystem(OneInTheChamberGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency(Order.BEFORE, DeathSystems.PlayerDeathScreen.class));
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull DeathComponent component,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context =
                gameManager.getGameContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        suppressDeathScreen(ref, component, store, commandBuffer, player);

        Damage deathInfo = component.getDeathInfo();
        Player attacker = resolveAttacker(deathInfo);
        if (attacker != null && !attacker.equals(player)) {
            gameManager.handleKillCredit(context, attacker);
        }

        gameManager.handlePlayerElimination(context, player, attacker);
    }

    private void suppressDeathScreen(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull DeathComponent component,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull Player player) {
        component.setShowDeathMenu(false);

        Message deathMessage = null;
        Damage deathInfo = component.getDeathInfo();
        if (deathInfo != null) {
            deathMessage = deathInfo.getDeathMessage(ref, commandBuffer);
        }

        PlayerRef playerRef = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            PageManager pageManager = player.getPageManager();
            pageManager.openCustomPage(ref, store, new PassThroughRespawnPage(
                    playerRef,
                    deathMessage,
                    component.displayDataOnDeathScreen(),
                    component.getDeathItemLoss()
            ));
        }

        World world = player.getWorld();
        if (world != null) {
            world.execute(() -> player.getPageManager().setPage(ref, store, Page.None));
        }
    }

    @Nullable
    private Player resolveAttacker(@Nullable Damage damage) {
        if (damage == null) {
            return null;
        }
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return null;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return null;
        }

        Store<EntityStore> attackerStore = attackerRef.getStore();
        if (attackerStore == null) {
            return null;
        }

        return attackerStore.getComponent(attackerRef, Player.getComponentType());
    }
}
