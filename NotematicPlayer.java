import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

class Song {
    private final String name;
    private final double tempo;
    private final List<SongNote> notes;
    private final int maxTick;
    private double volumeMultiplier = 1.0;
    private boolean isPrivate = false;

    public Song(String name, double tempo, List<SongNote> notes) {
        this.name = name;
        this.tempo = tempo;
        this.notes = notes;
        
        int calculatedMaxTick = 0;
        for (SongNote note : notes) {
            if (note.getWhen() > calculatedMaxTick) {
                calculatedMaxTick = note.getWhen();
            }
        }
        this.maxTick = calculatedMaxTick;
    }

    public String getName() {
        return name;
    }

    public double getTempo() {
        return tempo;
    }

    public List<SongNote> getNotes() {
        return notes;
    }

    public int getMaxTick() {
        return maxTick;
    }

    public double getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = volumeMultiplier;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}

class SongNote {
    private final String instrument;
    private final double note; // pitch multiplier
    private final double volume;
    private final int when; // tick offset

    public SongNote(String instrument, double note, double volume, int when) {
        this.instrument = instrument;
        this.note = note;
        this.volume = volume;
        this.when = when;
    }

    public String getInstrument() {
        return instrument;
    }

    public double getNote() {
        return note;
    }

    public double getVolume() {
        return volume;
    }

    public int getWhen() {
        return when;
    }
}

class SongParser {
    private static final Gson gson = new Gson();

    public static Song parseSong(File file) throws IOException {
        String fileName = file.getName();
        if (!fileName.contains(".")) {
            throw new IOException("File has no extension: " + fileName);
        }
        String songName = fileName.substring(0, fileName.lastIndexOf('.'));
        
        String content = readFileContent(file);
        
        if (fileName.endsWith(".mcfunction")) {
            content = extractJsonFromMcFunction(content);
            if (content == null) {
                throw new IOException("Failed to extract JSON song data from .mcfunction: " + file.getName());
            }
        }
        
        return parseFromJson(songName, content);
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String extractJsonFromMcFunction(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) continue;
            
            // Check for storage command or storage sheet set value
            if (line.contains("data modify storage") || line.contains("sheet set value")) {
                int openBraceIdx = line.indexOf('{');
                int closeBraceIdx = line.lastIndexOf('}');
                if (openBraceIdx != -1 && closeBraceIdx != -1 && closeBraceIdx > openBraceIdx) {
                    return line.substring(openBraceIdx, closeBraceIdx + 1);
                }
            }
        }
        return null;
    }

    private static Song parseFromJson(String name, String jsonStr) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            double tempo = 1.0;
            if (root.has("tempo")) {
                tempo = root.get("tempo").getAsDouble();
            }
            
            List<SongNote> notes = new ArrayList<>();
            if (root.has("notes")) {
                JsonArray notesArray = root.getAsJsonArray("notes");
                for (JsonElement noteElem : notesArray) {
                    JsonObject noteObj = noteElem.getAsJsonObject();
                    String instrument = noteObj.has("instrument") ? noteObj.get("instrument").getAsString() : "harp";
                    double note = noteObj.has("note") ? noteObj.get("note").getAsDouble() : 1.0;
                    double volume = noteObj.has("volume") ? noteObj.get("volume").getAsDouble() : 1.0;
                    int when = noteObj.has("when") ? noteObj.get("when").getAsInt() : 0;
                    
                    notes.add(new SongNote(instrument, note, volume, when));
                }
            }
            
            // Ensure notes are sorted by their trigger time
            notes.sort(Comparator.comparingInt(SongNote::getWhen));
            
            return new Song(name, tempo, notes);
        } catch (Exception e) {
            throw new IOException("Failed to parse song JSON structure: " + e.getMessage(), e);
        }
    }
}

class SongPlayback {
    private final Song song;
    private double currentVirtualTick;
    private int nextNoteIndex;
    private final UUID listenerUuid; // null if global playback
    private final boolean global;
    private boolean paused;
    private final boolean showChatMessage;
    private final boolean positional;
    private final String initiator;

