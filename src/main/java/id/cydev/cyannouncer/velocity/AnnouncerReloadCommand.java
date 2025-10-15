package id.cydev.cyannouncer.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class AnnouncerReloadCommand implements SimpleCommand {

    private final VelocityAnnouncer plugin;

    public AnnouncerReloadCommand(VelocityAnnouncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        plugin.loadConfig();
        plugin.startAnnouncements();

        invocation.source().sendMessage(Component.text("CyAnnouncer-Velocity configuration has been reloaded.", NamedTextColor.GREEN));
        plugin.getLogger().info("Configuration has been reloaded by " + invocation.source().toString() + ".");
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("announcer.reload");
    }
}