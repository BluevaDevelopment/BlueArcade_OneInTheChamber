package net.blueva.arcade.modules.oneinthechamber.listener;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.RespawnPage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathItemLoss;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PassThroughRespawnPage extends RespawnPage {

    public PassThroughRespawnPage(PlayerRef playerRef,
                                  @Nullable Message deathReason,
                                  boolean displayDataOnDeathScreen,
                                  DeathItemLoss deathItemLoss) {
        super(playerRef, deathReason, displayDataOnDeathScreen, deathItemLoss);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {
        // Intentionally empty: immediately pass through without showing a death screen.
    }
}
