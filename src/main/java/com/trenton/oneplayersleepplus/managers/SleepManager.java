package com.trenton.oneplayersleepplus.managers;

import com.trenton.oneplayersleepplus.OnePlayerSleepPlus;
import com.trenton.coreapi.annotations.CoreManager;
import com.trenton.coreapi.util.MessageUtils;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tracks who is in bed and skips the night when enough players sleep.
 *
 * <p>The requirement is either a fixed head count or a percentage of online
 * players, per config. The skip is animated: a repeating task advances time
 * in small steps until morning rather than jumping there. With the
 * every-other-night restriction on, each skip disarms the next one until a
 * new dusk arrives.
 */
@CoreManager(
   name = "SleepManager"
)
public class SleepManager {
   private OnePlayerSleepPlus plugin;
   private FileConfiguration config;
   private FileConfiguration messages;
   private final ConcurrentHashMap<UUID, Player> sleepingPlayers = new ConcurrentHashMap<>();
   private String sleepMode;
   private double sleepPercentage;
   private int fixedSleepers;
   private boolean announceSleep;
   private boolean showActionBar;
   private boolean particlesEnabled;
   private String particleType;
   private int particleCount;
   private boolean soundEnabled;
   private String soundType;
   private float soundVolume;
   private float soundPitch;
   private boolean restrictEveryOtherNight;

   // Armed state for the every-other-night restriction: cleared when a skip
   // starts, re-armed by scheduleNightReset at the following dusk.
   private boolean canSkipNight = true;
   private BukkitRunnable actionBarTask;
   private BukkitRunnable timeSkipTask;

   public void init(OnePlayerSleepPlus plugin) {
      this.plugin = plugin;
      this.config = plugin.getConfig();
      this.messages = plugin.getMessagesConfig();
      this.loadConfig();
      this.scheduleNightReset();
   }

   public void shutdown() {
      this.stopActionBarTask();
      this.stopTimeSkip();
      this.sleepingPlayers.clear();
   }

   private void loadConfig() {
      this.sleepMode = this.config.getString("sleep_requirement.mode", "fixed").toLowerCase();
      this.sleepPercentage = Math.max(1.0, Math.min(100.0, this.config.getDouble("sleep_requirement.percentage", 50.0)));
      this.fixedSleepers = Math.max(1, this.config.getInt("sleep_requirement.fixed_players", 1));
      this.announceSleep = this.config.getBoolean("announce.enabled", true);
      this.showActionBar = this.config.getBoolean("action_bar.enabled", true);
      this.particlesEnabled = this.config.getBoolean("time_skip_effects.particles_enabled", true);
      this.particleType = this.config.getString("time_skip_effects.particle_type", "PORTAL").toUpperCase();
      this.particleCount = Math.max(1, this.config.getInt("time_skip_effects.particle_count", 5));
      this.soundEnabled = this.config.getBoolean("time_skip_effects.sound_enabled", true);
      this.soundType = this.config.getString("time_skip_effects.sound_type", "BLOCK_PORTAL_AMBIENT").toUpperCase();
      this.soundVolume = (float) Math.max(0.0, Math.min(1.0, this.config.getDouble("time_skip_effects.sound_volume", 0.5)));
      this.soundPitch = (float) Math.max(0.5, Math.min(2.0, this.config.getDouble("time_skip_effects.sound_pitch", 1.0)));
      this.restrictEveryOtherNight = this.config.getBoolean("night_skip_restriction.every_other_night", true);
   }

   public void addSleepingPlayer(Player player) {
      this.sleepingPlayers.put(player.getUniqueId(), player);
      if (this.showActionBar) {
         this.startActionBarTask();
      }

      this.trySkipNight(player);
   }

   public void removeSleepingPlayer(Player player) {
      this.sleepingPlayers.remove(player.getUniqueId());
      if (this.showActionBar && this.sleepingPlayers.isEmpty()) {
         this.stopActionBarTask();
      }

      if (!this.sleepingPlayers.isEmpty()) {
         this.trySkipNight(player);
      } else {
         this.stopTimeSkip();
      }
   }

   public boolean canSkipNight() {
      return !this.restrictEveryOtherNight || this.canSkipNight;
   }

   private void startActionBarTask() {
      if (this.actionBarTask != null) {
         this.actionBarTask.cancel();
      }

      this.actionBarTask = new BukkitRunnable() {
         public void run() {
            if (SleepManager.this.sleepingPlayers.isEmpty()) {
               SleepManager.this.stopActionBarTask();
            } else {
               int needed = SleepManager.this.getRequiredSleepers();
               int current = Math.min(SleepManager.this.sleepingPlayers.size(), needed);
               String message = SleepManager.this.messages.getString("sleepers_needed", "");
               if (!message.isEmpty()) {
                  message = message.replace("{progress}", String.valueOf(current)).replace("{time}", String.valueOf(needed));
                  message = ChatColor.translateAlternateColorCodes('&', message);

                  for (Player player : SleepManager.this.plugin.getServer().getOnlinePlayers()) {
                     player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                  }
               }
            }
         }
      };
      this.actionBarTask.runTaskTimer(this.plugin, 0L, 20L);
   }

   private void stopActionBarTask() {
      if (this.actionBarTask != null) {
         this.actionBarTask.cancel();
         this.actionBarTask = null;
      }
   }

