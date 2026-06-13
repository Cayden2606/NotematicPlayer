package me.cayde26.notematicPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class NotematicCommand implements CommandExecutor, TabCompleter {
    private final NotematicPlayer plugin;
    private final SongManager songManager;
    private final SongPlaybackManager playbackManager;

    public NotematicCommand(NotematicPlayer plugin, SongManager songManager, SongPlaybackManager playbackManager) {
        this.plugin = plugin;
        this.songManager = songManager;
        this.playbackManager = playbackManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isNormalPlayer(sender) && !plugin.isPlayerCommandsEnabled()) {
            sender.sendMessage("§cCommands are currently disabled for players by an administrator.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
            case "play":
                handlePlay(sender, args);
                break;
            case "pause":
                handlePause(sender, args);
                break;
            case "resume":
                handleResume(sender, args);
                break;
            case "stop":
                handleStop(sender, args);
                break;
            case "seek":
                handleSeek(sender, args);
                break;
            case "volume":
                handleVolume(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "active":
                handleActive(sender);
                break;
            case "commands":
                handleCommands(sender, args);
                break;
            default:
                sender.sendMessage("§cUnknown subcommand. Use §f/notematic§c for help.");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l--- Notematic Player Info ---");
        sender.sendMessage("§eVersion: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eAuthor: §fCayden26");
        sender.sendMessage("§ePowered by: §fNotematic Studio");
        sender.sendMessage("§ePlayer Commands: " + (plugin.isPlayerCommandsEnabled() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§6§l--- Player Commands ---");
        sender.sendMessage("§e/notematic play <song> [target] [loop] §7- Play a song");
        sender.sendMessage("§e/notematic stop [#ID|song|player] [at x y z] §7- Stop playing music");
        sender.sendMessage("§e/notematic seek <#ID|song|player> <value> §7- Seek music (+/-10s, 0s)");
        sender.sendMessage("§e/notematic volume <value>           §7- Adjust your personal volume (0-100%)");
        sender.sendMessage("§e/notematic volume #ID <value>       §7- Adjust specific playback volume multiplier");
        sender.sendMessage("§e/notematic list                    §7- List all loaded songs");
        sender.sendMessage("§e/notematic active                  §7- Show all currently playing songs");
        sender.sendMessage("§e/notematic help                    §7- Show this help menu");
        if (sender.hasPermission("notematic.admin") || sender.isOp()) {
            sender.sendMessage("§6§l--- Admin Commands ---");
            sender.sendMessage("§e/notematic volume song <name> <val>   §7- Adjust song volume multiplier");
            sender.sendMessage("§e/notematic commands [true|false]   §7- Toggle/set commands for normal players");
            sender.sendMessage("§e/notematic reload                  §7- Reload songs from files");
        }
        sender.sendMessage("§6§l---------------------------------");
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /notematic play <song> [target] [loop]");
            return;
        }

        boolean loop = false;
        int effectiveLength = args.length;
        if (args.length > 2 && args[args.length - 1].equalsIgnoreCase("loop")) {
            loop = true;
            effectiveLength = args.length - 1;
        }

        String songName = args[1];
        Song song = songManager.getSong(songName);
        if (song == null || (song.isPrivate() && !sender.hasPermission("notematic.admin") && !sender.isOp())) {
            sender.sendMessage("§cSong '" + songName + "' not found! Use §f/notematic list§c to see available songs.");
            return;
        }

        if (loop) {
            if (!sender.hasPermission("notematic.play.loop") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to play looping music.");
                return;
            }
        }

        String initiatorName = sender.getName();

        // Check for location-based target: play <song> at <x> <y> <z> [radius] [volume] [loop]
        if (effectiveLength >= 3 && args[2].equalsIgnoreCase("at")) {
            if (!sender.hasPermission("notematic.play.location") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to play music at a location.");
                return;
            }
            if (effectiveLength < 6) {
                sender.sendMessage("§cUsage: /notematic play <song> at <x> <y> <z> [radius] [volume] [loop]");
                return;
            }
            Location loc = parseLocation(sender, args[3], args[4], args[5]);
            if (loc == null) {
                sender.sendMessage("§cInvalid coordinates! Use numbers or relative coordinates (~).");
                return;
            }

            double radius = 32.0;
            if (effectiveLength >= 7) {
                try {
                    radius = Double.parseDouble(args[6]);
                    if (radius <= 0) {
                        sender.sendMessage("§cRadius must be greater than 0.");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid radius value.");
                    return;
                }
            }

            double volumeMultiplier = 1.0;
            if (effectiveLength >= 8) {
                double val = parseVolumeValue(sender, args[7]);
                if (val < 0.0) return;
                if (val > 1.0) {
                    val = 1.0;
                    sender.sendMessage("§6[Warning] Volume cannot exceed 100%. Clamping to 100%.");
                }
                volumeMultiplier = val;
            }

            playbackManager.startPlayback(loc, radius, volumeMultiplier, song, loop, true, initiatorName);
            sender.sendMessage(String.format("§aStarted playing '%s' at (%d, %d, %d) with radius %.0f at %.0f%% volume%s.", 
                song.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), radius, volumeMultiplier * 100, loop ? " (looping)" : ""));
            return;
        }

        // Standard target resolution
        String target = null;
        if (effectiveLength >= 3) {
            target = args[2];
        }

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*") || target.equalsIgnoreCase("all")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to play songs for all players.");
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playbackManager.startPlayback(player, song, true, false, initiatorName, loop);
                }
                sender.sendMessage("§aStarted playing '" + song.getName() + "' for all online players.");
            } else {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(target)) {
                    sender.sendMessage("§cYou do not have permission to play songs for other players.");
                    return;
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage("§cPlayer '" + target + "' is not online.");
                    return;
                }
                playbackManager.startPlayback(targetPlayer, song, true, false, initiatorName, loop);
                sender.sendMessage("§aStarted playing '" + song.getName() + "' for player " + targetPlayer.getName() + ".");
            }
        } else {
            // Play for sender
            if (sender instanceof Player player) {
                playbackManager.startPlayback(player, song, true, false, initiatorName, loop);
            } else {
                sender.sendMessage("§cPlease specify a target player or location when running from console.");
            }
        }
    }

    private boolean isNormalPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return !player.isOp() && !player.hasPermission("notematic.admin");
        }
        return false;
    }

    private void handleCommands(CommandSender sender, String[] args) {
        if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to run this command.");
            return;
        }

        boolean newVal;
        if (args.length >= 2) {
            String arg1 = args[1].toLowerCase();
            if (arg1.equals("true") || arg1.equals("yes") || arg1.equals("on")) {
                newVal = true;
            } else if (arg1.equals("false") || arg1.equals("no") || arg1.equals("off")) {
                newVal = false;
            } else {
                sender.sendMessage("§cInvalid value. Use §ftrue§c or §ffalse§c.");
                return;
            }
        } else {
            newVal = !plugin.isPlayerCommandsEnabled();
        }

        plugin.setPlayerCommandsEnabled(newVal);
        if (newVal) {
            sender.sendMessage("§aEnabled commands for normal players.");
        } else {
            sender.sendMessage("§aDisabled commands for normal players. Only OPs/admins can use Notematic Player commands now.");
        }
    }

    private void handleActive(CommandSender sender) {
        List<SongPlayback> active = playbackManager.getActivePlaybacks();
        if (active.isEmpty()) {
            sender.sendMessage("§eNo songs are currently playing.");
            return;
        }

        sender.sendMessage("§6§l--- Active Song Playbacks (" + active.size() + ") ---");
        for (SongPlayback playback : active) {
            String targetStr;
            if (playback.getLocation() != null) {
                Location loc = playback.getLocation();
                targetStr = String.format("Location (%d, %d, %d) | Radius: %.0fb", 
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), playback.getRadius());
            } else {
                UUID listenerUuid = playback.getListenerUuid();
                Player listener = listenerUuid != null ? Bukkit.getPlayer(listenerUuid) : null;
                targetStr = "Player: " + (listener != null ? listener.getName() : "Unknown (" + listenerUuid + ")");
            }

            Song song = playback.getSong();
            double progressPercent = 0.0;
            if (song.getMaxTick() > 0) {
                progressPercent = (playback.getCurrentVirtualTick() / song.getMaxTick()) * 100.0;
                if (progressPercent > 100.0) progressPercent = 100.0;
            }

            sender.sendMessage(String.format("§8[§a#%d§8] §eSong: §f%s §7| §eTarget: §f%s §7| §eInitiator: §f%s §7| §eProgress: §f%.1f%% §7| §eLooping: §f%s §7| §ePaused: §f%s",
                playback.getId(),
                song.getName(),
                targetStr,
                playback.getInitiator(),
                progressPercent,
                playback.isLooping() ? "Yes" : "No",
                playback.isPaused() ? "Yes" : "No"
            ));
        }
        sender.sendMessage("§6§l-----------------------------------");
    }

    private void handlePause(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Pause all playbacks initiated by/for the sender
            if (sender instanceof Player player) {
                playbackManager.pausePlaybacksByPlayer(player);
                sender.sendMessage("§ePaused all of your active playbacks.");
            } else {
                playbackManager.pauseAll();
                sender.sendMessage("§ePaused all active playbacks on the server.");
            }
            return;
        }

        // Check for location pause syntax: pause at <x> <y> <z> or pause <song> at <x> <y> <z>
        Location pauseLoc = null;
        String locSong = null;
        if (args.length >= 5 && args[1].equalsIgnoreCase("at")) {
            pauseLoc = parseLocation(sender, args[2], args[3], args[4]);
        } else if (args.length >= 6 && args[2].equalsIgnoreCase("at")) {
            locSong = args[1];
            pauseLoc = parseLocation(sender, args[3], args[4], args[5]);
        }

        if (pauseLoc != null) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to pause location-based playbacks.");
                return;
            }
            int pausedCount = playbackManager.pausePlaybacksAtLocation(pauseLoc, locSong);
            if (pausedCount > 0) {
                sender.sendMessage("§ePaused " + pausedCount + " location-based playbacks at (" + pauseLoc.getBlockX() + ", " + pauseLoc.getBlockY() + ", " + pauseLoc.getBlockZ() + ").");
            } else {
                sender.sendMessage("§cNo active playbacks found at that location.");
            }
            return;
        }

        String arg = args[1];

        // 1. Unique ID Match
        if (arg.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(arg.substring(1));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid playback ID format. Use e.g. #3");
                return;
            }
            SongPlayback pb = playbackManager.getPlaybackById(id);
            if (pb == null) {
                sender.sendMessage("§cPlayback with ID #" + id + " is not currently active.");
                return;
            }
            // Permission gate: admin/op OR initiator
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                sender.sendMessage("§cYou do not have permission to pause this playback.");
                return;
            }
            pb.setPaused(true);
            sender.sendMessage("§ePaused playback ID #" + id + " (" + pb.getSong().getName() + ").");
            return;
        }

        // 2. Song Name Match
        Song song = songManager.getSong(arg);
        if (song != null) {
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                playbackManager.pausePlaybacksBySong(song.getName());
                sender.sendMessage("§ePaused all playbacks of song '" + song.getName() + "'.");
            } else {
                // Normal player pauses only their initiated playbacks of this song
                boolean pausedAny = false;
                for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                    if (pb.getInitiator().equalsIgnoreCase(sender.getName()) && pb.getSong().getName().equalsIgnoreCase(song.getName())) {
                        pb.setPaused(true);
                        pausedAny = true;
                    }
                }
                if (pausedAny) {
                    sender.sendMessage("§ePaused your playbacks of song '" + song.getName() + "'.");
                } else {
                    sender.sendMessage("§cYou have no active playbacks of song '" + song.getName() + "'.");
                }
            }
            return;
        }

        // 3. Player Name Match
        Player targetPlayer = Bukkit.getPlayer(arg);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(targetPlayer.getName())) {
                sender.sendMessage("§cYou do not have permission to pause playbacks for other players.");
                return;
            }
            playbackManager.pausePlaybacksByPlayer(targetPlayer);
            sender.sendMessage("§ePaused all playbacks for player " + targetPlayer.getName() + ".");
            return;
        }

        sender.sendMessage("§cCould not resolve pause target '" + arg + "'. (Check ID #, song name, or player name).");
    }

    private void handleResume(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Resume all playbacks initiated by/for the sender
            if (sender instanceof Player player) {
                playbackManager.resumePlaybacksByPlayer(player);
                sender.sendMessage("§aResumed all of your paused playbacks.");
            } else {
                playbackManager.resumeAll();
                sender.sendMessage("§aResumed all playbacks on the server.");
            }
            return;
        }

        // Check for location resume syntax: resume at <x> <y> <z> or resume <song> at <x> <y> <z>
        Location resumeLoc = null;
        String locSong = null;
        if (args.length >= 5 && args[1].equalsIgnoreCase("at")) {
            resumeLoc = parseLocation(sender, args[2], args[3], args[4]);
        } else if (args.length >= 6 && args[2].equalsIgnoreCase("at")) {
            locSong = args[1];
            resumeLoc = parseLocation(sender, args[3], args[4], args[5]);
        }

        if (resumeLoc != null) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to resume location-based playbacks.");
                return;
            }
            int resumedCount = playbackManager.resumePlaybacksAtLocation(resumeLoc, locSong);
            if (resumedCount > 0) {
                sender.sendMessage("§aResumed " + resumedCount + " location-based playbacks at (" + resumeLoc.getBlockX() + ", " + resumeLoc.getBlockY() + ", " + resumeLoc.getBlockZ() + ").");
            } else {
                sender.sendMessage("§cNo paused playbacks found at that location.");
            }
            return;
        }

        String arg = args[1];

        // 1. Unique ID Match
        if (arg.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(arg.substring(1));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid playback ID format. Use e.g. #3");
                return;
            }
            SongPlayback pb = playbackManager.getPlaybackById(id);
            if (pb == null) {
                sender.sendMessage("§cPlayback with ID #" + id + " is not currently active.");
                return;
            }
            // Permission gate: admin/op OR initiator
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                sender.sendMessage("§cYou do not have permission to resume this playback.");
                return;
            }
            pb.setPaused(false);
            sender.sendMessage("§aResumed playback ID #" + id + " (" + pb.getSong().getName() + ").");
            return;
        }

        // 2. Song Name Match
        Song song = songManager.getSong(arg);
        if (song != null) {
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                playbackManager.resumePlaybacksBySong(song.getName());
                sender.sendMessage("§aResumed all playbacks of song '" + song.getName() + "'.");
            } else {
                // Normal player resumes only their initiated playbacks of this song
                boolean resumedAny = false;
                for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                    if (pb.getInitiator().equalsIgnoreCase(sender.getName()) && pb.getSong().getName().equalsIgnoreCase(song.getName())) {
                        pb.setPaused(false);
                        resumedAny = true;
                    }
                }
                if (resumedAny) {
                    sender.sendMessage("§aResumed your playbacks of song '" + song.getName() + "'.");
                } else {
                    sender.sendMessage("§cYou have no active playbacks of song '" + song.getName() + "'.");
                }
            }
            return;
        }

        // 3. Player Name Match
        Player targetPlayer = Bukkit.getPlayer(arg);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(targetPlayer.getName())) {
                sender.sendMessage("§cYou do not have permission to resume playbacks for other players.");
                return;
            }
            playbackManager.resumePlaybacksByPlayer(targetPlayer);
            sender.sendMessage("§aResumed all playbacks for player " + targetPlayer.getName() + ".");
            return;
        }

        sender.sendMessage("§cCould not resolve resume target '" + arg + "'. (Check ID #, song name, or player name).");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Stop all playbacks initiated by/for the sender
            if (sender instanceof Player player) {
                playbackManager.stopPlayback(player);
                sender.sendMessage("§aStopped all of your active playbacks.");
            } else {
                playbackManager.stopAll();
                sender.sendMessage("§aStopped all active playbacks on the server.");
            }
            return;
        }

        // Check for location stop syntax: stop at <x> <y> <z> or stop <song> at <x> <y> <z>
        Location stopLoc = null;
        String locSong = null;
        if (args.length >= 5 && args[1].equalsIgnoreCase("at")) {
            stopLoc = parseLocation(sender, args[2], args[3], args[4]);
        } else if (args.length >= 6 && args[2].equalsIgnoreCase("at")) {
            locSong = args[1];
            stopLoc = parseLocation(sender, args[3], args[4], args[5]);
        }

        if (stopLoc != null) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to stop location-based playbacks.");
                return;
            }
            int stoppedCount = playbackManager.stopPlaybacksAtLocation(stopLoc, locSong);
            if (stoppedCount > 0) {
                sender.sendMessage("§aStopped " + stoppedCount + " location-based playbacks at (" + stopLoc.getBlockX() + ", " + stopLoc.getBlockY() + ", " + stopLoc.getBlockZ() + ").");
            } else {
                sender.sendMessage("§cNo active playbacks found at that location.");
            }
            return;
        }

        String arg = args[1];

        // Resolution Order:
        // 1. Unique ID Match
        if (arg.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(arg.substring(1));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid playback ID format. Use e.g. #3");
                return;
            }
            SongPlayback pb = playbackManager.getPlaybackById(id);
            if (pb == null) {
                sender.sendMessage("§cPlayback with ID #" + id + " is not currently active.");
                return;
            }
            // Permission gate: admin/op OR initiator
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                sender.sendMessage("§cYou do not have permission to stop this playback.");
                return;
            }
            playbackManager.stopPlayback(pb);
            sender.sendMessage("§aStopped playback ID #" + id + " (" + pb.getSong().getName() + ").");
            return;
        }

        // 2. Song Name Match
        Song song = songManager.getSong(arg);
        if (song != null) {
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                playbackManager.stopPlaybacksBySong(song.getName());
                sender.sendMessage("§aStopped all playbacks of song '" + song.getName() + "'.");
            } else {
                // Normal player stops only their initiated playbacks of this song
                Iterator<SongPlayback> it = playbackManager.getActivePlaybacks().iterator();
                boolean stoppedAny = false;
                while (it.hasNext()) {
                    SongPlayback pb = it.next();
                    if (pb.getInitiator().equalsIgnoreCase(sender.getName()) && pb.getSong().getName().equalsIgnoreCase(song.getName())) {
                        playbackManager.stopPlayback(pb);
                        stoppedAny = true;
                    }
                }
                if (stoppedAny) {
                    sender.sendMessage("§aStopped your playbacks of song '" + song.getName() + "'.");
                } else {
                    sender.sendMessage("§cYou have no active playbacks of song '" + song.getName() + "'.");
                }
            }
            return;
        }

        // 3. Player Name Match
        Player targetPlayer = Bukkit.getPlayer(arg);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(targetPlayer.getName())) {
                sender.sendMessage("§cYou do not have permission to stop playbacks for other players.");
                return;
            }
            playbackManager.stopPlaybacksByPlayer(targetPlayer);
            sender.sendMessage("§aStopped all playbacks for player " + targetPlayer.getName() + ".");
            return;
        }

        sender.sendMessage("§cCould not resolve stop target '" + arg + "'. (Check ID #, song name, or player name).");
    }

    private void handleSeek(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /notematic seek <#ID|song|player> <value>");
            return;
        }

        String targetStr = args[1];
        String seekValueStr = args[2];

        // 1. Resolve ID target
        if (targetStr.startsWith("#")) {
            int id;
            try {
                id = Integer.parseInt(targetStr.substring(1));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid playback ID format. Use e.g. #3");
                return;
            }
            SongPlayback pb = playbackManager.getPlaybackById(id);
            if (pb == null) {
                sender.sendMessage("§cPlayback with ID #" + id + " is not currently active.");
                return;
            }
            // Permission check
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                sender.sendMessage("§cYou do not have permission to seek this playback.");
                return;
            }
            if (pb.seek(seekValueStr)) {
                sender.sendMessage("§aSeeked playback ID #" + id + " (" + pb.getSong().getName() + ") to " + playbackManager.formatPosition(pb) + ".");
            } else {
                sender.sendMessage("§cInvalid seek value format (e.g. +10s, -5t, 120s).");
            }
            return;
        }

        // 2. Resolve Song target
        Song song = songManager.getSong(targetStr);
        if (song != null) {
            boolean seekedAny = false;
            for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                if (pb.getSong().getName().equalsIgnoreCase(song.getName())) {
                    if (sender.hasPermission("notematic.admin") || sender.isOp() || pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                        if (pb.seek(seekValueStr)) {
                            seekedAny = true;
                        }
                    }
                }
            }
            if (seekedAny) {
                sender.sendMessage("§aSeeked playbacks of song '" + song.getName() + "' to " + seekValueStr + ".");
            } else {
                sender.sendMessage("§cNo active playbacks of song '" + song.getName() + "' found that you can seek.");
            }
            return;
        }

        // 3. Resolve Player target
        Player targetPlayer = Bukkit.getPlayer(targetStr);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(targetPlayer.getName())) {
                sender.sendMessage("§cYou do not have permission to seek playbacks for other players.");
                return;
            }
            boolean seekedAny = false;
            for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                if (pb.getLocation() == null && pb.getListenerUuid().equals(targetPlayer.getUniqueId())) {
                    if (pb.seek(seekValueStr)) {
                        seekedAny = true;
                    }
                }
            }
            if (seekedAny) {
                sender.sendMessage("§aSeeked playbacks for player " + targetPlayer.getName() + " to " + seekValueStr + ".");
            } else {
                sender.sendMessage("§cNo active playbacks found for player " + targetPlayer.getName() + ".");
            }
            return;
        }

        sender.sendMessage("§cCould not resolve seek target '" + targetStr + "'. (Check ID #, song name, or player name).");
    }

    private List<String> getVisibleSongNames(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("notematic.admin") || sender.isOp();
        List<String> visible = new ArrayList<>();
        for (String name : songManager.getSongNames()) {
            Song song = songManager.getSong(name);
            if (song != null && (!song.isPrivate() || isAdmin)) {
                visible.add(song.getName());
            }
        }
        return visible;
    }

    private void handleList(CommandSender sender) {
        List<String> songNames = getVisibleSongNames(sender);
        if (songNames.isEmpty()) {
            sender.sendMessage("§cNo songs currently loaded. Place them in plugins/NotematicPlayer/songs/.");
            return;
        }
        Collections.sort(songNames);
        sender.sendMessage("§6§lAvailable Songs (" + songNames.size() + "):");
        sender.sendMessage("§f" + String.join(", ", songNames));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to reload the plugin.");
            return;
        }
        playbackManager.stopAll();
        songManager.loadSongs();
        sender.sendMessage("§aNotematic Player: Reloaded songs folder.");
    }

    private void handleVolume(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /notematic volume <value> (to adjust your personal volume)");
            sender.sendMessage("§cOr: /notematic volume #ID <value> (to adjust specific playback volume)");
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                sender.sendMessage("§cOr: /notematic volume song <name> <value>");
            }
            return;
        }

        // Check for specific playback ID volume adjustment
        if (args[1].startsWith("#")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /notematic volume #ID <value>");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(args[1].substring(1));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid playback ID format. Use e.g. #3");
                return;
            }
            SongPlayback pb = playbackManager.getPlaybackById(id);
            if (pb == null) {
                sender.sendMessage("§cPlayback with ID #" + id + " is not currently active.");
                return;
            }
            if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                sender.sendMessage("§cYou do not have permission to adjust the volume for this playback.");
                return;
            }
            double val = parseVolumeValue(sender, args[2]);
            if (val < 0.0) return;
            
            if (val > 1.0) {
                val = 1.0;
                sender.sendMessage("§6[Warning] Volume cannot exceed 100%. Clamping to 100%.");
            }
            
            pb.setVolumeMultiplier(val);
            sender.sendMessage("§aSet volume for playback ID #" + id + " (" + pb.getSong().getName() + ") to " + String.format("%.0f", val * 100) + "%.");
            return;
        }

        // Check for admin sub-syntaxes
        if (args[1].equalsIgnoreCase("song")) {
            if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                sender.sendMessage("§cYou do not have permission to run this command.");
                return;
            }
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /notematic volume song <name> <value>");
                return;
            }
            String targetSongName = args[2];
            Song song = songManager.getSong(targetSongName);
            if (song == null) {
                sender.sendMessage("§cSong '" + targetSongName + "' not found.");
                return;
            }
            double val = parseVolumeValue(sender, args[3]);
            if (val < 0.0) return;
            if (songManager.setSongVolume(targetSongName, val)) {
                sender.sendMessage("§aSet volume multiplier for song '" + song.getName() + "' to " + String.format("%.0f", val * 100) + "%.");
            } else {
                sender.sendMessage("§cFailed to update volume.");
            }
            return;
        }

        // Personal volume adjustment
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole cannot have a personal volume. Usage: /notematic volume song <name> <value>");
            return;
        }

        double val = parseVolumeValue(sender, args[1]);
        if (val < 0.0) return;

        // Validation & Clamping
        if (val > 1.0) {
            val = 1.0;
            sender.sendMessage("§6[Warning] Volume cannot exceed 100%. Clamping to 100%.");
        }

        plugin.setPlayerVolume(player, val);
        sender.sendMessage("§aYour personal volume is now set to " + String.format("%.0f", val * 100) + "%.");
    }

    private double parseVolumeValue(CommandSender sender, String valStr) {
        double value;
        try {
            if (valStr.endsWith("%")) {
                value = Double.parseDouble(valStr.substring(0, valStr.length() - 1)) / 100.0;
            } else {
                value = Double.parseDouble(valStr);
                if (value > 2.0) {
                    value = value / 100.0;
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid volume value! Use a decimal (e.g. 0.5) or a percentage (e.g. 80%).");
            return -1.0;
        }

        if (value < 0.0) {
            sender.sendMessage("§cVolume multiplier cannot be negative!");
            return -1.0;
        }
        return value;
    }

    private Location parseLocation(CommandSender sender, String xStr, String yStr, String zStr) {
        if (!(sender instanceof org.bukkit.entity.Entity)) {
            if (xStr.contains("~") || yStr.contains("~") || zStr.contains("~")) {
                sender.sendMessage("§cConsole/non-entity cannot use relative coordinates (~).");
                return null;
            }
            try {
                double x = Double.parseDouble(xStr);
                double y = Double.parseDouble(yStr);
                double z = Double.parseDouble(zStr);
                org.bukkit.World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world == null) return null;
                return new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        org.bukkit.entity.Entity entity = (org.bukkit.entity.Entity) sender;
        Location origin = entity.getLocation();
        double x = parseCoordinate(xStr, origin.getX());
        double y = parseCoordinate(yStr, origin.getY());
        double z = parseCoordinate(zStr, origin.getZ());
        
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return null;
        }
        
        return new Location(origin.getWorld(), x, y, z);
    }

    private double parseCoordinate(String str, double baseVal) {
        try {
            if (str.startsWith("~")) {
                if (str.length() == 1) {
                    return baseVal;
                }
                return baseVal + Double.parseDouble(str.substring(1));
            }
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("play");
            subCommands.add("pause");
            subCommands.add("resume");
            subCommands.add("stop");
            subCommands.add("seek");
            subCommands.add("list");
            subCommands.add("active");
            subCommands.add("volume");
            subCommands.add("help");
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                subCommands.add("reload");
                subCommands.add("commands");
            }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        String subCommand = args[0].toLowerCase();
        
        if (subCommand.equals("commands")) {
            if (args.length == 2) {
                List<String> vals = new ArrayList<>();
                vals.add("true");
                vals.add("false");
                StringUtil.copyPartialMatches(args[1], vals, completions);
                return completions;
            }
        }

        if (subCommand.equals("play")) {
            if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], getVisibleSongNames(sender), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                suggestions.add("at");
                suggestions.add("loop");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length >= 4) {
                if (args[2].equalsIgnoreCase("at")) {
                    int index = args.length - 1;
                    if (index == 3 || index == 4 || index == 5) {
                        completions.add("~");
                        if (sender instanceof Player player) {
                            Location loc = player.getLocation();
                            if (index == 3) completions.add(String.format("%.0f", loc.getX()));
                            if (index == 4) completions.add(String.format("%.0f", loc.getY()));
                            if (index == 5) completions.add(String.format("%.0f", loc.getZ()));
                        }
                        return completions;
                    }
                    if (index == 6) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("10");
                        suggestions.add("20");
                        suggestions.add("32");
                        suggestions.add("loop");
                        StringUtil.copyPartialMatches(args[index], suggestions, completions);
                        return completions;
                    }
                    if (index == 7) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("25%");
                        suggestions.add("50%");
                        suggestions.add("100%");
                        suggestions.add("loop");
                        StringUtil.copyPartialMatches(args[index], suggestions, completions);
                        return completions;
                    }
                    if (index == 8) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("loop");
                        StringUtil.copyPartialMatches(args[index], suggestions, completions);
                        return completions;
                    }
                } else {
                    if (args.length == 4) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("loop");
                        StringUtil.copyPartialMatches(args[3], suggestions, completions);
                        return completions;
                    }
                }
            }
        }

        if (subCommand.equals("stop") || subCommand.equals("pause") || subCommand.equals("resume")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("at");
                for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                    if (sender.hasPermission("notematic.admin") || sender.isOp() || pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                        suggestions.add("#" + pb.getId());
                    }
                }
                suggestions.addAll(getVisibleSongNames(sender));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length >= 3) {
                boolean isAt = args[1].equalsIgnoreCase("at");
                boolean isSongAndAt = args.length >= 4 && args[2].equalsIgnoreCase("at");
                if (isAt || isSongAndAt) {
                    int coordinateIndex = isAt ? (args.length - 1 - 2) : (args.length - 1 - 3);
                    if (coordinateIndex == 0 || coordinateIndex == 1 || coordinateIndex == 2) {
                        completions.add("~");
                        if (sender instanceof Player player) {
                            Location loc = player.getLocation();
                            if (coordinateIndex == 0) completions.add(String.format("%.0f", loc.getX()));
                            if (coordinateIndex == 1) completions.add(String.format("%.0f", loc.getY()));
                            if (coordinateIndex == 2) completions.add(String.format("%.0f", loc.getZ()));
                        }
                        return completions;
                    }
                } else if (args.length == 3) {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("at");
                    StringUtil.copyPartialMatches(args[2], suggestions, completions);
                    return completions;
                }
            }
        }

        if (subCommand.equals("seek")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                    if (sender.hasPermission("notematic.admin") || sender.isOp() || pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                        suggestions.add("#" + pb.getId());
                    }
                }
                suggestions.addAll(getVisibleSongNames(sender));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("+5s");
                suggestions.add("+10s");
                suggestions.add("-5s");
                suggestions.add("-10s");
                suggestions.add("0s");
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
                return completions;
            }
        }

        if (subCommand.equals("volume")) {
            if (args.length == 2) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("25%");
                suggestions.add("50%");
                suggestions.add("75%");
                suggestions.add("100%");
                suggestions.add("0.5");
                suggestions.add("1.0");
                for (SongPlayback pb : playbackManager.getActivePlaybacks()) {
                    if (sender.hasPermission("notematic.admin") || sender.isOp() || pb.getInitiator().equalsIgnoreCase(sender.getName())) {
                        suggestions.add("#" + pb.getId());
                    }
                }
                if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                    suggestions.add("song");
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
                return completions;
            }
            if (args.length == 3) {
                if (args[1].startsWith("#")) {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("25%");
                    suggestions.add("50%");
                    suggestions.add("75%");
                    suggestions.add("100%");
                    suggestions.add("0.5");
                    suggestions.add("1.0");
                    StringUtil.copyPartialMatches(args[2], suggestions, completions);
                    return completions;
                }
                if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                    List<String> suggestions = new ArrayList<>();
                    if (args[1].equalsIgnoreCase("song")) {
                        suggestions.addAll(getVisibleSongNames(sender));
                    }
                    StringUtil.copyPartialMatches(args[2], suggestions, completions);
                    Collections.sort(completions);
                    return completions;
                }
            }
            if (args.length == 4) {
                if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                    if (args[1].equalsIgnoreCase("song")) {
                        List<String> suggestions = new ArrayList<>();
                        suggestions.add("25%");
                        suggestions.add("50%");
                        suggestions.add("75%");
                        suggestions.add("100%");
                        suggestions.add("0.5");
                        suggestions.add("1.0");
                        StringUtil.copyPartialMatches(args[3], suggestions, completions);
                        return completions;
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}

