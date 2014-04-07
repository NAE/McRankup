package com.gmail.mrphpfan;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import ru.tehkode.permissions.PermissionGroup;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;

import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.skills.SkillType;

public class McRankup extends JavaPlugin implements Listener{
	
	private static final Logger log = Logger.getLogger("Minecraft");
	public static Economy econ = null;
	private ArrayList<Rank> ranks = new ArrayList<Rank>();
	
	McCombatLevel combatPlugin;
	
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		
		if(!setupEconomy()){
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		
		combatPlugin = (McCombatLevel) Bukkit.getServer().getPluginManager().getPlugin("McCombatLevel");
		
		loadConfiguration();
		
		getLogger().info("McRankup Enabled.");
	}
	
	@Override
	public void onDisable(){
		getLogger().info("McRankup Disabled.");
	}
	
    public void loadConfiguration(){
        File pluginFolder = this.getDataFolder();
		if(!pluginFolder.exists()){
			pluginFolder.mkdir();
		}
		
		this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        
        if(this.getConfig().get("ranks") == null){
	        ArrayList<String> defaultPerms = new ArrayList<String>();
	        this.getConfig().addDefault("ranks", defaultPerms);
	        this.saveConfig();
        }
        
        //read in ranks
        FileConfiguration config = this.getConfig();
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if(section != null){
        	Map<String,Object> ranksMap = section.getValues(false);
        	Iterator it = ranksMap.entrySet().iterator();
    	    while (it.hasNext()) {
    	        Map.Entry pairs = (Map.Entry)it.next();
    	        String rank = pairs.getKey().toString();
    	        getLogger().info(pairs.getKey() + ": ");
    	        if(pairs.getValue() instanceof MemorySection){
    	        	Rank rnk = new Rank(rank, (MemorySection) pairs.getValue());
    	        	ranks.add(rnk);
    	        }else{
    	        	getLogger().severe("Error in McRankup config. Please check syntax.");
    	        }
    	    }
        }
    }
	
