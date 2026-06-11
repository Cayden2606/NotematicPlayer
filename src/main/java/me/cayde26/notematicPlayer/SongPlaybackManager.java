package me.cayde26.notematicPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class SongPlaybackManager {
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
        Sound sound = getSoundFromInstrument(note.getInstrument());
        
        double playerVolMultiplier = 1.0;
        if (plugin instanceof NotematicPlayer) {
            playerVolMultiplier = ((NotematicPlayer) plugin).getPlayerVolume(player);
        }

        float volume = (float) (note.getVolume() * song.getVolumeMultiplier() * playerVolMultiplier);
        float pitch = (float) note.getNote();

        if (positional) {
            player.getWorld().playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
        } else {
            player.playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
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