    public SongPlayback(Song song, UUID listenerUuid) {
        this(song, listenerUuid, true, false, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage) {
        this(song, listenerUuid, showChatMessage, false, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional) {
        this(song, listenerUuid, showChatMessage, positional, "API");
    }

    public SongPlayback(Song song, UUID listenerUuid, boolean showChatMessage, boolean positional, String initiator) {
        this.song = song;
        this.listenerUuid = listenerUuid;
        this.global = (listenerUuid == null);
        this.paused = false;
        this.showChatMessage = showChatMessage;
        this.positional = positional;
        this.initiator = initiator != null ? initiator : "API";
        
        // Start at first note's "when" tick to avoid leading silence
        if (!song.getNotes().isEmpty()) {
            this.currentVirtualTick = song.getNotes().get(0).getWhen();
        } else {
            this.currentVirtualTick = 0;
        }
        this.nextNoteIndex = 0;
    }

    public String getInitiator() {
        return initiator;
    }

    public boolean isShowChatMessage() {
        return showChatMessage;
    }

    public boolean isPositional() {
        return positional;
    }

    public Song getSong() {
        return song;
    }

    public double getCurrentVirtualTick() {
        return currentVirtualTick;
    }

    public void incrementTick() {
        this.currentVirtualTick += song.getTempo();
    }

    public int getNextNoteIndex() {
        return nextNoteIndex;
    }

    public void setNextNoteIndex(int nextNoteIndex) {
        this.nextNoteIndex = nextNoteIndex;
    }

    public UUID getListenerUuid() {
        return listenerUuid;
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Seeks the playback based on a seek value string (e.g. "+10s", "-5t", "100t", "5s").
     *
     * @param seekValueStr The seek value string
     * @return true if seek was successful, false if invalid format
     */
    public boolean seek(String seekValueStr) {
        if (seekValueStr == null || seekValueStr.isEmpty()) {
            return false;
        }

        boolean relative = false;
        boolean negative = false;
        String numberPart = seekValueStr;

        if (seekValueStr.startsWith("+")) {
            relative = true;
            numberPart = seekValueStr.substring(1);
        } else if (seekValueStr.startsWith("-")) {
            relative = true;
            negative = true;
            numberPart = seekValueStr.substring(1);
        }

        boolean isSeconds = false;
        if (numberPart.endsWith("s") || numberPart.endsWith("sec") || numberPart.endsWith("seconds")) {
            isSeconds = true;
            if (numberPart.endsWith("seconds")) {
                numberPart = numberPart.substring(0, numberPart.length() - 7);
            } else if (numberPart.endsWith("sec")) {
                numberPart = numberPart.substring(0, numberPart.length() - 3);
            } else {
                numberPart = numberPart.substring(0, numberPart.length() - 1);
            }
        } else if (numberPart.endsWith("t") || numberPart.endsWith("tick") || numberPart.endsWith("ticks")) {
            if (numberPart.endsWith("ticks")) {
                numberPart = numberPart.substring(0, numberPart.length() - 5);
            } else if (numberPart.endsWith("tick")) {
                numberPart = numberPart.substring(0, numberPart.length() - 4);
            } else {
                numberPart = numberPart.substring(0, numberPart.length() - 1);
            }
        }

        double numericValue;
        try {
            numericValue = Double.parseDouble(numberPart);
        } catch (NumberFormatException e) {
            return false;
        }

        double virtualTicksChange;
        if (isSeconds) {
            // 20 game ticks per second, each game tick is song.getTempo() virtual ticks
            virtualTicksChange = numericValue * 20.0 * song.getTempo();
        } else {
            virtualTicksChange = numericValue;
        }

        double targetTick;
        if (relative) {
            if (negative) {
                targetTick = this.currentVirtualTick - virtualTicksChange;
            } else {
                targetTick = this.currentVirtualTick + virtualTicksChange;
            }
        } else {
            targetTick = virtualTicksChange;
        }

        seekToTick(targetTick);
        return true;
    }

    public void seekToTick(double targetTick) {
        double maxTick = song.getMaxTick();
        if (targetTick < 0) {
            targetTick = 0;
        } else if (targetTick > maxTick) {
            targetTick = maxTick;
        }
        this.currentVirtualTick = targetTick;

        // Recalculate nextNoteIndex
        java.util.List<SongNote> notes = song.getNotes();
        int index = 0;
        while (index < notes.size()) {
            if (notes.get(index).getWhen() >= targetTick) {
                break;
            }
            index++;
        }
        this.nextNoteIndex = index;
    }
}

class SongPlaybackManager {
    private final JavaPlugin plugin;
    private final List<SongPlayback> playbacks = new ArrayList<>();
    private BukkitTask tickTask;

    public SongPlaybackManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        this.tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickPlaybacks();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickPlaybacks() {
        if (playbacks.isEmpty()) return;

        Iterator<SongPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();

            if (playback.isPaused()) {
                continue;
            }

            UUID playerUuid = playback.getListenerUuid();
            Player player = Bukkit.getPlayer(playerUuid);
            boolean online = (player != null && player.isOnline());

            Song song = playback.getSong();
            List<SongNote> notes = song.getNotes();
            int index = playback.getNextNoteIndex();
            double virtualTick = playback.getCurrentVirtualTick();

            // Play all notes due up to current virtual tick
            while (index < notes.size()) {
                SongNote note = notes.get(index);
                if (note.getWhen() <= virtualTick) {
                    if (online) {
                        playNoteToPlayer(player, note, song, playback.isPositional());
                    }
                    index++;
                } else {
                    break;
                }
            }

            playback.setNextNoteIndex(index);
            playback.incrementTick();

            // Check if song has finished
            if (index >= notes.size()) {
                if (online && playback.isShowChatMessage()) {
                    player.sendMessage("§aFinished playing song: §f" + song.getName());
                }
                iterator.remove();
            }
        }
    }

    private void playNoteToPlayer(Player player, SongNote note, Song song, boolean positional) {
        if (song.isPrivate()) {
            if (!player.hasPermission("notematic.admin") && !player.isOp()) {
                return;
            }
            positional = false;
        }

        double playerVolMultiplier = 1.0;
        if (plugin instanceof NotematicPlayer) {
            playerVolMultiplier = ((NotematicPlayer) plugin).getPlayerVolume(player);
        }

        float volume = (float) (note.getVolume() * song.getVolumeMultiplier() * playerVolMultiplier);
        float pitch = (float) note.getNote();

        String instrument = note.getInstrument();
        if (instrument != null && (instrument.contains(":") || instrument.contains(".") || instrument.contains("/"))) {
            if (positional) {
                player.getWorld().playSound(player.getLocation(), instrument, SoundCategory.RECORDS, volume, pitch);
            } else {
                player.playSound(player.getLocation(), instrument, SoundCategory.RECORDS, volume, pitch);
            }
        } else {
            Sound sound = getSoundFromInstrument(instrument);
            if (positional) {
                player.getWorld().playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
            } else {
                player.playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
            }
        }
    }

    public void startPlayback(Player player, Song song) {
        startPlayback(player, song, true, false, "API");
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage) {
        startPlayback(player, song, showChatMessage, false, "API");
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage, boolean positional) {
        startPlayback(player, song, showChatMessage, positional, "API");
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage, boolean positional, String initiator) {
        playbacks.add(new SongPlayback(song, player.getUniqueId(), showChatMessage, positional, initiator));
        if (showChatMessage) {
            player.sendMessage("§aNow playing: §f" + song.getName());
        }
    }

    public List<SongPlayback> getActivePlaybacks() {
        return new ArrayList<>(playbacks);
    }

    public void stopPlayback(Player player) {
        Iterator<SongPlayback> iterator = playbacks.iterator();
        boolean stoppedAny = false;
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getListenerUuid().equals(player.getUniqueId())) {
                iterator.remove();
                if (playback.isShowChatMessage()) {
                    player.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                }
                stoppedAny = true;
            }
        }
        if (!stoppedAny) {
            player.sendMessage("§cNo music is currently playing.");
        }
    }

