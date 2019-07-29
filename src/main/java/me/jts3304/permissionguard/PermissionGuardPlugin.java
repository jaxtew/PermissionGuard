package me.jts3304.permissionguard;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PermissionGuardPlugin extends JavaPlugin implements Listener {
    private Permission permissions;
    private Map<Player, Map.Entry<String, Integer>> playersNotLoggedIn;

    @Override
    public void onEnable() {
        permissions = getServer().getServicesManager().getRegistration(Permission.class).getProvider();
        playersNotLoggedIn = new HashMap<>();
        // SETUP CONFIGURATION
        saveDefaultConfig();
        Arrays.asList(permissions.getGroups()).forEach(group -> {
            if(!getConfig().contains("groups." + group)){
                String path = "groups." + group;
                getConfig().createSection(path);
                getConfig().set(path + ".password-enabled", false);
                getConfig().set(path + ".password", "password");
            }
        });
        saveConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getOnlinePlayers().forEach(this::addPlayer);
        // LOGIN CHECKER
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, ()-> {
            List<Player> keySet = new ArrayList<>(playersNotLoggedIn.keySet());
            keySet.forEach(player -> {
                playersNotLoggedIn.get(player).setValue(playersNotLoggedIn.get(player).getValue() + 1);
                if(playersNotLoggedIn.get(player).getValue() % 5 == 0) player.sendMessage(ChatColor.DARK_RED + "Please log " +
                        "in to your group.");
                if(playersNotLoggedIn.get(player).getValue() >= getConfig().getInt("login-period")){
                    permissions.playerRemoveGroup(null, player, permissions.getPrimaryGroup(player));
                    permissions.playerAddGroup(null, player, playersNotLoggedIn.get(player).getKey());
                    playersNotLoggedIn.remove(player);
                    player.kickPlayer("Group login timed out.");
                }
            });
        }, 0L, 20L);

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        addPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        if(playersNotLoggedIn.containsKey(event.getPlayer())){
            permissions.playerRemoveGroup(null, event.getPlayer(), permissions.getPrimaryGroup(event.getPlayer()));
            permissions.playerAddGroup(null, event.getPlayer(), playersNotLoggedIn.get(event.getPlayer()).getKey());
            playersNotLoggedIn.remove(event.getPlayer());
        }
    }

    private void addPlayer(Player player){
        String group = permissions.getPrimaryGroup(player);
        if(!getConfig().getBoolean("groups." + group + ".password-enabled")) return;
        permissions.playerRemoveGroup(null, player, group);
        permissions.playerAddGroup(null, player, getConfig().getString("default-group"));
        playersNotLoggedIn.put(player, new AbstractMap.SimpleEntry<>(group, 0));
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        if(playersNotLoggedIn.containsKey(event.getPlayer())){
            event.setCancelled(true); // players cant chat, if they type it wrong it would be displayed in chat
            Player player = event.getPlayer();
            String group = playersNotLoggedIn.get(player).getKey();
            String password = getConfig().getString("groups." + group + ".password");
            String command = getConfig().getString("login-command").replace("<password>", password);
            String message = event.getMessage();
            if(message.equals(command)){
                // login successful
                Bukkit.getScheduler().runTask(this, () -> {
                    permissions.playerRemoveGroup(null, event.getPlayer(), permissions.getPrimaryGroup(event.getPlayer()));
                    permissions.playerAddGroup(null, event.getPlayer(), playersNotLoggedIn.get(event.getPlayer()).getKey());
                    playersNotLoggedIn.remove(player);
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Welcome!");
                });
            }
        }
    }
}
