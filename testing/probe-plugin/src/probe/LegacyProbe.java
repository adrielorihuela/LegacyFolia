package probe;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * LegacyFolia test plugin. {@code /probe <tag>} logs, one "PROBE[tag] key=value" line each, what a plugin can read
 * of the server through the Bukkit, Spigot and Paper configuration APIs. {@code /probeteam} puts players into
 * scoreboard teams (Folia has no /team).
 */
public final class LegacyProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        this.probe("enable");
    }

    private void out(final String tag, final String key, final Object value) {
        this.getLogger().info("PROBE[" + tag + "] " + key + "=" + value);
    }

    private void probe(final String tag) {
        // Bukkit API
        this.out(tag, "bukkit.motd", Bukkit.getMotd());
        this.out(tag, "bukkit.maxPlayers", Bukkit.getMaxPlayers());
        this.out(tag, "bukkit.port", Bukkit.getPort());
        this.out(tag, "bukkit.viewDistance", Bukkit.getViewDistance());
        this.out(tag, "bukkit.onlineMode", Bukkit.getOnlineMode());
        this.out(tag, "bukkit.hasWhitelist", Bukkit.hasWhitelist());
        this.out(tag, "bukkit.whitelistEnforced", Bukkit.isWhitelistEnforced());
        this.out(tag, "bukkit.whitelisted", Bukkit.getWhitelistedPlayers().stream().map(p -> p.getName()).sorted().toList());
        this.out(tag, "bukkit.ops", Bukkit.getOperators().stream().map(p -> p.getName()).sorted().toList());
        this.out(tag, "bukkit.banned", Bukkit.getBannedPlayers().stream().map(p -> p.getName()).sorted().toList());
        this.out(tag, "bukkit.spawnRadius", Bukkit.getSpawnRadius());
        this.out(tag, "bukkit.monsterSpawnLimit", Bukkit.getMonsterSpawnLimit());
        this.out(tag, "bukkit.idleTimeout", Bukkit.getIdleTimeout());
        this.out(tag, "bukkit.worlds", Bukkit.getWorlds().stream().map(w -> w.getName()).toList());
        this.out(tag, "world.monsterSpawnLimit", Bukkit.getWorlds().get(0).getSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER));
        this.out(tag, "world.viewDistance", Bukkit.getWorlds().get(0).getViewDistance());
        // Spigot API
        final YamlConfiguration bukkit = Bukkit.spigot().getBukkitConfig();
        final YamlConfiguration spigot = Bukkit.spigot().getSpigotConfig();
        this.out(tag, "spigot.getBukkitConfig.spawn-limits.monsters", bukkit.get("spawn-limits.monsters"));
        this.out(tag, "spigot.getSpigotConfig.world-settings.default.mob-spawn-range", spigot.get("world-settings.default.mob-spawn-range"));
        this.out(tag, "spigot.getSpigotConfig.world-settings.default.merge-radius.item", spigot.get("world-settings.default.merge-radius.item"));
        this.out(tag, "spigot.getSpigotConfig.keys", spigot.getKeys(true).size());
        this.out(tag, "spigot.getConfig.keys", Bukkit.spigot().getConfig().getKeys(true).size());
        // Paper configuration (global)
        final YamlConfiguration paper = Bukkit.spigot().getPaperConfig();
        this.out(tag, "paper.getPaperConfig.proxies.velocity.enabled", paper.get("proxies.velocity.enabled"));
        this.out(tag, "paper.getPaperConfig.proxies.velocity.secret.present", !String.valueOf(paper.get("proxies.velocity.secret", "")).isEmpty());
        this.out(tag, "paper.getPaperConfig.misc.enable-nether", paper.get("misc.enable-nether"));
        this.out(tag, "paper.getPaperConfig.keys", paper.getKeys(true).size());
        // Paper's server configuration API
        try {
            this.out(tag, "paper.getServerConfig", Bukkit.getServerConfig().getClass().getSimpleName());
        } catch (final Throwable t) {
            this.out(tag, "paper.getServerConfig", "ERR " + t);
        }
        // Files a plugin might look for in the server root
        final File root = new File(".").getAbsoluteFile().getParentFile();
        final List<String> present = Arrays.stream(new String[] {"bukkit.yml", "spigot.yml", "commands.yml", "paper-global.yml",
            "paper-world-defaults.yml", "server.properties", "whitelist.json", "ops.json", "eula.txt", "config/server.toml"})
            .filter(n -> new File(root, n).exists()).toList();
        this.out(tag, "rootFiles", present);
        final File[] cfg = new File(root, "config").listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".json"));
        this.out(tag, "config.yml-or-json", cfg == null ? "[]" : Arrays.stream(cfg).map(File::getName).sorted().toList());
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (command.getName().equals("probe")) {
            this.probe(args.length > 0 ? args[0] : "manual");
            return true;
        }
        // /probeteam add <team> <color> <player...> | remove <team> | list | bukkit
        // Folia's CraftScoreboard.registerNewTeam throws UnsupportedOperationException, so the teams are made on the
        // main scoreboard of the NMS server through reflection (the commands run on the global region).
        if (args.length == 0) {
            return false;
        }
        try {
            final Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            final Object board = server.getClass().getMethod("getScoreboard").invoke(server);
            final Class<?> boardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
            final Class<?> teamClass = Class.forName("net.minecraft.world.scores.PlayerTeam");
            final Class<?> colorClass = Class.forName("net.minecraft.world.scores.TeamColor");
            switch (args[0]) {
                case "bukkit" -> {
                    try {
                        Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("bukkit_probe");
                        sender.sendMessage("Bukkit registerNewTeam: worked");
                    } catch (final Throwable t) {
                        sender.sendMessage("Bukkit registerNewTeam: " + t);
                    }
                }
                case "add" -> {
                    Object team = boardClass.getMethod("getPlayerTeam", String.class).invoke(board, args[1]);
                    if (team == null) {
                        team = boardClass.getMethod("addPlayerTeam", String.class).invoke(board, args[1]);
                    }
                    final Object color = colorClass.getMethod("valueOf", String.class).invoke(null, args[2].toUpperCase());
                    teamClass.getMethod("setColor", java.util.Optional.class).invoke(team, java.util.Optional.of(color));
                    for (int i = 3; i < args.length; i++) {
                        boardClass.getMethod("addPlayerToTeam", String.class, teamClass).invoke(board, args[i], team);
                    }
                    sender.sendMessage("team " + args[1] + " color=" + args[2] + " members=" + java.util.Arrays.toString(java.util.Arrays.copyOfRange(args, 3, args.length)));
                }
                case "which" -> {
                    final Object team = boardClass.getMethod("getPlayersTeam", String.class).invoke(board, args[1]);
                    sender.sendMessage("team of " + args[1] + ": " + (team == null ? "none" : teamClass.getMethod("getName").invoke(team) + " color=" + teamClass.getMethod("getColor").invoke(team)));
                }
                case "color" -> {
                    final Object team = boardClass.getMethod("getPlayerTeam", String.class).invoke(board, args[1]);
                    teamClass.getMethod("setColor", java.util.Optional.class).invoke(team, java.util.Optional.of(colorClass.getMethod("valueOf", String.class).invoke(null, args[2].toUpperCase())));
                    sender.sendMessage("team " + args[1] + " recoloured " + args[2]);
                }
                case "remove" -> {
                    final Object team = boardClass.getMethod("getPlayerTeam", String.class).invoke(board, args[1]);
                    if (team != null) {
                        boardClass.getMethod("removePlayerTeam", teamClass).invoke(board, team);
                    }
                    sender.sendMessage("removed " + args[1]);
                }
                default -> sender.sendMessage("teams: " + boardClass.getMethod("getTeamNames").invoke(board));
            }
        } catch (final Throwable t) {
            this.getLogger().severe("probeteam failed: " + t);
            t.printStackTrace();
            sender.sendMessage("probeteam failed: " + t);
        }
        return true;
    }
}
