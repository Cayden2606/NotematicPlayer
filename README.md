# Notematic Player

A Minecraft Java plugin (Paper/Spigot 1.20.6 / 1.21 - 1.26.x) designed as the companion player for **Notematic Studio** (currently in pre-release). It plays custom note block music sequences exported from the studio in `.mcfunction` or `.json` formats. Beyond standard note block sounds, it can also play any in-game `/playsound` audio (including custom resource pack sounds) directly.

## Commands and Permissions

### Command Quick Reference

| Command | Description | Permission Node | Default |
|---|---|---|---|
| `/notematic [help]` | Displays the plugin info, status, and commands menu. | `notematic.use` | Everyone |
| `/notematic play ...` | Plays music for a player, everyone, or at a world location. | `notematic.use`<br>`notematic.play.location` (location)<br>`notematic.play.loop` (looping) | Everyone |
| `/notematic stop ...` | Stops active playbacks (targeted by ID, song, player, or location). | `notematic.use` | Everyone |
| `/notematic seek ...` | Seeks forward/backward or to an absolute time in a playback. | `notematic.use` | Everyone |
| `/notematic pause ...` | Pauses active playbacks (targeted by ID, song, player, or location). | `notematic.use` | Everyone |
| `/notematic resume ...` | Resumes paused playbacks (targeted by ID, song, player, or location). | `notematic.use` | Everyone |
| `/notematic volume <val>` | Sets your personal volume multiplier (0-100%). | `notematic.use` | Everyone |
| `/notematic volume #ID <val>` | Adjusts a specific active playback instance's volume (0-100%). | `notematic.use` | Everyone |
| `/notematic volume song ...`| Adjusts a specific song's global multiplier (admin only). | `notematic.admin` | OP |
| `/notematic active` | Lists active playbacks with IDs, progress, targets, and status. | `notematic.use` | Everyone |
| `/notematic list` | Lists all loaded songs available for playback. | `notematic.use` | Everyone |
| `/notematic commands ...` | Enables or disables player command usage globally. | `notematic.admin` | OP |
| `/notematic reload` | Stops all playbacks and reloads the song folder. | `notematic.admin` | OP |

---

### Detailed Command Instructions

#### 1. Play Command: `/notematic play <song> [target] [loop]`
Starts playing a song. Playback can target yourself, other players, all online players, or a physical 3D location in the world.

*   **Syntax & Variations:**
    *   `/notematic play <song>`
        Plays the song for yourself (only works if run by a player).
    *   `/notematic play <song> [player]`
        Plays the song for a specific player. (Requires `notematic.admin` to target others).
    *   `/notematic play <song> <@a | * | all>`
        Plays the song for all online players. (Requires `notematic.admin`).
    *   `/notematic play <song> at <x> <y> <z> [radius] [volume] [loop]`
        Plays the song at a specific 3D location. Sounds are positioned client-side relative to the location. Only players within `radius` (in blocks) can hear it.
        *   Supports relative coordinates using `~` (e.g. `~ ~ ~` plays at your current foot level, `~ ~2 ~` plays 2 blocks above).
        *   `[radius]` is optional and defaults to `32.0` blocks.
        *   `[volume]` is optional, defaults to `100%` (`1.0`), and allows specifying the volume multiplier for that playback instance.
        *   (Requires `notematic.play.location`).
    *   **Looping:** Append the literal keyword `loop` at the very end of any play command to enable infinite playback looping. (Requires `notematic.play.loop`).
*   **Examples:**
    *   `/notematic play test_song` (Play "test_song" for yourself)
    *   `/notematic play test_song loop` (Play "test_song" looping for yourself)
    *   `/notematic play test_song Steve` (Play for player Steve)
    *   `/notematic play test_song at ~ ~ ~ 15 50% loop` (Play looping sound at 50% volume with a 15-block listening radius)

#### 2. Stop Command: `/notematic stop [#ID | song | player] [at <x> <y> <z>]`
Stops active playbacks. Targets are resolved dynamically in a strict search order.

*   **Target Resolution Order:**
    1.  **Unique ID (`#ID`):** Stops the specific playback instance matching the numerical ID (e.g., `#3`). Normal players can only stop playbacks they initiated. Admins can stop any ID.
    2.  **Song Name:** Stops active playbacks of the specified song. Normal players can only stop their own; admins stop all playbacks of that song.
    3.  **Player Name:** Stops all playbacks currently listening on the specified player. (Requires `notematic.admin` to stop other players).
