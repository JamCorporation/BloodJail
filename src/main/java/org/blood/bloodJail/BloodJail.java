package org.blood.bloodJail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BloodJail extends JavaPlugin {

    private PrisonManager prisonManager;
    private MessageService messageService;
    private DiscordWebhook discordWebhook;
    private BukkitTask timerTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messageService = new MessageService(this);
        this.messageService.load();

        this.discordWebhook = new DiscordWebhook(getLogger());
        this.discordWebhook.setUrl(getConfig().getString("discord.webhook-url", "disabled"));

        this.prisonManager = new PrisonManager(this);
        this.prisonManager.load();

        PrisonCommandHandler commandHandler = new PrisonCommandHandler(this, prisonManager);
        bindCommand("prison", commandHandler);
        bindCommand("unprison", commandHandler);
        bindCommand("checkprison", commandHandler);
        bindCommand("setprison", commandHandler);

        getServer().getPluginManager().registerEvents(new PrisonListener(this, prisonManager), this);
        startTimerTask();
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (prisonManager != null) {
            prisonManager.save();
        }
    }

    public Location getJailLocation() {
        String worldName = getConfig().getString("jail.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = getConfig().getDouble("jail.x", 0.5D);
        double y = getConfig().getDouble("jail.y", 100.0D);
        double z = getConfig().getDouble("jail.z", 0.5D);
        float yaw = (float) getConfig().getDouble("jail.yaw", 0.0D);
        float pitch = (float) getConfig().getDouble("jail.pitch", 0.0D);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void releasePlayer(Player player, String reasonLabel, boolean broadcast) {
        releasePlayer((OfflinePlayer) player, reasonLabel, broadcast);
    }

    public void releasePlayer(OfflinePlayer player, String reasonLabel, boolean broadcast) {
        PrisonRecord record = prisonManager.unjail(player.getUniqueId());
        if (record == null) {
            return;
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null) {
            Location returnLocation = record.getArrestLocation();
            if (returnLocation != null && returnLocation.getWorld() != null) {
                onlinePlayer.teleport(returnLocation);
            }

            messageService.send(onlinePlayer, "release.personal");
        }
        String playerName = record.getPlayerName() != null ? record.getPlayerName() : player.getName();
        if (playerName == null) {
            playerName = player.getUniqueId().toString();
        }
        if (broadcast) {
            messageService.broadcast("release.broadcast", "player", playerName, "reason", reasonLabel);
        }
        discordWebhook.sendRelease(playerName, reasonLabel);
    }

    private void startTimerTask() {
        timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean changed = false;
            for (Player player : Bukkit.getOnlinePlayers()) {
                PrisonRecord record = prisonManager.getRecord(player.getUniqueId());
                if (record == null) {
                    continue;
                }

                long remaining = record.decreaseRemainingMillis(1000L);
                changed = true;
                if (remaining <= 0L) {
                    releasePlayer(player, "срок заключения истек", true);
                    continue;
                }

                String formatted = TimeUtil.formatCompactDuration(remaining);
                messageService.actionBar(player, "jail.timer", "time", formatted);
            }

            if (changed) {
                prisonManager.save();
            }
        }, 20L, 20L);
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    private void bindCommand(String commandName, PrisonCommandHandler handler) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().warning("Command not found in plugin.yml: " + commandName);
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }
}
