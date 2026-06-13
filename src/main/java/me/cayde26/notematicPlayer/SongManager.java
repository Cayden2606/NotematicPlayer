package me.cayde26.notematicPlayer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class SongManager {
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

    /**
     * Load a single song file dynamically into the memory cache.
     *
     * @param file The song file to parse (.json or .mcfunction)
     * @param isPrivate Whether the song is private
     * @return The parsed Song object
     * @throws IOException If parsing or reading fails
     */
    public Song loadSongFile(File file, boolean isPrivate) throws IOException {
        Song song = SongParser.parseSong(file);
        song.setPrivate(isPrivate);
        songs.put(song.getName().toLowerCase(), song);
        return song;
    }

    public Song getSong(String name) {
        if (name == null) return null;
        return songs.get(name.toLowerCase());
    }

    public Set<String> getSongNames() {
        return songs.keySet();
    }
}

