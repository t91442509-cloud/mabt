package com.example.manhunt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class ManhuntPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> runners = new HashSet<>();
    private final Set<UUID> hunters = new HashSet<>();
    private final Set<UUID> netherAnnounced = new HashSet<>();

    private int runnerStrengthLevel = 1; // 1 = 힘 1
    private int runnerExtraHearts = 0;   // 1 = 하트 1칸 (체력 2)
    private boolean gameStarted = false;

    private final String GUI_TITLE = ChatColor.DARK_PURPLE + "맨헌트 게임 설정";
    private NamespacedKey customGoldenAppleKey;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCustomRecipe();
        getLogger().info("맨헌트 플러그인(26.3)이 활성화되었습니다.");
    }

    private void registerCustomRecipe() {
        customGoldenAppleKey = new NamespacedKey(this, "runner_golden_apple");
        ItemStack result = new ItemStack(Material.GOLDEN_APPLE);
        ShapedRecipe recipe = new ShapedRecipe(customGoldenAppleKey, result);
        recipe.shape(" G ", "GAG", " G ");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('A', Material.APPLE);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            ItemStack star = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = star.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "맨헌트 설정 메뉴");
                star.setItemMeta(meta);
            }
            player.getInventory().addItem(star);
            openSetupGUI(player);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.NETHER_STAR) {
                event.setCancelled(true);
                openSetupGUI(event.getPlayer());
            }
        }
    }

    public void openSetupGUI(Player viewer) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                UUID uuid = target.getUniqueId();
                String roleText = ChatColor.GRAY + "대기 (역할 없음)";
                if (runners.contains(uuid)) roleText = ChatColor.GREEN + "러너 (Runner)";
                else if (hunters.contains(uuid)) roleText = ChatColor.RED + "헌터 (Hunter)";

                meta.setDisplayName(ChatColor.YELLOW + target.getName());
                meta.setLore(Arrays.asList(
                        ChatColor.WHITE + "현재 역할: " + roleText,
                        ChatColor.AQUA + "클릭하여 역할 변경"
                ));
                skull.setItemMeta(meta);
            }
            gui.setItem(slot++, skull);
        }

        ItemStack strengthItem = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta sMeta = strengthItem.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName(ChatColor.RED + "러너 힘 설정: " + ChatColor.WHITE + "힘 " + runnerStrengthLevel);
            sMeta.setLore(Collections.singletonList(ChatColor.GRAY + "클릭 시 1 ~ 5 사이로 변경"));
            strengthItem.setItemMeta(sMeta);
        }
        gui.setItem(47, strengthItem);

        ItemStack healthItem = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta hMeta = healthItem.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "러너 추가 체력: " + ChatColor.WHITE + "+" + runnerExtraHearts + "칸");
            hMeta.setLore(Collections.singletonList(ChatColor.GRAY + "클릭 시 0 ~ 10칸 사이로 변경"));
            healthItem.setItemMeta(hMeta);
        }
        gui.setItem(49, healthItem);

        ItemStack startItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta startMeta = startItem.getItemMeta();
        if (startMeta != null) {
            startMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "게임 시작");
            startMeta.setLore(Collections.singletonList(ChatColor.YELLOW + "클릭하면 즉시 게임이 시작됩니다."));
            startItem.setItemMeta(startMeta);
        }
        gui.setItem(51, startItem);

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (slot >= 0 && slot < 45 && clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetId = meta.getOwningPlayer().getUniqueId();
                if (!runners.contains(targetId) && !hunters.contains(targetId)) {
                    runners.add(targetId);
                } else if (runners.contains(targetId)) {
                    runners.remove(targetId);
                    hunters.add(targetId);
                } else {
                    hunters.remove(targetId);
                }
                openSetupGUI(player);
            }
        } else if (slot == 47) {
            runnerStrengthLevel = (runnerStrengthLevel % 5) + 1;
            openSetupGUI(player);
        } else if (slot == 49) {
            runnerExtraHearts = (runnerExtraHearts + 1) % 11;
            openSetupGUI(player);
        } else if (slot == 51) {
            player.closeInventory();
            startGame();
        }
    }

    private void startGame() {
        gameStarted = true;
        netherAnnounced.clear();

        Bukkit.broadcastMessage(ChatColor.GOLD + "====================================");
        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "맨헌트 게임이 시작되었습니다!");
        Bukkit.broadcastMessage(ChatColor.GOLD + "====================================");

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            p.getInventory().clear();
            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }

            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(20.0);
            }

            if (runners.contains(uuid)) {
                if (maxHealth != null) {
                    double newHealth = 20.0 + (runnerExtraHearts * 2.0);
                    maxHealth.setBaseValue(newHealth);
                    p.setHealth(newHealth);
                }

                // 시작 시 무조건 30초 동안 힘 1 부여
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30 * 20, 0));

                // 설정된 영구 힘 레벨 부여
                if (runnerStrengthLevel > 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, runnerStrengthLevel - 1));
                }

                // 기본 아이템 지급
                p.getInventory().addItem(
                        new ItemStack(Material.COOKED_BEEF, 64),
                        new ItemStack(Material.ENCHANTING_TABLE, 1),
                        new ItemStack(Material.BOOKSHELF, 32)
                );

                // 레벨 50 설정
                p.setLevel(50);
                p.sendMessage(ChatColor.GREEN + "당신은 [러너]입니다! 30초간 힘 1 버프와 기본 아이템이 지급되었습니다.");

            } else if (hunters.contains(uuid)) {
                p.setHealth(20.0);
                p.setLevel(30);
                p.sendMessage(ChatColor.RED + "당신은 [헌터]입니다! 러너를 처치하세요. (모루 사용 불가)");
            }
        }
    }

    @EventHandler
    public void onAnvilOpen(InventoryOpenEvent event) {
        if (!gameStarted) return;
        if (event.getInventory().getType() == InventoryType.ANVIL) {
            if (hunters.contains(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "헌터는 모루를 사용할 수 없습니다!");
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!gameStarted) return;
        Player player = event.getPlayer();
        if (runners.contains(player.getUniqueId())) {
            if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                if (!netherAnnounced.contains(player.getUniqueId())) {
                    netherAnnounced.add(player.getUniqueId());
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "[알림] " +
                            ChatColor.YELLOW + "러너 " + player.getName() + "님이 지옥에 갔습니다!");
                }
            }
        }
    }

    @EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() instanceof ShapedRecipe shapedRecipe) {
            if (shapedRecipe.getKey().equals(customGoldenAppleKey)) {
                if (event.getView().getPlayer() instanceof Player player) {
                    if (!runners.contains(player.getUniqueId())) {
                        event.getInventory().setResult(new ItemStack(Material.AIR));
                    }
                }
            }
        }
    }
}
