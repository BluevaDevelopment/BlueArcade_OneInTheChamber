package net.blueva.arcade.modules.oneinthechamber;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.api.events.EventSubscription;
import net.blueva.arcade.modules.oneinthechamber.game.OneInTheChamberGameManager;
import net.blueva.arcade.modules.oneinthechamber.listener.OneInTheChamberDamageListener;
import net.blueva.arcade.modules.oneinthechamber.listener.OneInTheChamberDeathSystem;
import net.blueva.arcade.modules.oneinthechamber.setup.OneInTheChamberSetup;
import net.blueva.arcade.modules.oneinthechamber.support.StatsService;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class OneInTheChamberModule implements GameModule<Player, Location, World, String, ItemStack, String, BlockState, Entity, EventSubscription<?>, Short> {

    private ModuleConfigAPI moduleConfig;
    public CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsService statsService;
    private OneInTheChamberGameManager gameManager;
    private boolean systemsRegistered;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("one_in_the_chamber");

        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for one in the chamber module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        StatsAPI<Player> statsAPI = ModuleAPI.getStatsAPI();
        VoteMenuAPI<String> voteMenu = ModuleAPI.getVoteMenuAPI();
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();

        statsService = new StatsService(statsAPI, moduleInfo);
        statsService.registerStats();

        moduleConfig.register("language.yml", 1);
        moduleConfig.register("settings.yml", 1);
        moduleConfig.register("achievements.yml", 2);

        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }

        gameManager = new OneInTheChamberGameManager(moduleConfig, coreConfig, moduleInfo, statsService);
        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new OneInTheChamberSetup(moduleConfig, coreConfig));

        if (moduleConfig != null && voteMenu != null) {
            String voteItem = moduleConfig.getString("menus.vote.item");
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    voteItem,
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context) {
        gameManager.handleStart(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context,
                                int secondsLeft) {
        gameManager.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context) {
        gameManager.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return gameManager.freezePlayersOnCountdown();
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context) {
        gameManager.handleGameStart(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, String, ItemStack, String, BlockState, Entity> context,
                      GameResult<Player> result) {
        gameManager.handleEnd(context, result);
    }

    @Override
    public void onDisable() {
        gameManager.onDisable();
    }

    @Override
    public void registerEvents(CustomEventRegistry<EventSubscription<?>, Short> registry) {
        if (systemsRegistered) {
            return;
        }
        // Register damage listener as ECS system
        // CustomEventRegistryImpl in Hytale has a registerSystem() method
        try {
            // Use reflection to call registerSystem methods to avoid compile-time dependency
            java.lang.reflect.Method registerEntityEventSystem = registry.getClass()
                    .getMethod("registerSystem", com.hypixel.hytale.component.system.EntityEventSystem.class);
            registerEntityEventSystem.invoke(registry, new OneInTheChamberDamageListener(gameManager));

            // Register death system with proper method lookup
            registerDeathSystem(registry);
            systemsRegistered = true;
        } catch (InvocationTargetException e) {
            if (isAlreadyRegistered(e.getCause())) {
                systemsRegistered = true;
                return;
            }
            throw new RuntimeException("Failed to register One In The Chamber systems", e);
        } catch (Exception e) {
            if (isAlreadyRegistered(e)) {
                systemsRegistered = true;
                return;
            }
            throw new RuntimeException("Failed to register One In The Chamber systems", e);
        }
    }

    private void registerDeathSystem(CustomEventRegistry<EventSubscription<?>, Short> registry) throws Exception {
        OneInTheChamberDeathSystem deathSystem = new OneInTheChamberDeathSystem(gameManager);

        // Access the plugin field from CustomEventRegistryImpl to get EntityStoreRegistry
        java.lang.reflect.Field pluginField = registry.getClass().getDeclaredField("plugin");
        pluginField.setAccessible(true);
        Object plugin = pluginField.get(registry);

        // Get the EntityStoreRegistry from the plugin
        Object entityStoreRegistry = plugin.getClass().getMethod("getEntityStoreRegistry").invoke(plugin);

        // ComponentRegistry.registerSystem() accepts ISystem, which all systems implement
        entityStoreRegistry.getClass()
                .getMethod("registerSystem", com.hypixel.hytale.component.system.ISystem.class)
                .invoke(entityStoreRegistry, deathSystem);
    }

    private boolean isAlreadyRegistered(Throwable error) {
        if (!(error instanceof IllegalArgumentException)) {
            return false;
        }
        String message = error.getMessage();
        return message != null && message.contains("already registered");
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return gameManager.getCustomPlaceholders(player);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public OneInTheChamberGameManager getGameManager() {
        return gameManager;
    }
}
