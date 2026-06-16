package org.blood.bloodJail;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageService {

    private final BloodJail plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration messages;

    public MessageService(BloodJail plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);

        InputStream resource = plugin.getResource("messages.yml");
        if (resource != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            this.messages.setDefaults(defaults);
        }

        boolean addedMissing = false;
        InputStream resource2 = plugin.getResource("messages.yml");
        if (resource2 != null) {
            YamlConfiguration defaults2 = YamlConfiguration.loadConfiguration(new InputStreamReader(resource2, StandardCharsets.UTF_8));
            for (String key : defaults2.getKeys(true)) {
                if (!defaults2.isConfigurationSection(key) && !this.messages.contains(key)) {
                    this.messages.set(key, defaults2.get(key));
                    addedMissing = true;
                }
            }
        }

        if (migrateLegacyMessages() || addedMissing) {
            try {
                this.messages.save(file);
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось сохранить обновленный messages.yml: " + ex.getMessage());
            }
        }
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        Component component = get(key, placeholders);
        if (sender instanceof Player player) {
            player.sendMessage(component);
            return;
        }

        // Console can always receive plain text even when message uses MiniMessage styling.
        sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(component));
    }

    public void send(Player player, String key, String... placeholders) {
        player.sendMessage(get(key, placeholders));
    }

    public void actionBar(Player player, String key, String... placeholders) {
        player.sendActionBar(get(key, placeholders));
    }

    public void broadcast(String key, String... placeholders) {
        Bukkit.getServer().broadcast(get(key, placeholders));
    }

    public Component get(String key, String... placeholders) {
        String template = messages.getString(key, "<red>Missing message key: " + key + "</red>");
        String prefixTemplate = messages.getString("prefix", "<#ff8c00><bold>BloodJail</bold></#ff8c00><white> | </white>");

        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.parsed("prefix", prefixTemplate));

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = normalizePlaceholderName(placeholders[i]);
            String value = placeholders[i + 1];
            resolvers.add(Placeholder.component(name, Component.text(value)));
        }

        return miniMessage.deserialize(template, resolvers.toArray(new TagResolver[0]));
    }

    private String normalizePlaceholderName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    private boolean migrateLegacyMessages() {
        boolean changed = false;

        changed |= replaceIfContains("prison.broadcast", "<jailedBy>", "<jailed-by>");
        changed |= replaceIfContains("check.info.jailed-by", "<jailedBy>", "<jailed-by>");

        changed |= forceValueIfDifferent("usage.prison",
                "<prefix><white>Использование: </white><#ff8c00>/prison [игрок] [время] [причина]</#ff8c00>");
        changed |= forceValueIfDifferent("usage.unprison",
                "<prefix><white>Использование: </white><#ff8c00>/unprison [игрок]</#ff8c00>");
        changed |= forceValueIfDifferent("usage.checkprison",
                "<prefix><white>Использование: </white><#ff8c00>/checkprison [игрок]</#ff8c00>");

        return changed;
    }

    private boolean replaceIfContains(String path, String target, String replacement) {
        String current = messages.getString(path);
        if (current == null || !current.contains(target)) {
            return false;
        }

        messages.set(path, current.replace(target, replacement));
        return true;
    }

    private boolean forceValueIfDifferent(String path, String expectedValue) {
        String current = messages.getString(path);
        if (expectedValue.equals(current)) {
            return false;
        }

        if (current == null
                || current.contains("&lt;")
                || current.contains("\\<")
                || current.contains("<игрок>")
                || current.contains("<время>")
                || current.contains("<причина>")) {
            messages.set(path, expectedValue);
            return true;
        }

        return false;
    }
}


