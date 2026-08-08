package com.trenton.oneplayersleepplus;

import org.bstats.bukkit.Metrics;
import com.trenton.coreapi.api.CoreAPI;
import com.trenton.updater.api.UpdaterImpl;
import com.trenton.updater.api.UpdaterService;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class OnePlayerSleepPlus extends JavaPlugin {
   private FileConfiguration messagesConfig;
   private UpdaterService updater;
   private CoreAPI coreAPI;

   public void onEnable() {
      this.saveDefaultConfig();
      this.saveDefaultMessagesConfig();
      File messagesFile = new File(this.getDataFolder(), "messages.yml");
      this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
      String packageName = this.getClass().getPackageName();
      this.coreAPI = new CoreAPI(this, packageName);
      this.coreAPI.initialize();
      boolean autoUpdaterEnabled = this.getConfig().getBoolean("auto_updater.enabled", true);
      this.updater = new UpdaterImpl(this, 124265);
      if (autoUpdaterEnabled) {
         this.updater.checkForUpdates(true);
      }

      new Metrics(this, 25550);
   }

   public void onDisable() {
      if (this.coreAPI != null) {
         this.coreAPI.shutdown();
      }

      if (this.updater != null) {
         this.updater.handleUpdateOnShutdown();
      }

   }

   public FileConfiguration getMessagesConfig() {
      return this.messagesConfig;
   }

   public CoreAPI getCoreAPI() {
      return this.coreAPI;
   }

   private void saveDefaultMessagesConfig() {
      File messagesFile = new File(this.getDataFolder(), "messages.yml");
      if (!messagesFile.exists()) {
         this.saveResource("messages.yml", false);
      }

   }

   public UpdaterService getUpdater() {
      return this.updater;
   }
}
