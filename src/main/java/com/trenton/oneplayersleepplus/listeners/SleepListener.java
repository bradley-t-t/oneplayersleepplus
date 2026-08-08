package com.trenton.oneplayersleepplus.listeners;

import com.trenton.oneplayersleepplus.OnePlayerSleepPlus;
import com.trenton.oneplayersleepplus.managers.SleepManager;
import com.trenton.coreapi.annotations.CoreListener;
import com.trenton.coreapi.api.CoreListenerInterface;
import com.trenton.coreapi.util.MessageUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;

@CoreListener(
   name = "SleepListener"
)
public class SleepListener implements CoreListenerInterface {
   private OnePlayerSleepPlus plugin;
   private SleepManager sleepManager;
   private FileConfiguration messages;

   public void init(OnePlayerSleepPlus plugin) {
      this.plugin = plugin;
      this.messages = plugin.getMessagesConfig();
      this.sleepManager = (SleepManager)plugin.getCoreAPI().getManager("SleepManager");
      if (this.sleepManager == null) {
         plugin.getLogger().severe("SleepManager is null in SleepListener.init");
      }

   }

   public void handleEvent(Event event) {
      if (this.sleepManager != null) {
         if (event instanceof PlayerBedEnterEvent bedEnterEvent) {
            if (bedEnterEvent.getBedEnterResult() != BedEnterResult.OK) {
               return;
            }

            if (!this.sleepManager.canSkipNight()) {
               bedEnterEvent.setCancelled(true);
               MessageUtils.sendMessage(this.messages, bedEnterEvent.getPlayer(), "night_skip_blocked");
               return;
            }

            this.sleepManager.addSleepingPlayer(bedEnterEvent.getPlayer());
         } else if (event instanceof PlayerBedLeaveEvent bedLeaveEvent) {
            this.sleepManager.removeSleepingPlayer(bedLeaveEvent.getPlayer());
         }
      }
   }

   public Class<? extends Event>[] getHandledEvents() {
      return new Class[]{PlayerBedEnterEvent.class, PlayerBedLeaveEvent.class};
   }
}
