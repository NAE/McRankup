package com.gmail.mrphpfan;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;

import com.gmail.nossr50.datatypes.skills.SkillType;

import ru.tehkode.permissions.PermissionGroup;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.bukkit.PermissionsEx;

public class Rank {
	private String rank;
	private int cost;
	private int combat;
	private PermissionGroup group;
	private Map<SkillType, Integer> mcmmo = new HashMap<SkillType, Integer>();
	
	private PermissionManager pex = PermissionsEx.getPermissionManager();
	
	public Rank(String rank, MemorySection data){
		this.rank = rank;
		cost = data.getInt("cost");
		combat = data.getInt("combat");
		group = pex.getGroup(rank);
		
		Bukkit.getServer().getConsoleSender().sendMessage("cost: " + cost);
		Bukkit.getServer().getConsoleSender().sendMessage("combat: " + combat);
		
		ConfigurationSection mcmmoReqs = data.getConfigurationSection("mcmmo");
		
		if(mcmmoReqs != null){
			Map<String, Object> mcdata = mcmmoReqs.getValues(false);
			Iterator it = mcdata.entrySet().iterator();
			while(it.hasNext()){
				Map.Entry mcpair = (Map.Entry)it.next();
				String skill = mcpair.getKey().toString();
				int lvl = (Integer) mcpair.getValue();
				SkillType type = SkillType.getSkill(skill);
				mcmmo.put(type, lvl);
			}
		}
	}
	
	public String getRank(){
		return rank;
	}
	
	public int getCost(){
		return cost;
	}
	
	public int getCombat(){
		return combat;
	}
	
	public PermissionGroup getGroup(){
		return group;
	}
	
	public Map<SkillType, Integer> getMcmmo(){
		return mcmmo;
	}
}
