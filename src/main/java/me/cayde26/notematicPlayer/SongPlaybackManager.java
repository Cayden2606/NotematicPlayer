package me.cayde26.notematicPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SongPlaybackManager {
    private final JavaPlugin plugin;
    private final List<SongPlayback> playbacks = new ArrayList<>();
    private BukkitTask tickTask;
    private int nextPlaybackId = 1;
    private boolean shuttingDown = false;

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

            Song song = playback.getSong();
            List<SongNote> notes = song.getNotes();
            int index = playback.getNextNoteIndex();
            double virtualTick = playback.getCurrentVirtualTick();

            Location loc = playback.getLocation();
            if (loc != null) {
                // Play all notes due up to current virtual tick
                double radiusSq = playback.getRadius() * playback.getRadius();
                while (index < notes.size()) {
                    SongNote note = notes.get(index);
                    if (note.getWhen() <= virtualTick) {
                        for (Player p : loc.getWorld().getPlayers()) {
                            if (p.getLocation().distanceSquared(loc) <= radiusSq) {
                                playNoteAtLocation(p, loc, note, playback);
                            }
                        }
                        index++;
                    } else {
                        break;
                    }
                }
            } else {
                UUID playerUuid = playback.getListenerUuid();
                Player player = Bukkit.getPlayer(playerUuid);
                boolean online = (player != null && player.isOnline());

                // Play all notes due up to current virtual tick
                while (index < notes.size()) {
                    SongNote note = notes.get(index);
                    if (note.getWhen() <= virtualTick) {
                        if (online) {
                            playNoteToPlayer(player, note, playback);
                        }
                        index++;
                    } else {
                        break;
                    }
                }
            }

            playback.setNextNoteIndex(index);
            playback.incrementTick();

            // Check if song has finished
            if (index >= notes.size()) {
                if (playback.isLooping()) {
                    playback.setNextNoteIndex(0);
                    if (!notes.isEmpty()) {
                        playback.seekToTick(notes.get(0).getWhen());
                    } else {
                        playback.seekToTick(0);
                    }
                } else {
                    Location l = playback.getLocation();
                    if (l == null) {
                        UUID playerUuid = playback.getListenerUuid();
                        Player player = Bukkit.getPlayer(playerUuid);
                        if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                            player.sendMessage("§aFinished playing song: §f" + song.getName());
                        }
                    }
                    iterator.remove();
                }
            }
        }
    }

    private void playNoteToPlayer(Player player, SongNote note, SongPlayback playback) {
        Song song = playback.getSong();
        boolean positional = playback.isPositional();
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

        float volume = (float) (note.getVolume() * song.getVolumeMultiplier() * playerVolMultiplier * playback.getVolumeMultiplier());
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

    private void playNoteAtLocation(Player player, Location loc, SongNote note, SongPlayback playback) {
        Song song = playback.getSong();
        if (song.isPrivate()) {
            if (!player.hasPermission("notematic.admin") && !player.isOp()) {
                return;
            }
        }

        double playerVolMultiplier = 1.0;
        if (plugin instanceof NotematicPlayer) {
            playerVolMultiplier = ((NotematicPlayer) plugin).getPlayerVolume(player);
        }

        float volume = (float) (note.getVolume() * song.getVolumeMultiplier() * playerVolMultiplier * playback.getVolumeMultiplier());
        float pitch = (float) note.getNote();

        String instrument = note.getInstrument();
        if (instrument != null && (instrument.contains(":") || instrument.contains(".") || instrument.contains("/"))) {
            player.playSound(loc, instrument, SoundCategory.RECORDS, volume, pitch);
        } else {
            Sound sound = getSoundFromInstrument(instrument);
            player.playSound(loc, sound, SoundCategory.RECORDS, volume, pitch);
        }
    }

    public void startPlayback(Player player, Song song) {
        startPlayback(player, song, true, false, "API", false);
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage) {
        startPlayback(player, song, showChatMessage, false, "API", false);
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage, boolean positional) {
        startPlayback(player, song, showChatMessage, positional, "API", false);
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage, boolean positional, String initiator) {
        startPlayback(player, song, showChatMessage, positional, initiator, false);
    }

    public void startPlayback(Player player, Song song, boolean showChatMessage, boolean positional, String initiator, boolean looping) {
        SongPlayback pb = new SongPlayback(song, player.getUniqueId(), showChatMessage, positional, initiator, looping);
        pb.setId(nextPlaybackId++);
        playbacks.add(pb);
        if (showChatMessage) {
            player.sendMessage("§aNow playing: §f" + song.getName());
        }
    }

    public void startPlayback(Location location, double radius, Song song, boolean looping, boolean showChatMessage, String initiator) {
        SongPlayback pb = new SongPlayback(song, location, radius, looping, showChatMessage, initiator);
        pb.setId(nextPlaybackId++);
        playbacks.add(pb);
        if (looping && !shuttingDown) {
            savePersistentPlaybacks();
        }
    }

    public void startPlayback(Location location, double radius, double volumeMultiplier, Song song, boolean looping, boolean showChatMessage, String initiator) {
        SongPlayback pb = new SongPlayback(song, location, radius, looping, showChatMessage, initiator);
        pb.setVolumeMultiplier(volumeMultiplier);
        pb.setId(nextPlaybackId++);
        playbacks.add(pb);
        if (looping && !shuttingDown) {
            savePersistentPlaybacks();
        }
    }

    public List<SongPlayback> getActivePlaybacks() {
        return new ArrayList<>(playbacks);
    }

    public SongPlayback getPlaybackById(int id) {
        for (SongPlayback pb : playbacks) {
            if (pb.getId() == id) {
                return pb;
            }
        }
        return null;
    }

    public void stopPlayback(SongPlayback playback) {
        if (playback == null) return;
        if (playbacks.remove(playback)) {
            if (playback.getLocation() == null) {
                Player player = Bukkit.getPlayer(playback.getListenerUuid());
                if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                    player.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                }
            } else if (playback.isLooping() && !shuttingDown) {
                savePersistentPlaybacks();
            }
        }
    }

    public boolean stopPlaybackById(int id) {
        SongPlayback pb = getPlaybackById(id);
        if (pb != null) {
            stopPlayback(pb);
            return true;
        }
        return false;
    }

    public int stopPlaybacksAtLocation(Location loc, String songName) {
        int stoppedCount = 0;
        boolean removedPersistent = false;
        Iterator<SongPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getLocation() != null && playback.getLocation().getWorld().equals(loc.getWorld())) {
                if (playback.getLocation().distanceSquared(loc) < 1.0) { // within 1 block
                    if (songName == null || playback.getSong().getName().equalsIgnoreCase(songName)) {
                        iterator.remove();
                        stoppedCount++;
                        if (playback.isLooping()) {
                            removedPersistent = true;
                        }
                    }
                }
            }
        }
        if (removedPersistent && !shuttingDown) {
            savePersistentPlaybacks();
        }
        return stoppedCount;
    }

    public void stopPlaybacksBySong(String songName) {
        boolean removedPersistent = false;
        Iterator<SongPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getSong().getName().equalsIgnoreCase(songName)) {
                iterator.remove();
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                    }
                } else if (playback.isLooping()) {
                    removedPersistent = true;
                }
            }
        }
        if (removedPersistent && !shuttingDown) {
            savePersistentPlaybacks();
        }
    }

    public void stopPlaybacksByPlayer(Player targetPlayer) {
        Iterator<SongPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getLocation() == null && playback.getListenerUuid().equals(targetPlayer.getUniqueId())) {
                iterator.remove();
                if (playback.isShowChatMessage()) {
                    targetPlayer.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                }
            }
        }
    }

    public void stopPlaybacksByInitiator(String initiator) {
        boolean removedPersistent = false;
        Iterator<SongPlayback> iterator = playbacks.iterator();
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getInitiator().equalsIgnoreCase(initiator)) {
                iterator.remove();
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§cStopped playing: §f" + playback.getSong().getName());
                    }
                } else if (playback.isLooping()) {
                    removedPersistent = true;
                }
            }
        }
        if (removedPersistent && !shuttingDown) {
            savePersistentPlaybacks();
        }
    }

    public void stopPlayback(Player player) {
        stopPlaybacksByPlayer(player);
    }

    public void stopPlayback(Player player, Song song) {
        Iterator<SongPlayback> iterator = playbacks.iterator();
        boolean stopped = false;
        while (iterator.hasNext()) {
            SongPlayback playback = iterator.next();
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && playback.getSong().getName().equalsIgnoreCase(song.getName())) {
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
        boolean removedPersistent = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null) {
                Player player = Bukkit.getPlayer(playback.getListenerUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage("§cPlayback stopped by server.");
                }
            } else if (playback.isLooping()) {
                removedPersistent = true;
            }
        }
        playbacks.clear();
        if (removedPersistent && !shuttingDown) {
            savePersistentPlaybacks();
        }
    }

    public boolean isPlaying(Player player) {
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public void pausePlayback(Player player) {
        boolean pausedAny = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && !playback.isPaused()) {
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
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && 
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
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && playback.isPaused()) {
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
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && 
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
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§ePlayback paused by server.");
                    }
                }
            }
        }
    }

    public void resumeAll() {
        for (SongPlayback playback : playbacks) {
            if (playback.isPaused()) {
                playback.setPaused(false);
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§aPlayback resumed by server.");
                    }
                }
            }
        }
    }

    public int pausePlaybacksAtLocation(Location loc, String songName) {
        int count = 0;
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() != null && playback.getLocation().getWorld().equals(loc.getWorld())) {
                if (playback.getLocation().distanceSquared(loc) < 1.0) { // within 1 block
                    if (songName == null || playback.getSong().getName().equalsIgnoreCase(songName)) {
                        if (!playback.isPaused()) {
                            playback.setPaused(true);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public int resumePlaybacksAtLocation(Location loc, String songName) {
        int count = 0;
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() != null && playback.getLocation().getWorld().equals(loc.getWorld())) {
                if (playback.getLocation().distanceSquared(loc) < 1.0) { // within 1 block
                    if (songName == null || playback.getSong().getName().equalsIgnoreCase(songName)) {
                        if (playback.isPaused()) {
                            playback.setPaused(false);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    public void pausePlaybacksBySong(String songName) {
        for (SongPlayback playback : playbacks) {
            if (playback.getSong().getName().equalsIgnoreCase(songName) && !playback.isPaused()) {
                playback.setPaused(true);
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§ePlayback paused: §f" + playback.getSong().getName());
                    }
                }
            }
        }
    }

    public void resumePlaybacksBySong(String songName) {
        for (SongPlayback playback : playbacks) {
            if (playback.getSong().getName().equalsIgnoreCase(songName) && playback.isPaused()) {
                playback.setPaused(false);
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§aPlayback resumed: §f" + playback.getSong().getName());
                    }
                }
            }
        }
    }

    public void pausePlaybacksByPlayer(Player targetPlayer) {
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(targetPlayer.getUniqueId()) && !playback.isPaused()) {
                playback.setPaused(true);
                if (playback.isShowChatMessage()) {
                    targetPlayer.sendMessage("§ePlayback paused: §f" + playback.getSong().getName());
                }
            }
        }
    }

    public void resumePlaybacksByPlayer(Player targetPlayer) {
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(targetPlayer.getUniqueId()) && playback.isPaused()) {
                playback.setPaused(false);
                if (playback.isShowChatMessage()) {
                    targetPlayer.sendMessage("§aPlayback resumed: §f" + playback.getSong().getName());
                }
            }
        }
    }

    public boolean seekPlayback(Player player, String seekValueStr) {
        boolean seekedAny = false;
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId())) {
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
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && 
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
                if (playback.getLocation() == null) {
                    Player player = Bukkit.getPlayer(playback.getListenerUuid());
                    if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                        player.sendMessage("§ePlayback seeked by server to " + formatPosition(playback));
                    }
                }
            }
        }
    }

    public void seekAll(Song song, String seekValueStr) {
        for (SongPlayback playback : playbacks) {
            if (playback.getSong().getName().equalsIgnoreCase(song.getName())) {
                if (playback.seek(seekValueStr)) {
                    if (playback.getLocation() == null) {
                        Player player = Bukkit.getPlayer(playback.getListenerUuid());
                        if (player != null && player.isOnline() && playback.isShowChatMessage()) {
                            player.sendMessage("§ePlayback for '" + song.getName() + "' seeked by server to " + formatPosition(playback));
                        }
                    }
                }
            }
        }
    }

    public String formatPosition(SongPlayback playback) {
        Song song = playback.getSong();
        double currentTick = playback.getCurrentVirtualTick();
        double tempo = song.getTempo();
        double seconds = currentTick / (20.0 * tempo);
        return String.format("%.1fs (%.0ft)", seconds, currentTick);
    }

    public boolean isPaused(Player player) {
        for (SongPlayback playback : playbacks) {
            if (playback.getLocation() == null && playback.getListenerUuid().equals(player.getUniqueId()) && playback.isPaused()) {
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

    public void prepareShutdown() {
        this.shuttingDown = true;
        savePersistentPlaybacks();
    }

    public void savePersistentPlaybacks() {
        File file = new File(plugin.getDataFolder(), "persistent_playbacks.yml");
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        
        for (SongPlayback pb : playbacks) {
            if (pb.getLocation() != null && pb.isLooping()) {
                Map<String, Object> map = new HashMap<>();
                map.put("song", pb.getSong().getName());
                map.put("world", pb.getLocation().getWorld().getName());
                map.put("x", pb.getLocation().getX());
                map.put("y", pb.getLocation().getY());
                map.put("z", pb.getLocation().getZ());
                map.put("radius", pb.getRadius());
                map.put("volume", pb.getVolumeMultiplier());
                map.put("tick", pb.getCurrentVirtualTick());
                map.put("initiator", pb.getInitiator());
                list.add(map);
            }
        }
        
        config.set("playbacks", list);
        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to save persistent_playbacks.yml", e);
        }
    }

    public void loadPersistentPlaybacks() {
        File file = new File(plugin.getDataFolder(), "persistent_playbacks.yml");
        if (!file.exists()) return;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> list = config.getList("playbacks");
        if (list == null) return;
        
        NotematicPlayer nmp = (NotematicPlayer) plugin;
        SongManager sm = nmp.getSongManager();
        int loadedCount = 0;
        
        for (Object obj : list) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                String songName = (String) map.get("song");
                String worldName = (String) map.get("world");
                
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Could not load persistent playback for song '" + songName + "' because world '" + worldName + "' is not loaded.");
                    continue;
                }
                
                Song song = sm.getSong(songName);
                if (song == null) {
                    plugin.getLogger().warning("Could not load persistent playback: song '" + songName + "' not found.");
                    continue;
                }
                
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                double radius = ((Number) map.get("radius")).doubleValue();
                double volumeMultiplier = ((Number) map.get("volume")).doubleValue();
                double tick = ((Number) map.get("tick")).doubleValue();
                String initiator = (String) map.get("initiator");
                
                Location location = new Location(world, x, y, z);
                
                SongPlayback pb = new SongPlayback(song, location, radius, true, true, initiator);
                pb.setVolumeMultiplier(volumeMultiplier);
                pb.seekToTick(tick);
                pb.setId(nextPlaybackId++);
                playbacks.add(pb);
                loadedCount++;
            }
        }
        if (loadedCount > 0) {
            plugin.getLogger().info("Loaded " + loadedCount + " persistent looping playbacks.");
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

