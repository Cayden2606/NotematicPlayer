package me.cayde26.notematicPlayer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class NotematicPlayer extends JavaPlugin {
    private SongManager songManager;
    private SongPlaybackManager playbackManager;
    private boolean playerCommandsEnabled = true;

    private final Map<UUID, Double> playerVolumes = new HashMap<>();
    private File playerVolumesFile;
    private YamlConfiguration playerVolumesConfig;

    @Override
    public void onEnable() {
        // Load config
        saveDefaultConfig();
        playerCommandsEnabled = getConfig().getBoolean("player-commands-enabled", true);

        // Load player volumes
        loadPlayerVolumes();

        // Initialize managers
        songManager = new SongManager(this);
        playbackManager = new SongPlaybackManager(this);

        // Register command
        NotematicCommand notematicCommand = new NotematicCommand(this, songManager, playbackManager);
        if (getCommand("notematic") != null) {
            getCommand("notematic").setExecutor(notematicCommand);
            getCommand("notematic").setTabCompleter(notematicCommand);
        }

        getLogger().info("Notematic Player plugin enabled successfully!");
    }

    public boolean isPlayerCommandsEnabled() {
        return playerCommandsEnabled;
    }

    public void setPlayerCommandsEnabled(boolean enabled) {
        this.playerCommandsEnabled = enabled;
        getConfig().set("player-commands-enabled", enabled);
        saveConfig();
    }

    private void loadPlayerVolumes() {
        playerVolumesFile = new File(getDataFolder(), "player_volumes.yml");
        if (!playerVolumesFile.exists()) {
            try {
                playerVolumesFile.getParentFile().mkdirs();
                playerVolumesFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not create player_volumes.yml", e);
            }
        }
        playerVolumesConfig = YamlConfiguration.loadConfiguration(playerVolumesFile);
        if (playerVolumesConfig.contains("volumes")) {
            for (String key : playerVolumesConfig.getConfigurationSection("volumes").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double vol = playerVolumesConfig.getDouble("volumes." + key, 1.0);
                    playerVolumes.put(uuid, vol);
                } catch (IllegalArgumentException e) {
                    // Ignore invalid UUID
                }
            }
        }
    }

    public void savePlayerVolumes() {
        if (playerVolumesConfig == null || playerVolumesFile == null) return;
        playerVolumesConfig.set("volumes", null); // Clear old entries
        for (Map.Entry<UUID, Double> entry : playerVolumes.entrySet()) {
            playerVolumesConfig.set("volumes." + entry.getKey().toString(), entry.getValue());
        }
        try {
            playerVolumesConfig.save(playerVolumesFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not save player_volumes.yml", e);
        }
    }

    /**
     * Get the personal volume multiplier for a player.
     *
     * @param player The target player
     * @return The volume multiplier (default 1.0)
     */
    public double getPlayerVolume(Player player) {
        if (player == null) return 1.0;
        return playerVolumes.getOrDefault(player.getUniqueId(), 1.0);
    }

    /**
     * Set the personal volume multiplier for a player.
     *
     * @param player The target player
     * @param volumeMultiplier The volume multiplier (e.g. 0.5 for 50%)
     */
    public void setPlayerVolume(Player player, double volumeMultiplier) {
        if (player == null) return;
        playerVolumes.put(player.getUniqueId(), volumeMultiplier);
        savePlayerVolumes();
    }

    @Override
    public void onDisable() {
        // Stop active playbacks
        if (playbackManager != null) {
            playbackManager.stopAll();
            playbackManager.stopTickTask();
        }
        getLogger().info("Notematic Player plugin disabled.");
    }

    // ==========================================
    //              PUBLIC API METHODS
    // ==========================================

    /**
     * Play a song for a specific player.
     *
     * @param player The target player
     * @param songName The name of the song to play (case-insensitive)
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSong(Player player, String songName) {
        return playSong(player, songName, true, false, "API");
    }

    /**
     * Play a song for a specific player with a chat message toggle.
     *
     * @param player The target player
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSong(Player player, String songName, boolean showChatMessage) {
        return playSong(player, songName, showChatMessage, false, "API");
    }

    /**
     * Play a song for a specific player with chat message and positional toggles.
     *
     * @param player The target player
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @param positional whether to play the sound in the world at the player's location
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSong(Player player, String songName, boolean showChatMessage, boolean positional) {
        return playSong(player, songName, showChatMessage, positional, "API");
    }

    /**
     * Play a song for a specific player with chat message, positional toggles, and initiator tracking.
     *
     * @param player The target player
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @param positional whether to play the sound in the world at the player's location
     * @param initiator the name of the user or system that started the playback
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSong(Player player, String songName, boolean showChatMessage, boolean positional, String initiator) {
        if (songManager == null || playbackManager == null || player == null || songName == null) {
            return false;
        }
        Song song = songManager.getSong(songName);
        if (song == null) {
            return false;
        }
        playbackManager.startPlayback(player, song, showChatMessage, positional, initiator);
        return true;
    }

    /**
     * Stop all playing songs for a specific player.
     *
     * @param player The target player
     */
    public void stopSong(Player player) {
        if (playbackManager != null && player != null) {
            playbackManager.stopPlayback(player);
        }
    }

    /**
     * Stop a specific playing song for a specific player.
     *
     * @param player The target player
     * @param songName The name of the song to stop (case-insensitive)
     */
    public void stopSong(Player player, String songName) {
        if (playbackManager != null && player != null && songManager != null && songName != null) {
            Song song = songManager.getSong(songName);
            if (song != null) {
                playbackManager.stopPlayback(player, song);
            }
        }
    }

    /**
     * Play a song for all online players.
     *
     * @param songName The name of the song to play (case-insensitive)
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSongForAll(String songName) {
        return playSongForAll(songName, true, false, "API");
    }

    /**
     * Play a song for all online players with a chat message toggle.
     *
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSongForAll(String songName, boolean showChatMessage) {
        return playSongForAll(songName, showChatMessage, false, "API");
    }

    /**
     * Play a song for all online players with chat message and positional toggles.
     *
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @param positional whether to play the sound in the world at the players' locations
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSongForAll(String songName, boolean showChatMessage, boolean positional) {
        return playSongForAll(songName, showChatMessage, positional, "API");
    }

    /**
     * Play a song for all online players with chat message, positional toggles, and initiator tracking.
     *
     * @param songName The name of the song to play (case-insensitive)
     * @param showChatMessage whether to show play/stop/finish messages in chat
     * @param positional whether to play the sound in the world at the players' locations
     * @param initiator the name of the user or system that started the playback
     * @return true if the song was found and playback started, false otherwise
     */
    public boolean playSongForAll(String songName, boolean showChatMessage, boolean positional, String initiator) {
        if (songManager == null || playbackManager == null || songName == null) {
            return false;
        }
        Song song = songManager.getSong(songName);
        if (song == null) {
            return false;
        }
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            playbackManager.startPlayback(player, song, showChatMessage, positional, initiator);
        }
        return true;
    }

    /**
     * Get a list of all currently active song playbacks.
     *
     * @return List of active SongPlayback objects
     */
    public List<SongPlayback> getActivePlaybacks() {
        return playbackManager != null ? playbackManager.getActivePlaybacks() : new ArrayList<>();
    }

    /**
     * Stop all active song playbacks on the server.
     */
    public void stopAllSongs() {
        if (playbackManager != null) {
            playbackManager.stopAll();
        }
    }

    /**
     * Check if a player is currently listening to a song.
     *
     * @param player The player to check
     * @return true if listening, false otherwise
     */
    public boolean isPlaying(Player player) {
        return playbackManager != null && player != null && playbackManager.isPlaying(player);
    }

    /**
     * Check if a song exists in the loaded library.
     *
     * @param songName The name of the song (case-insensitive)
     * @return true if it exists, false otherwise
     */
    public boolean songExists(String songName) {
        return songManager != null && songName != null && songManager.getSong(songName) != null;
    }

    /**
     * Pause all active songs for a player.
     *
     * @param player The target player
     */
    public void pauseSong(Player player) {
        if (playbackManager != null && player != null) {
            playbackManager.pausePlayback(player);
        }
    }

    /**
     * Pause a specific playing song for a player.
     *
     * @param player The target player
     * @param songName The name of the song to pause (case-insensitive)
     */
    public void pauseSong(Player player, String songName) {
        if (playbackManager != null && player != null && songManager != null && songName != null) {
            Song song = songManager.getSong(songName);
            if (song != null) {
                playbackManager.pausePlayback(player, song);
            }
        }
    }

    /**
     * Resume all paused songs for a player.
     *
     * @param player The target player
     */
    public void resumeSong(Player player) {
        if (playbackManager != null && player != null) {
            playbackManager.resumePlayback(player);
        }
    }

    /**
     * Resume a specific paused song for a player.
     *
     * @param player The target player
     * @param songName The name of the song to resume (case-insensitive)
     */
    public void resumeSong(Player player, String songName) {
        if (playbackManager != null && player != null && songManager != null && songName != null) {
            Song song = songManager.getSong(songName);
            if (song != null) {
                playbackManager.resumePlayback(player, song);
            }
        }
    }

    /**
     * Pause all active playbacks on the server.
     */
    public void pauseAllSongs() {
        if (playbackManager != null) {
            playbackManager.pauseAll();
        }
    }

    /**
     * Resume all active playbacks on the server.
     */
    public void resumeAllSongs() {
        if (playbackManager != null) {
            playbackManager.resumeAll();
        }
    }

    /**
     * Check if a player's song is currently paused.
     *
     * @param player The target player
     * @return true if paused, false otherwise
     */
    public boolean isPaused(Player player) {
        return playbackManager != null && player != null && playbackManager.isPaused(player);
    }

    /**
     * Seek all active songs for a player.
     *
     * @param player The target player
     * @param seekValueStr The seek value string (e.g. "+10s", "-5t", "100")
     * @return true if any song was successfully seeked, false otherwise
     */
    public boolean seekSong(Player player, String seekValueStr) {
        if (playbackManager != null && player != null) {
            return playbackManager.seekPlayback(player, seekValueStr);
        }
        return false;
    }

    /**
     * Seek a specific playing song for a player.
     *
     * @param player The target player
     * @param songName The name of the song to seek (case-insensitive)
     * @param seekValueStr The seek value string (e.g. "+10s", "-5t", "100")
     * @return true if the song was found and seeked, false otherwise
     */
    public boolean seekSong(Player player, String songName, String seekValueStr) {
        if (playbackManager != null && player != null && songManager != null && songName != null) {
            Song song = songManager.getSong(songName);
            if (song != null) {
                return playbackManager.seekPlayback(player, song, seekValueStr);
            }
        }
        return false;
    }

    /**
     * Set the global volume multiplier for a song.
     *
     * @param songName The name of the song
     * @param multiplier The volume multiplier (e.g. 0.5 for 50%)
     * @return true if song exists and volume was updated, false otherwise
     */
    public boolean setSongVolume(String songName, double multiplier) {
        return songManager != null && songManager.setSongVolume(songName, multiplier);
    }

    /**
     * Get the global volume multiplier for a song.
     *
     * @param songName The name of the song
     * @return The volume multiplier, or 1.0 if not set or song not found
     */
    public double getSongVolume(String songName) {
        if (songManager == null) return 1.0;
        Song song = songManager.getSong(songName);
        return song != null ? song.getVolumeMultiplier() : 1.0;
    }
}