    public void stopPlayback(Player player, Song song) {
        Iterator<SongPlayback> iterator = playbacks.iterator();
        boolean stopped = false;
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getListenerUuid().equals(player.getUniqueId()) && playback.getSong().getName().equalsIgnoreCase(song.getName())) {
                iterator.remove();
                if (playback.isShowChatMessage()) {
                    player.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                }
                stopped = true;
                break;
            }
        }
        if (!stopped) {
            player.sendMessage("§cSong '" + song.getName() + "' is not currently playing for you.");
        }
    }

    public void stopAll() {
        for (SongPlayback playback : playbacks) {
            Player player = Bukkit.getPlayer(playback.getListenerUuid());
            if (player != null && player.isOnline()) {
                player.sendMessage("§cPlayback stopped by server.");
            }
        }
        playbacks.clear();
    }

    public boolean isPlaying(Player player) {
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public void pausePlayback(Player player) {
        boolean pausedAny = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && !playback.isPaused()) {
                playback.setPaused(true);
                pausedAny = true;
            }
        }
        if (pausedAny) {
            player.sendMessage("§ePlayback paused.");
        }
    }

    public void pausePlayback(Player player, Song song) {
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && 
                playback.getSong().getName().equalsIgnoreCase(song.getName()) && 
                !playback.isPaused()) {
                playback.setPaused(true);
                player.sendMessage("§ePlayback paused: §f" + song.getName());
                break;
            }
        }
    }

    public void resumePlayback(Player player) {
        boolean resumedAny = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && playback.isPaused()) {
                playback.setPaused(false);
                resumedAny = true;
            }
        }
        if (resumedAny) {
            player.sendMessage("§aPlayback resumed.");
        }
    }

    public void resumePlayback(Player player, Song song) {
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && 
                playback.getSong().getName().equalsIgnoreCase(song.getName()) && 
                playback.isPaused()) {
                playback.setPaused(false);
                player.sendMessage("§aPlayback resumed: §f" + song.getName());
                break;
            }
        }
    }

    public void pauseAll() {
        for (SongPlayback playback : playbacks) {
            if (!playback.isPaused()) {
                playback.setPaused(true);
                Player player = Bukkit.getPlayer(playback.getListenerUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage("§ePlayback paused by server.");
                }
            }
        }
    }

    public void resumeAll() {
        for (SongPlayback playback : playbacks) {
            if (playback.isPaused()) {
                playback.setPaused(false);
                Player player = Bukkit.getPlayer(playback.getListenerUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage("§aPlayback resumed by server.");
                }
            }
        }
    }

    public boolean seekPlayback(Player player, String seekValueStr) {
        boolean seekedAny = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId())) {
                if (playback.seek(seekValueStr)) {
                    seekedAny = true;
                    if (playback.isShowChatMessage()) {
                        player.sendMessage("§eSeeked '" + playback.getSong().getName() + "' to " + formatPosition(playback));
                    }
                }
            }
        }
        return seekedAny;
    }

    public boolean seekPlayback(Player player, Song song, String seekValueStr) {
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && 
                playback.getSong().getName().equalsIgnoreCase(song.getName())) {
                if (playback.seek(seekValueStr)) {
                    if (playback.isShowChatMessage()) {
                        player.sendMessage("§eSeeked '" + playback.getSong().getName() + "' to " + formatPosition(playback));
                    }
                    return true;
                }
                break;
            }
        }
        return false;
    }

    public void seekAll(String seekValueStr) {
        for (SongPlayback playback : playbacks) {
            if (playback.seek(seekValueStr)) {
                Player player = Bukkit.getPlayer(playback.getListenerUuid());
                if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                    player.sendMessage("§ePlayback seeked by server to " + formatPosition(playback));
                }
            }
        }
    }

    public void seekAll(Song song, String seekValueStr) {
        for (SongPlayback playback : playbacks) {
            if (playback.getSong().getName().equalsIgnoreCase(song.getName())) {
                if (playback.seek(seekValueStr)) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§ePlayback for '" + song.getName() + "' seeked by server to " + formatPosition(playback));
                    }
                }
            }
        }
    }

    private String formatPosition(SongPlayback playback) {
        Song song = playback.getSong();
        double currentTick = playback.getCurrentVirtualTick();
        double tempo = song.getTempo();
        double seconds = currentTick / (20.0 * tempo);
        return String.format("%.1fs (%.0ft)", seconds, currentTick);
    }

    public boolean isPaused(Player player) {
        for (SongPlayback playback : playbacks) {
            if (playback.getListenerUuid().equals(player.getUniqueId()) && playback.isPaused()) {
                return true;
            }
        }
        return false;
    }

    public void stopTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    public static Sound getSoundFromInstrument(String instrument) {
        if (instrument == null) return Sound.BLOCK_NOTE_BLOCK_HARP;

        String name = instrument.toLowerCase().trim();
        switch (name) {
            case "harp":
            case "piano":
                return Sound.BLOCK_NOTE_BLOCK_HARP;
            case "bass":
            case "bassattack":
                return Sound.BLOCK_NOTE_BLOCK_BASS;
            case "basedrum":
            case "bd":
            case "stone":
                return Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case "snare":
            case "cloth":
                return Sound.BLOCK_NOTE_BLOCK_SNARE;
            case "hat":
            case "click":
            case "glass":
                return Sound.BLOCK_NOTE_BLOCK_HAT;
            case "bell":
                return Sound.BLOCK_NOTE_BLOCK_BELL;
            case "flute":
                return Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case "chime":
                return Sound.BLOCK_NOTE_BLOCK_CHIME;
            case "guitar":
                return Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case "xylophone":
                return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case "iron_xylophone":
                return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case "cow_bell":
                return Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case "didgeridoo":
                return Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case "bit":
                return Sound.BLOCK_NOTE_BLOCK_BIT;
            case "banjo":
                return Sound.BLOCK_NOTE_BLOCK_BANJO;
            case "pling":
                return Sound.BLOCK_NOTE_BLOCK_PLING;
            default:
                try {
                    return Sound.valueOf("BLOCK_NOTE_BLOCK_" + name.toUpperCase());
                } catch (IllegalArgumentException e1) {
                    try {
                        return Sound.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e2) {
                        return Sound.BLOCK_NOTE_BLOCK_HARP; // fallback
                    }
                }
        }
    }
}

