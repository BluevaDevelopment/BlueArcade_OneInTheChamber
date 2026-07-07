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
import net.blueva.arcade.modules.oneinthechamber.game.OneInTheChamberGameManager;
import net.blueva.arcade.modules.oneinthechamber.listener.OneInTheChamberListener;
import net.blueva.arcade.modules.oneinthechamber.setup.OneInTheChamberSetup;
import net.blueva.arcade.modules.oneinthechamber.support.StatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

public class OneInTheChamberModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    public CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsService statsService;
    private OneInTheChamberGameManager gameManager;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("one_in_the_chamber");

        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for one in the chamber module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        StatsAPI statsAPI = ModuleAPI.getStatsAPI();
        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();

        statsService = new StatsService(statsAPI, moduleInfo);
        statsService.registerStats();

        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");

        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }

        gameManager = new OneInTheChamberGameManager(moduleConfig, coreConfig, moduleInfo, statsService);
        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new OneInTheChamberSetup(this));

        if (moduleConfig != null && voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getTranslation(null, "vote_menu.name"),
                    moduleConfig.getTranslationList(null, "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleStart(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        gameManager.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return gameManager.freezePlayersOnCountdown();
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleGameStart(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        gameManager.handleEnd(context, result);
    }

    @Override
    public void onDisable() {
        gameManager.onDisable();
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new OneInTheChamberListener(gameManager));
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


    @Override
    public boolean requiresSpawnCapacityValidation() {
        return false;
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("setmode", true, "Configure Setmode", "Configure the module-specific setmode setup data.", List.of("/baa game <arena> one_in_the_chamber setmode"), "mode"),
                        new ModuleSetupStep("setregion", true, "Configure Setregion", "Configure the module-specific setregion setup data.", List.of("/baa game <arena> one_in_the_chamber setregion"), "selection region")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("setmode", "/baa game <arena> one_in_the_chamber setmode", "Configure setmode setup data.", true),
                        new ModuleSetupCommand("setregion", "/baa game <arena> one_in_the_chamber setregion", "Configure setregion setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("setmode", true, "Set the game mode.", context -> context.getData().has("basic.win_mode") || context.getData().has("basic.mode")),
                        new ModuleSetupStatusCheck<>("setregion", true, "Select the play area region.", context -> context.getData().has("game.play_area.bounds.min.x") && context.getData().has("game.play_area.bounds.max.x"))
                );
            }
        };
    }

}
