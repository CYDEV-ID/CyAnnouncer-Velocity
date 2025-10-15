package id.cydev.cyannouncer.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Plugin(id = "cyannouncer-velocity", name = "CyAnnouncer-Velocity", version = "1.6.0",
        description = "An advanced announcement plugin for Velocity.", authors = {"cydev-id"})
public class VelocityAnnouncer {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ScheduledTask announcementTask;

    private List<Announcement> allMessages;
    private Map<String, List<Announcement>> serverSpecificMessages;

    // State Tracking: Stores the current message index for each server
    private final Map<String, AtomicInteger> specificCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> allCounters = new ConcurrentHashMap<>();

    private String prefix;
    private int interval;

    @Inject
    public VelocityAnnouncer(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @com.velocitypowered.api.event.Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        server.getCommandManager().register("vbroadcast", new BroadcastCommand(this));
        server.getCommandManager().register("announcer", new AnnouncerReloadCommand(this));
        logger.info("Successfully registered commands.");

        startAnnouncements();
    }

    public void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            try {
                Files.createDirectories(dataDirectory);
                try (InputStream defaultConfig = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (defaultConfig != null) { Files.copy(defaultConfig, configFile.toPath()); }
                }
            } catch (Exception e) { logger.error("Failed to create the default configuration file!", e); return; }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().file(configFile).build();
        try {
            CommentedConfigurationNode config = loader.load();
            this.interval = config.node("interval").getInt(60);
            this.prefix = config.node("prefix").getString("&e[&l!&r&e] &r");

            this.allMessages = new ArrayList<>();
            this.serverSpecificMessages = new HashMap<>();

            List<? extends CommentedConfigurationNode> announcementNodes = config.node("announcements").childrenList();
            for (CommentedConfigurationNode node : announcementNodes) {
                List<String> servers = node.node("servers").getList(String.class, Collections.emptyList());
                List<String> lines = node.node("lines").getList(String.class, Collections.emptyList());

                if (servers.isEmpty() || lines.isEmpty()) continue;

                Announcement announcement = new Announcement(servers, lines);
                if (servers.contains("all")) {
                    allMessages.add(announcement);
                } else {
                    for (String serverName : servers) {
                        serverSpecificMessages.computeIfAbsent(serverName, k -> new ArrayList<>()).add(announcement);
                    }
                }
            }

            logger.info("Configuration loaded. Found " + allMessages.size() + " global announcements and messages for " + serverSpecificMessages.size() + " specific servers.");
            specificCounters.clear();
            allCounters.clear();
        } catch (Exception e) { logger.error("Failed to load the configuration!", e); }
    }

    public void startAnnouncements() {
        if (announcementTask != null) announcementTask.cancel();

        if (allMessages.isEmpty() && serverSpecificMessages.isEmpty() || interval <= 0) {
            logger.warn("Announcements are disabled (no messages found or invalid interval).");
            return;
        }

        announcementTask = server.getScheduler().buildTask(this, () -> {
            if (server.getPlayerCount() == 0) return;

            Map<String, List<Player>> playersByServer = server.getAllPlayers().stream()
                    .filter(p -> p.getCurrentServer().isPresent())
                    .collect(Collectors.groupingBy(p -> p.getCurrentServer().get().getServerInfo().getName()));

            for (String serverName : playersByServer.keySet()) {
                List<Announcement> specificMessages = serverSpecificMessages.getOrDefault(serverName, Collections.emptyList());

                AtomicInteger specificCounter = specificCounters.computeIfAbsent(serverName, k -> new AtomicInteger(0));
                AtomicInteger allCounter = allCounters.computeIfAbsent(serverName, k -> new AtomicInteger(0));

                Announcement announcementToSend = null;

                if (!specificMessages.isEmpty() && specificCounter.get() < specificMessages.size()) {
                    announcementToSend = specificMessages.get(specificCounter.getAndIncrement());
                } else {
                    if (!allMessages.isEmpty()) {
                        announcementToSend = allMessages.get(allCounter.getAndIncrement());
                        if (allCounter.get() >= allMessages.size()) allCounter.set(0);
                    }
                    specificCounter.set(0);
                }

                if (announcementToSend != null) {
                    List<Player> targetPlayers = playersByServer.get(serverName);
                    for (String line : announcementToSend.lines()) {
                        Component finalLine = LegacyComponentSerializer.legacyAmpersand().deserialize(this.prefix + line);
                        for (Player player : targetPlayers) {
                            player.sendMessage(finalLine);
                        }
                    }
                }
            }
        }).repeat(Duration.ofSeconds(this.interval)).schedule();

        logger.info("Advanced announcements scheduler started, running every " + this.interval + " seconds.");
    }

    public ProxyServer getServer() { return server; }
    public String getPrefix() { return prefix; }
    public Logger getLogger() { return logger; }
}