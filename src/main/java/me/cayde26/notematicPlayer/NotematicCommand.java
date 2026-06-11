package me.cayde26.notematicPlayer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
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
            case "info":
                handleInfo(sender);
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
            case "toggle":
                handleToggle(sender);
                break;
            default:
                sender.sendMessage("§cUnknown subcommand. Use §f/notematic§c for help.");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l--- Notematic Player Commands ---");
        sender.sendMessage("§e/notematic play <song> [player/@a/*] [showChatMessage] [positional] §7- Play a song");
        sender.sendMessage("§e/notematic pause [player/@a/*]      §7- Pause playing music");
        sender.sendMessage("§e/notematic resume [player/@a/*]     §7- Resume playing music");
        sender.sendMessage("§e/notematic stop [player/@a/*]       §7- Stop playing music");
        sender.sendMessage("§e/notematic volume <value>           §7- Adjust your personal volume (0-100%)");
        sender.sendMessage("§e/notematic list                    §7- List all loaded songs");
        sender.sendMessage("§e/notematic active                  §7- Show all currently playing songs");
        sender.sendMessage("§e/notematic info                    §7- Show plugin information & author");
        sender.sendMessage("§e/notematic help                    §7- Show this help menu");
        if (sender.hasPermission("notematic.admin") || sender.isOp()) {
            sender.sendMessage("§e/notematic volume <song/player> <val> §7- Adjust song or player volume");
            sender.sendMessage("§e/notematic toggle                  §7- Enable/disable commands for players");
            sender.sendMessage("§e/notematic reload                  §7- Reload songs from files");
        }
        sender.sendMessage("§6§l---------------------------------");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6§l--- Notematic Player Info ---");
        sender.sendMessage("§ePlugin Name: §fNotematicPlayer");
        sender.sendMessage("§eVersion: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eAuthor: §fCayden26");
        sender.sendMessage("§ePowered by: §fNotematic Studio (Pre-release)");
        sender.sendMessage("§eDescription: §fPlays custom note block music sequences loaded from JSON or mcfunction.");
        sender.sendMessage("§6§l----------------------------");
    }

    private void handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /notematic play <song> [player/@a/*] [showChatMessage] [positional]");
            return;
        }

        String songName = args[1];
        Song song = songManager.getSong(songName);
        if (song == null) {
            sender.sendMessage("§cSong '" + songName + "' not found! Use §f/notematic list§c to see available songs.");
            return;
        }

        boolean showChatMessage = true;
        boolean positional = false;
        String target = null;

        if (args.length >= 5) {
            target = args[2];
            showChatMessage = parseBoolean(args[3], true);
            positional = parseBoolean(args[4], false);
        } else if (args.length == 4) {
            String arg2 = args[2];
            String arg3 = args[3];
            if (arg2.equalsIgnoreCase("true") || arg2.equalsIgnoreCase("false")) {
                showChatMessage = Boolean.parseBoolean(arg2);
                positional = parseBoolean(arg3, false);
            } else {
                target = arg2;
                showChatMessage = parseBoolean(arg3, true);
            }
        } else if (args.length == 3) {
            String arg2 = args[2];
            if (arg2.equalsIgnoreCase("true") || arg2.equalsIgnoreCase("false")) {
                showChatMessage = Boolean.parseBoolean(arg2);
            } else {
                target = arg2;
            }
        }

        String initiatorName = sender.getName();

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to play songs for all players.");
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playbackManager.startPlayback(player, song, showChatMessage, positional, initiatorName);
                }
                if (showChatMessage) {
                    sender.sendMessage("§aStarted playing '" + song.getName() + "' for all online players.");
                }
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
                playbackManager.startPlayback(targetPlayer, song, showChatMessage, positional, initiatorName);
                if (showChatMessage) {
                    sender.sendMessage("§aStarted playing '" + song.getName() + "' for player " + targetPlayer.getName() + ".");
                }
            }
        } else {
            // Play for sender
            if (sender instanceof Player) {
                playbackManager.startPlayback((Player) sender, song, showChatMessage, positional, initiatorName);
            } else {
                sender.sendMessage("§cPlease specify a target player when running from console: /notematic play <song> <player/@a/*> [showChatMessage] [positional]");
            }
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return defaultValue;
    }

    private boolean isNormalPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return !player.isOp() && !player.hasPermission("notematic.admin");
        }
        return false;
    }

    private void handleToggle(CommandSender sender) {
        if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to run this command.");
            return;
        }

        boolean current = plugin.isPlayerCommandsEnabled();
        plugin.setPlayerCommandsEnabled(!current);
        if (current) {
            sender.sendMessage("§aDisabled commands for normal players. Only OPs/admins can use Notematic Player commands now.");
        } else {
            sender.sendMessage("§aEnabled commands for normal players.");
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
            UUID listenerUuid = playback.getListenerUuid();
            Player listener = listenerUuid != null ? Bukkit.getPlayer(listenerUuid) : null;
            String listenerName = listener != null ? listener.getName() : "Unknown (" + listenerUuid + ")";
            
            Song song = playback.getSong();
            double progressPercent = 0.0;
            if (song.getMaxTick() > 0) {
                progressPercent = (playback.getCurrentVirtualTick() / song.getMaxTick()) * 100.0;
                if (progressPercent > 100.0) progressPercent = 100.0;
            }
            
            sender.sendMessage(String.format("§eSong: §f%s §7| §eTarget: §f%s §7| §eInitiator: §f%s §7| §eProgress: §f%.1f%% §7| §ePaused: §f%s §7| §ePositional: §f%s",
                song.getName(),
                listenerName,
                playback.getInitiator(),
                progressPercent,
                playback.isPaused() ? "Yes" : "No",
                playback.isPositional() ? "Yes" : "No"
            ));
        }
        sender.sendMessage("§6§l-----------------------------------");
    }

    private void handlePause(CommandSender sender, String[] args) {
        Song specificSong = null;
        String target = null;
        
        if (args.length >= 2) {
            target = args[1];
            if (args.length >= 3) {
                String songName = args[2];
                specificSong = songManager.getSong(songName);
                if (specificSong == null) {
                    sender.sendMessage("§cSong '" + songName + "' not found!");
                    return;
                }
            }
        }

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to pause songs for all players.");
                    return;
                }
                if (specificSong != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playbackManager.pausePlayback(player, specificSong);
                    }
                    sender.sendMessage("§aPaused '" + specificSong.getName() + "' for all online players.");
                } else {
                    playbackManager.pauseAll();
                    sender.sendMessage("§aPaused playback for all online players.");
                }
            } else {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(target)) {
                    sender.sendMessage("§cYou do not have permission to pause songs for other players.");
                    return;
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage("§cPlayer '" + target + "' is not online.");
                    return;
                }
                if (specificSong != null) {
                    playbackManager.pausePlayback(targetPlayer, specificSong);
                } else {
                    playbackManager.pausePlayback(targetPlayer);
                }
            }
        } else {
            if (sender instanceof Player) {
                playbackManager.pausePlayback((Player) sender);
            } else {
                playbackManager.pauseAll();
                sender.sendMessage("§aPaused all active playbacks.");
            }
        }
    }

    private void handleResume(CommandSender sender, String[] args) {
        Song specificSong = null;
        String target = null;
        
        if (args.length >= 2) {
            target = args[1];
            if (args.length >= 3) {
                String songName = args[2];
                specificSong = songManager.getSong(songName);
                if (specificSong == null) {
                    sender.sendMessage("§cSong '" + songName + "' not found!");
                    return;
                }
            }
        }

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to resume songs for all players.");
                    return;
                }
                if (specificSong != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playbackManager.resumePlayback(player, specificSong);
                    }
                    sender.sendMessage("§aResumed '" + specificSong.getName() + "' for all online players.");
                } else {
                    playbackManager.resumeAll();
                    sender.sendMessage("§aResumed playback for all online players.");
                }
            } else {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(target)) {
                    sender.sendMessage("§cYou do not have permission to resume songs for other players.");
                    return;
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage("§cPlayer '" + target + "' is not online.");
                    return;
                }
                if (specificSong != null) {
                    playbackManager.resumePlayback(targetPlayer, specificSong);
                } else {
                    playbackManager.resumePlayback(targetPlayer);
                }
            }
        } else {
            if (sender instanceof Player) {
                playbackManager.resumePlayback((Player) sender);
            } else {
                playbackManager.resumeAll();
                sender.sendMessage("§aResumed all active playbacks.");
            }
        }
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String target = args[1];
            Song specificSong = null;
            if (args.length >= 3) {
                String songName = args[2];
                specificSong = songManager.getSong(songName);
                if (specificSong == null) {
                    sender.sendMessage("§cSong '" + songName + "' not found!");
                    return;
                }
            }

            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to stop songs for all players.");
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (specificSong != null) {
                        playbackManager.stopPlayback(player, specificSong);
                    } else {
                        playbackManager.stopPlayback(player);
                    }
                }
                if (specificSong != null) {
                    sender.sendMessage("§aStopped playing '" + specificSong.getName() + "' for all online players.");
                } else {
                    sender.sendMessage("§aStopped playback for all online players.");
                }
            } else {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(target)) {
                    sender.sendMessage("§cYou do not have permission to stop songs for other players.");
                    return;
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage("§cPlayer '" + target + "' is not online.");
                    return;
                }
                if (specificSong != null) {
                    playbackManager.stopPlayback(targetPlayer, specificSong);
                    sender.sendMessage("§aStopped playing '" + specificSong.getName() + "' for player " + targetPlayer.getName() + ".");
                } else {
                    playbackManager.stopPlayback(targetPlayer);
                    sender.sendMessage("§aStopped playback for player " + targetPlayer.getName() + ".");
                }
            }
        } else {
            // Stop for sender or all (if console)
            if (sender instanceof Player) {
                playbackManager.stopPlayback((Player) sender);
            } else {
                playbackManager.stopAll();
                sender.sendMessage("§aStopped all active playbacks.");
            }
        }
    }

    private void handleList(CommandSender sender) {
        List<String> songNames = new ArrayList<>(songManager.getSongNames());
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
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                sender.sendMessage("§cOr: /notematic volume <song/player> <value> (to adjust song or player volume)");
            }
            return;
        }

        if (args.length == 2) {
            // Personal volume adjustment for the sender
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole cannot have a personal volume. Usage: /notematic volume <song/player> <value>");
                return;
            }

            double value = parseVolumeValue(sender, args[1]);
            if (value < 0.0) return;

            plugin.setPlayerVolume(player, value);
            sender.sendMessage("§aSet your personal volume to " + String.format("%.0f", value * 100) + "%.");
            return;
        }

        // Adjusting someone else's volume or a song's volume (requires admin)
        if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to adjust other volumes.");
            return;
        }

        String target = args[1];
        double value = parseVolumeValue(sender, args[2]);
        if (value < 0.0) return;

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            plugin.setPlayerVolume(targetPlayer, value);
            sender.sendMessage("§aSet personal volume for player " + targetPlayer.getName() + " to " + String.format("%.0f", value * 100) + "%.");
            return;
        }

        Song song = songManager.getSong(target);
        if (song != null) {
            if (songManager.setSongVolume(target, value)) {
                sender.sendMessage("§aSet volume multiplier for '" + song.getName() + "' to " + String.format("%.0f", value * 100) + "%.");
            } else {
                sender.sendMessage("§cFailed to update volume.");
            }
            return;
        }

        sender.sendMessage("§cTarget '" + target + "' is neither an online player nor a loaded song.");
    }

    private double parseVolumeValue(CommandSender sender, String valStr) {
        double value;
        try {
            if (valStr.endsWith("%")) {
                value = Double.parseDouble(valStr.substring(0, valStr.length() - 1)) / 100.0;
            } else {
                value = Double.parseDouble(valStr);
                // If they enter e.g. 50 (which is > 2.0), we assume they mean 50% and divide by 100
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("play");
            subCommands.add("pause");
            subCommands.add("resume");
            subCommands.add("stop");
            subCommands.add("list");
            subCommands.add("active");
            subCommands.add("volume");
            subCommands.add("help");
            subCommands.add("info");
            if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                subCommands.add("reload");
                subCommands.add("toggle");
            }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("play")) {
                StringUtil.copyPartialMatches(args[1], songManager.getSongNames(), completions);
                Collections.sort(completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("volume")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("25%");
                suggestions.add("50%");
                suggestions.add("75%");
                suggestions.add("100%");
                suggestions.add("0.5");
                suggestions.add("1.0");
                if (sender.hasPermission("notematic.admin") || sender.isOp()) {
                    suggestions.addAll(songManager.getSongNames());
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        suggestions.add(player.getName());
                    }
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
                Collections.sort(completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("pause") || args[0].equalsIgnoreCase("resume")) {
                List<String> targets = new ArrayList<>();
                targets.add("@a");
                targets.add("*");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    targets.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], targets, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("play")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("@a");
                suggestions.add("*");
                suggestions.add("true");
                suggestions.add("false");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
                Collections.sort(completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("volume")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("25%");
                suggestions.add("50%");
                suggestions.add("75%");
                suggestions.add("100%");
                suggestions.add("150%");
                suggestions.add("0.5");
                suggestions.add("1.0");
                suggestions.add("1.5");
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
                return completions;
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("play")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("true");
                suggestions.add("false");
                StringUtil.copyPartialMatches(args[3], suggestions, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("play")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("true");
                suggestions.add("false");
                StringUtil.copyPartialMatches(args[4], suggestions, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        return Collections.emptyList();
    }
}

