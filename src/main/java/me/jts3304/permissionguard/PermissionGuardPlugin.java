package me.jts3304.permissionguard;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
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
                getConfig().set(path + ".op", false);
            }
        });
        saveConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("pgreload").setExecutor(this);
        Bukkit.getOnlinePlayers().forEach(this::addPlayer);
        // LOGIN CHECKER
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, ()-> {
            List<Player> keySet = new ArrayList<>(playersNotLoggedIn.keySet());
            keySet.forEach(player -> {
                playersNotLoggedIn.get(player).setValue(playersNotLoggedIn.get(player).getValue() + 1);
                if(playersNotLoggedIn.get(player).getValue() % 5 == 0)
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("login-prompt")));
                if(playersNotLoggedIn.get(player).getValue() >= getConfig().getInt("login-timeout")){
                    permissions.playerRemoveGroup(null, player, permissions.getPrimaryGroup(player));
                    permissions.playerAddGroup(null, player, playersNotLoggedIn.get(player).getKey());
                    boolean shouldBeOp = getConfig().getBoolean("groups." + playersNotLoggedIn.get(player).getKey() + ".op");
                    if(player.isOp() != shouldBeOp) player.setOp(shouldBeOp);
                    playersNotLoggedIn.remove(player);
                    player.kickPlayer(
                            ChatColor.translateAlternateColorCodes('&', getConfig().getString("login" +
                            "-timeout-message")));
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
        player.setOp(false);
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
                    permissions.playerRemoveGroup(null, player, permissions.getPrimaryGroup(player));
                    permissions.playerAddGroup(null, player, playersNotLoggedIn.get(player).getKey());
                    playersNotLoggedIn.remove(player);
                    boolean shouldBeOp = getConfig().getBoolean("groups." + group + ".op");
                    if(player.isOp() != shouldBeOp) player.setOp(shouldBeOp);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            getConfig().getString("login-welcome")));
                });
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event){
        getConfig().getStringList("blocked-commands").forEach(command -> {
            if(event.getMessage().substring(1).startsWith(command)){
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("blocked-command-message")));
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "PermissionGuard configuration file reloaded.");
        return true;
    }
}