   private int getRequiredSleepers() {
      int onlinePlayers = this.plugin.getServer().getOnlinePlayers().size();
      int required = this.sleepMode.equals("percentage") ? (int) Math.ceil(onlinePlayers * (this.sleepPercentage / 100.0)) : this.fixedSleepers;
      return Math.max(1, required);
   }

   private void trySkipNight(Player player) {
      if (this.canSkipNight()) {
         boolean isNight = false;

         for (World world : this.plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == Environment.NORMAL && world.getTime() > 12000L) {
               isNight = true;
               break;
            }
         }

         if (isNight) {
            int needed = this.getRequiredSleepers();
            int current = this.sleepingPlayers.size();
            if (current >= needed) {
               this.startTimeSkip();
            }
         }
      }
   }

   private void startTimeSkip() {
      if (this.timeSkipTask != null) {
         this.timeSkipTask.cancel();
      }

      if (this.restrictEveryOtherNight) {
         this.canSkipNight = false;
      }

      // Advances time by timeIncrement each server tick until morning is
      // reached, or forces morning after maxTicks as a stop against worlds
      // whose time is being moved by something else concurrently.
      this.timeSkipTask = new BukkitRunnable() {
         int ticks = 0;
         final int maxTicks = 100;
         final long targetTime = 0L;
         final long timeIncrement = 120L;
         final int soundInterval = 30; // ticks between ambient sound plays

         public void run() {
            for (World world : SleepManager.this.plugin.getServer().getWorlds()) {
               if (world.getEnvironment() == Environment.NORMAL) {
                  long newTime = world.getTime() + this.timeIncrement;
                  if (newTime >= 24000L) {
                     newTime %= 24000L;
                  }

                  world.setTime(newTime);

                  for (Player player : SleepManager.this.sleepingPlayers.values()) {
                     Location bedLocation = player.getBedSpawnLocation() != null ? player.getBedSpawnLocation() : player.getLocation();
                     if (SleepManager.this.particlesEnabled) {
                        try {
                           world.spawnParticle(Particle.valueOf(SleepManager.this.particleType), bedLocation.clone().add(0.0, 1.0, 0.0), SleepManager.this.particleCount, 0.5, 0.5, 0.5, 0.05);
                        } catch (IllegalArgumentException e) {
                           SleepManager.this.plugin.getLogger().warning("Invalid particle type: " + SleepManager.this.particleType + ", using PORTAL");
                           SleepManager.this.particleType = "PORTAL";
                        }
                     }

                     if (SleepManager.this.soundEnabled && this.ticks % this.soundInterval == 0) {
                        try {
                           world.playSound(bedLocation, Sound.valueOf(SleepManager.this.soundType), SleepManager.this.soundVolume, SleepManager.this.soundPitch);
                        } catch (IllegalArgumentException e) {
                           SleepManager.this.plugin.getLogger().warning("Invalid sound type: " + SleepManager.this.soundType + ", using BLOCK_PORTAL_AMBIENT");
                           SleepManager.this.soundType = "BLOCK_PORTAL_AMBIENT";
                        }
                     }
                  }

                  if (world.getTime() < 1000L || world.getTime() > 23000L) {
                     world.setTime(this.targetTime);
                     world.setStorm(false);
                     if (SleepManager.this.announceSleep) {
                        MessageUtils.broadcast(SleepManager.this.plugin, SleepManager.this.messages, "night_skipped");
                     }

                     SleepManager.this.sleepingPlayers.clear();
                     SleepManager.this.stopTimeSkip();
                     SleepManager.this.scheduleNightReset();
                  }
               }
            }

            ++this.ticks;
            if (this.ticks >= this.maxTicks) {
               for (World world : SleepManager.this.plugin.getServer().getWorlds()) {
                  if (world.getEnvironment() == Environment.NORMAL) {
                     world.setTime(this.targetTime);
                     world.setStorm(false);
                  }
               }

               if (SleepManager.this.announceSleep) {
                  MessageUtils.broadcast(SleepManager.this.plugin, SleepManager.this.messages, "night_skipped");
               }

               SleepManager.this.sleepingPlayers.clear();
               SleepManager.this.stopTimeSkip();
               SleepManager.this.scheduleNightReset();
            }
         }
      };
      this.timeSkipTask.runTaskTimer(this.plugin, 0L, 1L);
   }

   // Re-arms the every-other-night restriction: polls until a world reaches
   // dusk with no skip in progress, then allows the next skip and stops.
   private void scheduleNightReset() {
      if (this.restrictEveryOtherNight) {
         (new BukkitRunnable() {
            public void run() {
               for (World world : SleepManager.this.plugin.getServer().getWorlds()) {
                  if (world.getEnvironment() == Environment.NORMAL && world.getTime() > 13000L && world.getTime() < 14000L && SleepManager.this.timeSkipTask == null) {
                     SleepManager.this.canSkipNight = true;
                     this.cancel();
                  }
               }
            }
         }).runTaskTimer(this.plugin, 0L, 200L);
      }
   }

   private void stopTimeSkip() {
      if (this.timeSkipTask != null) {
         this.timeSkipTask.cancel();
         this.timeSkipTask = null;
      }
   }
}
