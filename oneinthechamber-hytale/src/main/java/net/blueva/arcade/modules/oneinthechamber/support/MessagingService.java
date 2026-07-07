package net.blueva.arcade.modules.oneinthechamber.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Holder;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MessagingService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final ModuleInfo moduleInfo;

    public MessagingService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig, ModuleInfo moduleInfo) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.moduleInfo = moduleInfo;
    }

    public void sendDescription(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                String winMode) {
        String descriptionKey = "description." + winMode;
        for (Player player : context.getPlayers()) {
            List<String> description = moduleConfig.getTranslationList(player, descriptionKey);
            if (description == null || description.isEmpty()) {
                description = moduleConfig.getTranslationList(player, "description");
            }
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                  int secondsLeft) {
        for (Player player : context.getPlayers()) {
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinish(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context) {
        for (Player player : context.getPlayers()) {
            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void sendDeathTitle(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                               Player target,
                               boolean killed) {
        if (killed) {
            context.getSoundsAPI().play(target, coreConfig.getSound("sounds.in_game.dead"));
            context.getTitlesAPI().sendRaw(target,
                    moduleConfig.getTranslation(target, "titles.you_died.title"),
                    moduleConfig.getTranslation(target, "titles.you_died.subtitle"),
                    0, 80, 20);
            return;
        }

        context.getSoundsAPI().play(target, coreConfig.getSound("sounds.in_game.classified"));
        context.getTitlesAPI().sendRaw(target,
                moduleConfig.getTranslation(target, "titles.classified.title"),
                moduleConfig.getTranslation(target, "titles.classified.subtitle"),
                0, 80, 20);
    }

    public void broadcastDeathMessage(GameContext<Player, Location, World, String, ItemStack, String, Holder, Entity> context,
                                      Player victim,
                                      Player killer) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(victim)) {
            return;
        }

        String path = killer != null ? "messages.deaths.killed_by_player" : "messages.deaths.generic";
        String message = getRandomMessage(path);

        if (message == null) {
            return;
        }

        message = message
                .replace("{victim}", victim.getPlayerRef().getUsername())
                .replace("{killer}", killer != null ? killer.getPlayerRef().getUsername() : "");

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getTranslationList(null, path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
