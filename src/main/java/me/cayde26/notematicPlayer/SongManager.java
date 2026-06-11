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
    private final Map<String, Song> songs = new HashMap<>();

    public SongManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.songsFolder = new File(plugin.getDataFolder(), "songs");
        this.volumesFile = new File(plugin.getDataFolder(), "volumes.yml");
        init();
    }

    public void init() {
        if (!songsFolder.exists()) {
            songsFolder.mkdirs();
        }

        loadSongs();
    }

    public void loadSongs() {
        songs.clear();
        File[] files = songsFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && (file.getName().endsWith(".mcfunction") || file.getName().endsWith(".json"))) {
                    try {
                        Song song = SongParser.parseSong(file);
                        songs.put(song.getName().toLowerCase(), song);
                        plugin.getLogger().info("Loaded song '" + song.getName() + "' with " + song.getNotes().size() + " notes.");
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load song file " + file.getName(), e);
                    }
                }
            }
        }
        
        loadSongVolumes();
        saveSongVolumes(); // Purges deleted songs from configuration
        
        plugin.getLogger().info("Total songs loaded: " + songs.size());
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

