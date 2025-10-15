package id.cydev.cyannouncer.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.List;

public class BroadcastCommand implements SimpleCommand {

    private final VelocityAnnouncer plugin;

    public BroadcastCommand(VelocityAnnouncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /vbroadcast <server1,server2,...|all> <message>", NamedTextColor.RED));
            return;
        }

        String targets = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getPrefix() + message);

        if (targets.equalsIgnoreCase("all")) {
            plugin.getServer().sendMessage(componentMessage);
        } else {
            List<String> targetServers = Arrays.asList(targets.split(","));
            for (Player player : plugin.getServer().getAllPlayers()) {
                player.getCurrentServer().ifPresent(serverConnection -> {
                    String serverName = serverConnection.getServerInfo().getName();
                    if (targetServers.contains(serverName)) {
                        player.sendMessage(componentMessage);
                    }
                });
            }
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("announcer.broadcast");
    }
}