	public boolean setupEconomy(){
    	if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		String cmdName = cmd.getName();
		Player player;
		if(sender instanceof Player){
			player = (Player) sender;
		}else{
			sender.sendMessage("You must be a player to execute this command.");
    		return true;
		}
		if(cmdName.equalsIgnoreCase("rankup")){
			if(args.length == 1){
				PermissionManager pex = PermissionsEx.getPermissionManager();
				//attempt to rank the player up
				String rnk = args[0];
				PermissionGroup group = pex.getGroup(rnk);
				PermissionUser user = pex.getUser(player);
				
				if(group == null){
					//specified group invalid
					player.sendMessage(ChatColor.GOLD + "Invalid rank specified.");
					return true;
				}
				
				Rank rank = null;
				//check that group is actually allowed to rank up to
				for(int i=0;i<ranks.size();i++){
					if(rnk.toLowerCase().equals(ranks.get(i).getRank().toLowerCase())){
						rank = ranks.get(i);
					}
				}
				
				if(rank == null){
					//user tried to rank up to a rank that wasn't allowed to rank up to
					player.sendMessage(ChatColor.GOLD + "You can't rank up to that rank.");
					return true;
				}
				
				//check to make sure user is in the group before this before ranking up to this one
				//go through all the ranks and fine the one they're looking for, then go 1 lower
				Rank lastRank = null;
				for(int i=1;i<ranks.size();i++){
					Rank checkRank = ranks.get(i);
					if(rank.getRank().equals(checkRank.getRank())){
						lastRank = ranks.get(i - 1);
						if(!user.inGroup(lastRank.getRank())){
							//they weren't in the group yet
							player.sendMessage(ChatColor.GOLD + "You need to get " + lastRank.getRank() + " first.");
							return true;
						}else{
							//they're in the required group to rank up.
							break;
						}
					}
				}
				
				//check that they're not ranking down by checking all the groups below lastRank
				//first get user's current rank
				int n=0;
				while(n < ranks.size()){
					String nextCheck = ranks.get(n).getRank();
					if(!user.inGroup(nextCheck)){
						break;
					}else{
						n++;
						continue;
					}
				}
				
				//now that we have user's current rank, make sure that they aren't ranking down
				int rankPos = n - 1;
				for(int i=rankPos;i>=0;i--){
					Rank checkRnk = ranks.get(i);
					if(checkRnk.getRank().equals(rank.getRank())){
						player.sendMessage(ChatColor.GOLD + "You have already attained that rank.");
						return true;
					}
				}
				
				//group was valid, check if user has all the requirements
				double balance = econ.getBalance(player.getName());
				if(balance < rank.getCost()){
					player.sendMessage(ChatColor.GOLD + "You don't have enough money for that rank.");
					return true;
				}
				
				if(combatPlugin != null){
					//get user's combat level via McCombatLevel
					int combatLevel = combatPlugin.getCombatLevel(player);
					if(combatLevel < rank.getCombat()){
						player.sendMessage(ChatColor.GOLD + "You don't have a high enough combat level for that rank.");
						return true;
					}
				}
				
				//check mcmmo stats
				Map<SkillType, Integer> mcmmo = rank.getMcmmo();
				Iterator it = mcmmo.entrySet().iterator();
				while(it.hasNext()){
					Map.Entry mcpair = (Map.Entry)it.next();
					String skill = mcpair.getKey().toString();
					int lvl = (Integer)mcpair.getValue();
					int skillLvl = ExperienceAPI.getLevel(player, skill);
					if(skillLvl < lvl){
						player.sendMessage(ChatColor.GOLD + "One or more of your MCMMO stats did not meet the requirement for that rank.");
						return true;
					}
				}
				
				//all the requirements have been met, rank the player up.
				//deduct money
				EconomyResponse r1 = econ.withdrawPlayer(player.getName(), rank.getCost());
				if(r1.transactionSuccess()){
					//add the user to the group
					user.addGroup(group);
					PermissionGroup lastGroup = pex.getGroup(lastRank.getRank());
					user.removeGroup(lastGroup);
					player.sendMessage(ChatColor.GOLD + "You have been promoted to " + rank.getRank() + "!");
				}else{
					player.sendMessage(ChatColor.GOLD + "An internal error occured. Try again.");;
				}
				return true;
			}else if(args.length == 0){
				//show available ranks, except first one because the user is obviously already that rank
				for(int i=1;i<ranks.size();i++){
					Rank rnk = ranks.get(i);
					String prefix = rnk.getGroup().getPrefix().replace("&", "§").replace(" ", "");
					if(prefix == null || prefix.equals("")){
						prefix = rnk.getRank();
					}
					int cost = rnk.getCost();
					int combat = rnk.getCombat();
					Map<SkillType, Integer> mcmmo = rnk.getMcmmo();
					
					player.sendMessage(prefix + ChatColor.WHITE + ": $" + rnk.getCost());
					if(combat == 0 && mcmmo.size() == 0){
						continue;
					}else{
						player.sendMessage("   Combat Level: " + combat);
						Iterator it = mcmmo.entrySet().iterator();
						while(it.hasNext()){
							Map.Entry mcpair = (Map.Entry)it.next();
							String skill = mcpair.getKey().toString();
							int lvl = (Integer) mcpair.getValue();
							player.sendMessage("   " + WordUtils.capitalize(skill.toString().toLowerCase()) + ": " + lvl);
						}
					}
				}
				player.sendMessage(ChatColor.GOLD + "Usage: /rankup [rank]");
				return true;
			}else{
				player.sendMessage("You specified too many arguments");
			}
		}
		return false;
	}
	
	public int getCombatLevel(Player player){
		int swords = ExperienceAPI.getLevel(player, "swords");
        int axes = ExperienceAPI.getLevel(player, "axes");
        int unarmed = ExperienceAPI.getLevel(player, "unarmed");
        int archery = ExperienceAPI.getLevel(player, "archery");
        int taming = ExperienceAPI.getLevel(player, "taming");
        int acrobatics = ExperienceAPI.getLevel(player, "acrobatics");
        
        int relevantSwords = swords <= 1000 ? swords : 1000;
        int relevantAxes = axes <= 1000 ? axes : 1000;
        int relevantUnarmed = unarmed <= 1000 ? unarmed : 1000;
        int relevantArchery = archery <= 1000 ? archery : 1000;
        int relevantTaming = taming <= 1000 ? taming : 1000;
        int relevantAcrobatics = acrobatics <= 1000 ? acrobatics : 1000;
        
        int combatLevel = (int) Math.floor((relevantSwords + relevantAxes + relevantUnarmed + relevantArchery + (.25 * relevantTaming) + (.25 * relevantAcrobatics)) / 45);
        return combatLevel;
	}
}