class SongManager {
    private final JavaPlugin plugin;
    private final File songsFolder;
    private final File volumesFile;
    private final File publicFolder;
    private final File privateFolder;
    private final Map<String, Song> songs = new HashMap<>();

    public SongManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.songsFolder = new File(plugin.getDataFolder(), "songs");
        this.volumesFile = new File(plugin.getDataFolder(), "volumes.yml");
        this.publicFolder = new File(songsFolder, "public");
        this.privateFolder = new File(songsFolder, "private");
        init();
    }

    public void init() {
        if (!songsFolder.exists()) {
            songsFolder.mkdirs();
        }
        if (!publicFolder.exists()) {
            publicFolder.mkdirs();
        }
        if (!privateFolder.exists()) {
            privateFolder.mkdirs();
        }

        loadSongs();
    }

    public void loadSongs() {
        songs.clear();
        
        // Load legacy songs from the root songs folder (treated as public)
        loadSongsFromDirectory(songsFolder, false);
        
        // Load public songs
        loadSongsFromDirectory(publicFolder, false);
        
        // Load private songs
        loadSongsFromDirectory(privateFolder, true);
        
        loadSongVolumes();
        saveSongVolumes(); // Purges deleted songs from configuration
        
        plugin.getLogger().info("Total songs loaded: " + songs.size());
    }

    private void loadSongsFromDirectory(File dir, boolean isPrivate) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && (file.getName().endsWith(".mcfunction") || file.getName().endsWith(".json"))) {
                    try {
                        Song song = SongParser.parseSong(file);
                        song.setPrivate(isPrivate);
                        songs.put(song.getName().toLowerCase(), song);
                        plugin.getLogger().info("Loaded " + (isPrivate ? "private " : "") + "song '" + song.getName() + "' with " + song.getNotes().size() + " notes.");
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load song file " + file.getName(), e);
                    }
                }
            }
        }
    }

    private void loadSongVolumes() {
        if (!volumesFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(volumesFile);
        for (Song song : songs.values()) {
            String path = "volumes." + song.getName().toLowerCase();
            if (config.contains(path)) {
                double mult = config.getDouble(path, 1.0);
                song.setVolumeMultiplier(mult);
            }
        }
    }

    public void saveSongVolumes() {
        YamlConfiguration config = new YamlConfiguration();
        for (Song song : songs.values()) {
            config.set("volumes." + song.getName().toLowerCase(), song.getVolumeMultiplier());
        }
        try {
            config.save(volumesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save volumes.yml", e);
        }
    }

    public boolean setSongVolume(String songName, double multiplier) {
        Song song = getSong(songName);
        if (song == null) return false;
        
        song.setVolumeMultiplier(multiplier);
        saveSongVolumes();
        return true;
    }

    public Song getSong(String name) {
        if (name == null) return null;
        return songs.get(name.toLowerCase());
    }

    public Set<String> getSongNames() {
        return songs.keySet();
    }
}

class NotematicCommand implements CommandExecutor, TabCompleter {
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
            case "seek":
                handleSeek(sender, args);
                break;
            case "stopall":
                handleStopAll(sender, args);
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
        sender.sendMessage("§e/notematic pause [player/song] [song] §7- Pause playing music");
        sender.sendMessage("§e/notematic resume [player/song] [song] §7- Resume playing music");
        sender.sendMessage("§e/notematic stop [player/song] [song]  §7- Stop playing music");
        sender.sendMessage("§e/notematic stopall [player/@a/*]     §7- Stop all playing music");
        sender.sendMessage("§e/notematic seek <[+|-]value[s|t]> [player/song] [song] §7- Seek forward/back or to absolute time");
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
        if (song == null || (song.isPrivate() && !sender.hasPermission("notematic.admin") && !sender.isOp())) {
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
            String arg1 = args[1];
            boolean isPlayerTarget = arg1.equalsIgnoreCase("@a") || arg1.equals("*") || Bukkit.getPlayer(arg1) != null;
            if (isPlayerTarget) {
                target = arg1;
                if (args.length >= 3) {
                    String songName = args[2];
                    specificSong = songManager.getSong(songName);
                    if (specificSong == null) {
                        sender.sendMessage("§cSong '" + songName + "' not found!");
                        return;
                    }
                }
            } else {
                specificSong = songManager.getSong(arg1);
                if (specificSong == null) {
                    sender.sendMessage("§cTarget player not online or song '" + arg1 + "' not found!");
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
                if (specificSong != null) {
                    playbackManager.pausePlayback((Player) sender, specificSong);
                } else {
                    playbackManager.pausePlayback((Player) sender);
                }
            } else {
                if (specificSong != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playbackManager.pausePlayback(player, specificSong);
                    }
                    sender.sendMessage("§aPaused '" + specificSong.getName() + "' for all players.");
                } else {
                    playbackManager.pauseAll();
                    sender.sendMessage("§aPaused all active playbacks.");
                }
            }
        }
    }

    private void handleResume(CommandSender sender, String[] args) {
        Song specificSong = null;
        String target = null;
        
        if (args.length >= 2) {
            String arg1 = args[1];
            boolean isPlayerTarget = arg1.equalsIgnoreCase("@a") || arg1.equals("*") || Bukkit.getPlayer(arg1) != null;
            if (isPlayerTarget) {
                target = arg1;
                if (args.length >= 3) {
                    String songName = args[2];
                    specificSong = songManager.getSong(songName);
                    if (specificSong == null) {
                        sender.sendMessage("§cSong '" + songName + "' not found!");
                        return;
                    }
                }
            } else {
                specificSong = songManager.getSong(arg1);
                if (specificSong == null) {
                    sender.sendMessage("§cTarget player not online or song '" + arg1 + "' not found!");
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
                if (specificSong != null) {
                    playbackManager.resumePlayback((Player) sender, specificSong);
                } else {
                    playbackManager.resumePlayback((Player) sender);
                }
            } else {
                if (specificSong != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playbackManager.resumePlayback(player, specificSong);
                    }
                    sender.sendMessage("§aResumed '" + specificSong.getName() + "' for all players.");
                } else {
                    playbackManager.resumeAll();
                    sender.sendMessage("§aResumed all active playbacks.");
                }
            }
        }
    }

    private void handleStop(CommandSender sender, String[] args) {
        Song specificSong = null;
        String target = null;
        
        if (args.length >= 2) {
            String arg1 = args[1];
            boolean isPlayerTarget = arg1.equalsIgnoreCase("@a") || arg1.equals("*") || Bukkit.getPlayer(arg1) != null;
            if (isPlayerTarget) {
                target = arg1;
                if (args.length >= 3) {
                    String songName = args[2];
                    specificSong = songManager.getSong(songName);
                    if (specificSong == null) {
                        sender.sendMessage("§cSong '" + songName + "' not found!");
                        return;
                    }
                }
            } else {
                specificSong = songManager.getSong(arg1);
                if (specificSong == null) {
                    sender.sendMessage("§cTarget player not online or song '" + arg1 + "' not found!");
                    return;
                }
            }
        }

        if (target != null) {
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
            if (sender instanceof Player) {
                if (specificSong != null) {
                    playbackManager.stopPlayback((Player) sender, specificSong);
                } else {
                    playbackManager.stopPlayback((Player) sender);
                }
            } else {
                if (specificSong != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playbackManager.stopPlayback(player, specificSong);
                    }
                    sender.sendMessage("§aStopped playing '" + specificSong.getName() + "' for all players.");
                } else {
                    playbackManager.stopAll();
                    sender.sendMessage("§aStopped all active playbacks.");
                }
            }
        }
    }

    private void handleStopAll(CommandSender sender, String[] args) {
        String target = null;
        if (args.length >= 2) {
            target = args[1];
        }

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to stop songs for all players.");
                    return;
                }
                playbackManager.stopAll();
                sender.sendMessage("§aStopped all active playbacks for all players.");
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
                playbackManager.stopPlayback(targetPlayer);
                sender.sendMessage("§aStopped all playing music for player " + targetPlayer.getName() + ".");
            }
        } else {
            if (sender instanceof Player) {
                playbackManager.stopPlayback((Player) sender);
            } else {
                playbackManager.stopAll();
                sender.sendMessage("§aStopped all active playbacks.");
            }
        }
    }

    private void handleSeek(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /notematic seek <[+|-]value[s|t]> [player/song] [song]");
            return;
        }

        String seekValueStr = args[1];
        String target = null;
        Song specificSong = null;

        if (args.length >= 3) {
            String arg2 = args[2];
            boolean isPlayerTarget = arg2.equalsIgnoreCase("@a") || arg2.equals("*") || Bukkit.getPlayer(arg2) != null;
            if (isPlayerTarget) {
                target = arg2;
                if (args.length >= 4) {
                    String songName = args[3];
                    specificSong = songManager.getSong(songName);
                    if (specificSong == null) {
                        sender.sendMessage("§cSong '" + songName + "' not found!");
                        return;
                    }
                }
            } else {
                specificSong = songManager.getSong(arg2);
                if (specificSong == null) {
                    sender.sendMessage("§cTarget player not online or song '" + arg2 + "' not found!");
                    return;
                }
            }
        }

        if (target != null) {
            if (target.equalsIgnoreCase("@a") || target.equals("*")) {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp()) {
                    sender.sendMessage("§cYou do not have permission to seek songs for all players.");
                    return;
                }
                if (specificSong != null) {
                    playbackManager.seekAll(specificSong, seekValueStr);
                    sender.sendMessage("§aSeeked '" + specificSong.getName() + "' for all online players.");
                } else {
                    playbackManager.seekAll(seekValueStr);
                    sender.sendMessage("§aSeeked playback for all online players.");
                }
            } else {
                if (!sender.hasPermission("notematic.admin") && !sender.isOp() && !sender.getName().equalsIgnoreCase(target)) {
                    sender.sendMessage("§cYou do not have permission to seek songs for other players.");
                    return;
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage("§cPlayer '" + target + "' is not online.");
                    return;
                }
                if (specificSong != null) {
                    if (playbackManager.seekPlayback(targetPlayer, specificSong, seekValueStr)) {
                        sender.sendMessage("§aSeeked '" + specificSong.getName() + "' for player " + targetPlayer.getName() + ".");
                    } else {
                        sender.sendMessage("§cSong '" + specificSong.getName() + "' is not playing for player " + targetPlayer.getName() + ".");
                    }
                } else {
                    if (playbackManager.seekPlayback(targetPlayer, seekValueStr)) {
                        sender.sendMessage("§aSeeked playback for player " + targetPlayer.getName() + ".");
                    } else {
                        sender.sendMessage("§cNo active playbacks found for player " + targetPlayer.getName() + ".");
                    }
                }
            }
        } else {
            if (sender instanceof Player) {
                if (specificSong != null) {
                    if (!playbackManager.seekPlayback((Player) sender, specificSong, seekValueStr)) {
                        sender.sendMessage("§cSong '" + specificSong.getName() + "' is not playing for you.");
                    }
                } else {
                    if (!playbackManager.seekPlayback((Player) sender, seekValueStr)) {
                        sender.sendMessage("§cNo active playbacks found for you.");
                    }
                }
            } else {
                if (specificSong != null) {
                    playbackManager.seekAll(specificSong, seekValueStr);
                    sender.sendMessage("§aSeeked '" + specificSong.getName() + "' for all players.");
                } else {
                    playbackManager.seekAll(seekValueStr);
                    sender.sendMessage("§aSeeked all active playbacks.");
                }
            }
        }
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
            subCommands.add("stopall");
            subCommands.add("seek");
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
                StringUtil.copyPartialMatches(args[1], getVisibleSongNames(sender), completions);
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
                    suggestions.addAll(getVisibleSongNames(sender));
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
                targets.addAll(getVisibleSongNames(sender));
                StringUtil.copyPartialMatches(args[1], targets, completions);
                Collections.sort(completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("stopall")) {
                List<String> targets = new ArrayList<>();
                targets.add("@a");
                targets.add("*");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    targets.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], targets, completions);
                Collections.sort(completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("seek")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("+5s");
                suggestions.add("+10s");
                suggestions.add("-5s");
                suggestions.add("-10s");
                suggestions.add("+20t");
                suggestions.add("-20t");
                suggestions.add("0s");
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
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
            } else if (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("pause") || args[0].equalsIgnoreCase("resume")) {
                String arg1 = args[1];
                boolean isPlayerTarget = arg1.equalsIgnoreCase("@a") || arg1.equals("*") || Bukkit.getPlayer(arg1) != null;
                if (isPlayerTarget) {
                    StringUtil.copyPartialMatches(args[2], getVisibleSongNames(sender), completions);
                    Collections.sort(completions);
                    return completions;
                }
            } else if (args[0].equalsIgnoreCase("seek")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("@a");
                suggestions.add("*");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                suggestions.addAll(getVisibleSongNames(sender));
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
                Collections.sort(completions);
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
            } else if (args[0].equalsIgnoreCase("seek")) {
                String arg2 = args[2];
                boolean isPlayerTarget = arg2.equalsIgnoreCase("@a") || arg2.equals("*") || Bukkit.getPlayer(arg2) != null;
                if (isPlayerTarget) {
                    StringUtil.copyPartialMatches(args[3], getVisibleSongNames(sender), completions);
                    Collections.sort(completions);
                    return completions;
                }
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
