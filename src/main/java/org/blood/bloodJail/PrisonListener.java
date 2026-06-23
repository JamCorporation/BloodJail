package org.blood.bloodJail;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class PrisonListener implements Listener {

    private final BloodJail plugin;
    private final PrisonManager prisonManager;
    private final MessageService messages;

    public PrisonListener(BloodJail plugin, PrisonManager prisonManager) {
        this.plugin = plugin;
        this.prisonManager = prisonManager;
        this.messages = plugin.getMessageService();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("bloodjail.bypass")) {
            return;
        }
        if (prisonManager.isJailed(player.getUniqueId())) {
            event.setCancelled(true);
            messages.send(player, "jail.block.chat");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("bloodjail.bypass")) {
            return;
        }
        if (prisonManager.isJailed(player.getUniqueId())) {
            event.setCancelled(true);
            messages.send(player, "jail.block.drop");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        Player victim = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;

        if (attacker != null && !attacker.hasPermission("bloodjail.bypass")
                && prisonManager.isJailed(attacker.getUniqueId())) {
            event.setCancelled(true);
            messages.send(attacker, "jail.block.fight");
            return;
        }

        if (victim != null && !victim.hasPermission("bloodjail.bypass")
                && prisonManager.isJailed(victim.getUniqueId())) {
            event.setCancelled(true);
            if (attacker != null) {
                messages.send(attacker, "jail.block.victim-protected");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("bloodjail.bypass")) {
            return;
        }
        if (prisonManager.isJailed(player.getUniqueId())) {
            String message = event.getMessage().toLowerCase();
            if (message.startsWith("/warp") || message.startsWith("/scale")) {
                event.setCancelled(true);
                messages.send(player, "jail.block.command");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PrisonRecord record = prisonManager.getRecord(player.getUniqueId());
        if (record == null) {
            return;
        }

        if (record.isExpired(System.currentTimeMillis())) {
            plugin.releasePlayer(player, "СЃСЂРѕРє Р·Р°РєР»СЋС‡РµРЅРёСЏ РёСЃС‚РµРє", true);
            return;
        }

        if (record.shouldCaptureArrestOnJoin()) {
            record.setArrestLocation(player.getLocation().clone());
            record.setCaptureArrestOnJoin(false);
            prisonManager.save();
        }

        if (plugin.getJailLocation() != null) {
            player.teleport(plugin.getJailLocation());
        }

        long remaining = record.getRemainingMillis(System.currentTimeMillis());
        messages.send(player, "jail.still-jailed", "time", TimeUtil.formatCompactDuration(remaining));
        messages.send(player, "jail.still-jailed-by", "jailed-by", record.getJailedBy());
        messages.send(player, "jail.still-jailed-reason", "reason", record.getReason());
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