*   **Syntax & Variations:**
    *   `/notematic stop`
        Stops all of your own active playbacks.
    *   `/notematic stop [#ID | song | player]`
        Stops the specified target.
    *   `/notematic stop at <x> <y> <z>` or `/notematic stop <song> at <x> <y> <z>`
        Stops active location-based playbacks at the specified coordinates (supports relative `~` coordinates). (Requires `notematic.admin`).
*   **Examples:**
    *   `/notematic stop` (Stop all your playbacks)
    *   `/notematic stop #12` (Stop the specific playback instance #12)
    *   `/notematic stop test_song` (Stop all playbacks of "test_song" you initiated)
    *   `/notematic stop at ~ ~ ~` (Stop location-based playbacks at your current coordinates)

#### 3. Seek Command: `/notematic seek <#ID | song | player> <value>`
Seeks active playbacks forward, backward, or to a specific time.

*   **Targets:** Can target a specific playback ID (`#ID`), all playbacks of a `song`, or all playbacks of a `player`. Normal players can only seek playbacks they initiated.
*   **Value Formats:**
    *   **Relative Seek:** e.g., `+10s` (forward 10 seconds), `-5s` (backward 5 seconds), `+200t` (forward 200 ticks), `-50t` (backward 50 ticks).
    *   **Absolute Seek:** e.g., `30s` (sets progress to exactly 30 seconds), `150t` (sets progress to tick 150).
*   **Looping Behavior:** Seeks that go past the end or before the beginning of the song will wrap around seamlessly using modulo math if the song is looping. If the song is not looping, seeking past the end stops the playback, and seeking before the beginning clamps to the start (`0s`).
*   **Examples:**
    *   `/notematic seek #5 +10s` (Seeks playback ID #5 forward by 10 seconds)
    *   `/notematic seek test_song 0s` (Restarts all active playbacks of "test_song" from the beginning)
    *   `/notematic seek Steve -5s` (Seeks Steve's playbacks back by 5 seconds)

#### 4. Volume Commands
Personal volume settings, global song multipliers, and instance-specific volume multipliers stack dynamically.

*   **Personal Volume: `/notematic volume <value>`**
    *   Sets your own personal listening comfort volume.
    *   Accepts decimals (e.g. `0.8` for 80%) or percentages (e.g. `80%` or `80`).
    *   Inputs above `100%` (1.0) are clamped to `100%` with a warning message to protect players' hearing.
    *   UUID-bound and persisted in `player_volumes.yml` across server restarts.
*   **Instance Playback Volume: `/notematic volume #ID <value>`**
    *   Sets the temporary volume multiplier of the active playback instance.
    *   Accepts decimals and percentages. Clamps to `100%` with a warning.
    *   Normal players can only adjust playbacks they initiated. Admins can adjust any.
    *   *Note: This is temporary and resets when the song finishes or is stopped.*
*   **Global Song Multiplier: `/notematic volume song <name> <value>`**
    *   Sets the base volume level for a specific song. (Requires `notematic.admin`).
    *   Accepts decimals and percentages. Volume levels are stored in `volumes.yml`.
    *   *Note: Personal comfort, global song, and instance volume multipliers all stack dynamically (e.g., if a song is at `50%`, your personal volume is `80%`, and the instance playback is at `50%`, the resulting note volume will be `0.5 * 0.8 * 0.5 = 20%`).*
*   **Examples:**
    *   `/notematic volume 80%` (Sets your personal volume to 80%)
    *   `/notematic volume #3 50%` (Sets playback instance #3 to 50% volume)
    *   `/notematic volume song test_song 60%` (Sets "test_song"'s global volume to 60%)

#### 5. Active Command: `/notematic active`
Displays a comprehensive list of all active playbacks currently playing on the server.

*   **Details Displayed:**
    *   `#ID` (e.g., `#3`) - The unique identifier to target specific playbacks.
    *   `Song` - The song name.
    *   `Target` - Who/where the song is playing for (Player name or Coordinate location + radius).
    *   `Initiator` - The name of the player or console who started the playback.
    *   `Progress` - Real-time progress percentage (e.g., `42.5%`).
    *   `Looping` - Indicates whether the playback loops infinitely.
    *   `Paused` - Shows whether the playback is currently paused.

#### 6. Pause & Resume Commands
Temporarily pauses or resumes active playbacks. Targets are resolved in the exact same order as the `/notematic stop` command.

*   **Syntax & Variations:**
    *   `/notematic pause` / `/notematic resume`
        Pauses or resumes all of your own active playbacks.
    *   `/notematic pause [#ID | song | player]` / `/notematic resume [#ID | song | player]`
        Pauses or resumes the specified target (ID, song name, or player name). Normal players can only affect playbacks they initiated.
    *   `/notematic pause at <x> <y> <z>` or `/notematic pause <song> at <x> <y> <z>`
        Pauses location-based playbacks at the specified coordinates (supports relative `~` coordinates). (Requires `notematic.admin`).
    *   `/notematic resume at <x> <y> <z>` or `/notematic resume <song> at <x> <y> <z>`
        Resumes location-based playbacks at the specified coordinates (supports relative `~` coordinates). (Requires `notematic.admin`).
*   **Examples:**
    *   `/notematic pause` (Pause all your active playbacks)
    *   `/notematic pause #5` (Pause specific playback instance #5)
    *   `/notematic pause test_song` (Pause all playbacks of "test_song" you initiated)
    *   `/notematic resume at ~ ~ ~` (Resume location-based playbacks at your current coordinates)

#### 7. Admin Commands
*   `/notematic commands [true | false]`
    Enables or disables commands for players without `notematic.admin` or OP status. If run with no argument, toggles the state.
*   `/notematic reload`
    Stops all active playbacks, clears cache, and reloads the `songs/` directory. Use this when adding new music files without restarting the server.

## Playback Persistence & Sync

### Offline Player Playback
When a player disconnects from the server, any active playbacks for them are **not** immediately terminated. Instead, they continue ticking virtually in the background. 
* If the player rejoins before the song ends, the playback automatically resumes playing sound packets, in perfect sync with the virtual timeline (no stop-and-resume lag).
* If the song completes while the player is offline, the playback finishes normally and cleans itself up.

### Persistent Location Loops (Jukebox Style XYZ Loops)
Any location-based playback that is set to loop (e.g. `/notematic play <song> at <x> <y> <z> [radius] [volume] loop`) is **persistent across server restarts and crashes**:
* **Database Storage:** The active loop parameters (location, radius, volume, song, initiator) are stored in `persistent_playbacks.yml`.
* **Dynamic Updates:** The database is updated live whenever a loop is started or stopped, protecting against unexpected server crashes.
* **Resumption:** On server startup, the plugin auto-resumes all looping sounds from the exact tick progress they were at when the server stopped/restarted.

## Music File Formats & Organization

Place your music files (`.mcfunction` or `.json`) in `plugins/NotematicPlayer/songs/`. The plugin automatically categorizes them by folder structure to manage access control:

*   **Public Songs (`/songs/` or `/songs/public/`):**
    *   Place files directly in `songs/` or in `songs/public/`.
    *   **Access:** Anyone can list these songs via `/notematic list` and play them.
*   **Private Songs (`/songs/private/`):**
    *   Place files in the `songs/private/` subfolder.
    *   **Access:** Only OPs/admins (or players with the `notematic.admin` permission) can list or play these songs. Normal players cannot see or access them.

The plugin automatically parses both:
1.  **Native `.mcfunction` datapacks** (generated by Notematic Studio).
2.  **Clean, lightweight `.json` files** (generated by Notematic Studio or custom MIDI converters).

---

### 1. Clean JSON format (for custom MIDI exporters)

This is the simplest format to use for writing a custom exporter. Save your song as a `.json` file:

```json
{
  "tempo": 1.0,
  "notes": [
    {
      "instrument": "harp",
      "note": 1.4142,
      "volume": 0.8,
      "when": 0
    },
    {
      "instrument": "bass",
      "note": 0.7071,
      "volume": 0.6,
      "when": 5
    }
  ]
}
```

#### JSON Fields:
- **`tempo`**: (double) Speed multiplier. `1.0` plays at normal speed (virtual tick matches server tick). `2.0` plays twice as fast.
- **`notes`**: (list) Array of note events, sorted by `when`.
  - **`instrument`**: (string) Note block instrument name (e.g. `harp`, `piano`, `bass`, `bell`, `flute`, `guitar`, `pling`, `bit`, `banjo`). You can also use any in-game `/playsound` identifier (e.g., `minecraft:entity.cow.ambient`, `minecraft:block.bell.use`, or custom resource pack sounds) by specifying a key containing a `.`, `:`, or `/`.
  - **`note`**: (double) Pitch multiplier from `0.5` to `2.0` (matching Minecraft's playsound/note block pitch scales).
  - **`volume`**: (double, optional) Volume from `0.0` to `1.0` (defaults to `1.0`).
  - **`when`**: (int) The tick delay at which the note plays relative to the start of the song.

---

### 2. Notematic mcfunction Datapack Format

If you drop an `.mcfunction` file into `plugins/NotematicPlayer/songs/`, the plugin will scan for the storage sheet command and extract the JSON:
```mcfunction
data modify storage music sheet set value {"tempo":1,"notes":[{"instrument":"harp","note":1.4142,"when":0}, ...]}
```

## Volume Management & Storage

### Global Song Volume
* **`/notematic volume song <name> <value>`**
* Volume values are saved in `plugins/NotematicPlayer/volumes.yml`.
* Tied directly to song files on disk. If a song file is deleted, its entry in `volumes.yml` is automatically purged.

### Per-Player Personal Volume
* **`/notematic volume <value>`** (Sets your own personal comfort volume)
* The `<value>` can be a decimal (e.g., `0.5` for 50%) or a percentage (e.g., `80%` or `80`).
* Personal volumes are stored using UUIDs in `plugins/NotematicPlayer/player_volumes.yml` and persist across server restarts.
* Inputs above `100%` (1.0) are automatically clamped to `100%` with a warning message. Negative values are rejected.
* **Volume Stacking:** The final volume a player hears for any note block is calculated dynamically as: `baseNoteVolume * songVolumeMultiplier * personalPlayerVolume`. It is applied globally to both direct and positional playbacks.

## Developer API

Other plugins can interact with `NotematicPlayer` directly to play or stop music.

### Accessing the API
Retrieve the plugin instance from Bukkit's plugin manager:
```java
import me.cayde26.notematicPlayer.NotematicPlayer;
import org.bukkit.Bukkit;

NotematicPlayer api = (NotematicPlayer) Bukkit.getPluginManager().getPlugin("NotematicPlayer");
if (api != null) {
    // Ex: play a song for a player
    api.playSong(player, "chromatic_scale");
}
```

### Available API Methods

#### Playback Control
- **`boolean playSong(Player player, String songName)`**: Plays the song for the specified player. Returns `true` if successful.
- **`boolean playSong(Player player, String songName, boolean showChatMessage)`**: Plays the song with optional chat announcements.
- **`boolean playSong(Player player, String songName, boolean showChatMessage, boolean positional)`**: Plays the song, option to play positionally in the world.
- **`boolean playSong(Player player, String songName, boolean showChatMessage, boolean positional, String initiator)`**: Plays the song, tracking the initiator.
- **`boolean playSongForAll(String songName)`**: Plays the song for all online players.
- **`boolean playSongForAll(String songName, boolean showChatMessage)`**
- **`boolean playSongForAll(String songName, boolean showChatMessage, boolean positional)`**
- **`boolean playSongForAll(String songName, boolean showChatMessage, boolean positional, String initiator)`**
- **`void stopSong(Player player)`**: Stops all active playbacks for the specified player.
- **`void stopSong(Player player, String songName)`**: Stops a specific active song playback for the player.
- **`void stopAllSongs()`**: Stops all active song playbacks on the server.
- **`void pauseSong(Player player)`**: Pauses all active song playbacks for the player.
- **`void pauseSong(Player player, String songName)`**
- **`void resumeSong(Player player)`**: Resumes all paused song playbacks for the player.
- **`void resumeSong(Player player, String songName)`**
- **`void pauseAllSongs()`**: Pauses all active playbacks on the server.
- **`void resumeAllSongs()`**: Resumes all active playbacks on the server.

#### Status & Query
- **`boolean isPlaying(Player player)`**: Checks if the player is currently listening to at least one song.
- **`boolean isPaused(Player player)`**: Checks if the player has any paused song playbacks.
- **`boolean songExists(String songName)`**: Checks if a song is loaded in the library.
- **`List<SongPlayback> getActivePlaybacks()`**: Returns list of all active playbacks.

#### Volume Adjustments
- **`double getSongVolume(String songName)`**: Gets the global song volume multiplier.
- **`boolean setSongVolume(String songName, double multiplier)`**: Sets the global song volume multiplier.
- **`double getPlayerVolume(Player player)`**: Gets the personal volume multiplier of the player.
- **`void setPlayerVolume(Player player, double volumeMultiplier)`**: Sets the personal volume multiplier of the player (persisted).

