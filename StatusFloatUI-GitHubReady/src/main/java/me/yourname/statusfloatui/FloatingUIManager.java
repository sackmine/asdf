package me.yourname.statusfloatui;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class FloatingUIManager implements Listener {

    private final StatusUIPlugin plugin;
    private final NamespacedKey KEY_TYPE;
    private final NamespacedKey KEY_STORED_ITEM;
    private final Map<UUID, PlayerUI> uis = new HashMap<>();
    private BukkitTask ticker;

    public FloatingUIManager(StatusUIPlugin plugin) {
        this.plugin = plugin;
        KEY_TYPE = new NamespacedKey(plugin, "ui_type");
        KEY_STORED_ITEM = new NamespacedKey(plugin, "stored_item");
    }

    public void startTicker() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerUI ui = uis.get(p.getUniqueId());
            if (ui == null) continue;
            ui.updatePositions();
        }
    }

    public void openStats(Player p) {
        closeAllFor(p);
        PlayerUI ui = new PlayerUI(p);
        ui.spawnStats();
        uis.put(p.getUniqueId(), ui);
        p.sendMessage(Component.text("스탯 UI를 켰습니다."));
    }

    public void openEnchant(Player p) {
        closeAllFor(p);
        PlayerUI ui = new PlayerUI(p);
        ui.spawnEnchant();
        uis.put(p.getUniqueId(), ui);
        p.sendMessage(Component.text("인첸트 UI를 켰습니다."));
    }

    public void openEnhance(Player p) {
        closeAllFor(p);
        PlayerUI ui = new PlayerUI(p);
        ui.spawnEnhance();
        uis.put(p.getUniqueId(), ui);
        p.sendMessage(Component.text("강화 UI를 켰습니다."));
    }

    public void closeAllFor(Player p) {
        PlayerUI ui = uis.remove(p.getUniqueId());
        if (ui != null) {
            ui.despawnAll();
        }
        p.sendMessage(Component.text("상태창을 껐습니다."));
    }

    public void cleanupAll() {
        if (ticker != null) ticker.cancel();
        for (PlayerUI ui : uis.values()) ui.despawnAll();
        uis.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        closeAllFor(e.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Interaction inter)) return;
        Player p = e.getPlayer();
        PlayerUI ui = uis.get(p.getUniqueId());
        if (ui == null) return;
        String type = inter.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        if (type == null) return;

        switch (type) {
            // Stats clicks
            case "stat_jump" -> ui.levelUpJump();
            case "stat_regen" -> ui.levelUpRegen();
            case "stat_haste" -> ui.levelUpHaste();
            // Enchant UI
            case "ench_slot" -> ui.toggleEnchantSlot();
            case "ench_button" -> ui.clickEnchantButton();
            // Enhance UI
            case "enh_slot" -> ui.toggleEnhanceSlot();
            case "enh_button" -> ui.clickEnhanceButton();
        }
    }

    // ===== inner =====
    private class PlayerUI {
        final Player p;
        final World w;
        final List<Entity> spawned = new ArrayList<>();

        // positions (relative)
        final Vector baseOffset = new Vector(0, 0.5, 1.8); // in front of eyes
        final Vector left = new Vector(-0.6, 0, 0);
        final Vector mid  = new Vector( 0.0, 0, 0);
        final Vector right= new Vector( 0.6, 0, 0);

        // stats
        int jumpLv = 0, regenLv = 0, hasteLv = 0;

        // enchant/enhance stored item
        ItemStack storedEnchant = null;
        ItemStack storedEnhance = null;

        // displays
        ItemDisplay dJump, dRegen, dHaste;
        Interaction iJump, iRegen, iHaste;

        ItemDisplay dSlot, dButton; // generic for ench/enh
        Interaction iSlot, iButton;

        PlayerUI(Player p) {
            this.p = p;
            this.w = p.getWorld();
        }

        void spawnStats() {
            dJump = spawnItem(left, new ItemStack(Material.RABBIT_FOOT));
            dRegen = spawnItem(mid, new ItemStack(Material.GOLDEN_APPLE));
            dHaste = spawnItem(right, new ItemStack(Material.DIAMOND_PICKAXE));
            iJump = spawnClick(dJump, "stat_jump");
            iRegen = spawnClick(dRegen, "stat_regen");
            iHaste = spawnClick(dHaste, "stat_haste");
        }

        void spawnEnchant() {
            dSlot = spawnItem(left, new ItemStack(Material.BARRIER)); // empty slot
            name(dSlot, "아이템 넣기");
            iSlot = spawnClick(dSlot, "ench_slot");
            dButton = spawnItem(right, new ItemStack(Material.ENCHANTED_BOOK));
            name(dButton, "인첸트");
            iButton = spawnClick(dButton, "ench_button");
        }

        void spawnEnhance() {
            dSlot = spawnItem(left, new ItemStack(Material.BARRIER)); // empty slot
            name(dSlot, "아이템 넣기");
            iSlot = spawnClick(dSlot, "enh_slot");
            dButton = spawnItem(right, new ItemStack(Material.NETHER_STAR));
            name(dButton, "강화");
            iButton = spawnClick(dButton, "enh_button");
        }

        void updatePositions() {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Vector rightVec = dir.clone().crossProduct(new Vector(0,1,0)).normalize();
            Vector up = rightVec.clone().crossProduct(dir).normalize();

            // base point
            Vector base = dir.clone().multiply(baseOffset.getZ())
                    .add(up.clone().multiply(baseOffset.getY()))
                    .add(rightVec.clone().multiply(baseOffset.getX()));
            Location baseLoc = eye.clone().add(base);

            Map<Entity, Vector> targets = new LinkedHashMap<>();

            if (dJump != null) targets.put(dJump, left);
            if (dRegen != null) targets.put(dRegen, mid);
            if (dHaste != null) targets.put(dHaste, right);
            if (dSlot != null) targets.put(dSlot, left);
            if (dButton != null) targets.put(dButton, right);

            for (Map.Entry<Entity, Vector> en : targets.entrySet()) {
                Vector off = en.getValue();
                Vector worldOff = rightVec.clone().multiply(off.getX())
                        .add(up.clone().multiply(off.getY()))
                        .add(dir.clone().multiply(off.getZ()));
                Location loc = baseLoc.clone().add(worldOff);
                Entity e = en.getKey();
                if (!e.isDead()) {
                    e.teleport(loc);
                }
            }
        }

        ItemDisplay spawnItem(Vector rel, ItemStack stack) {
            Location eye = p.getEyeLocation();
            ItemDisplay d = (ItemDisplay) w.spawnEntity(eye, EntityType.ITEM_DISPLAY);
            d.setItemStack(stack);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15,15));
            d.setInterpolationDuration(1);
            d.setCustomNameVisible(false);
            spawned.add(d);
            return d;
        }

        void name(ItemDisplay d, String text) {
            d.customName(Component.text(text));
            d.setCustomNameVisible(true);
        }

        Interaction spawnClick(ItemDisplay ref, String type) {
            Interaction inter = (Interaction) w.spawnEntity(ref.getLocation(), EntityType.INTERACTION);
            inter.setInteractionWidth(0.7f);
            inter.setInteractionHeight(0.7f);
            inter.setResponsive(true);
            PersistentDataContainer pdc = inter.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, type);
            spawned.add(inter);
            return inter;
        }

        void levelUpCommon(int current, String name) {
            if (current >= 10) {
                p.sendMessage(Component.text(name + "은(는) 이미 최대 레벨(10)입니다."));
                return;
            }
            int totalExp = p.getTotalExperience();
            if (totalExp < 10) {
                p.sendMessage(Component.text("경험치가 부족합니다. (필요: 10)"));
                return;
            }
            p.giveExp(-10);
        }

        void levelUpJump() {
            levelUpCommon(jumpLv, "점프 강화");
            if (jumpLv >= 10) return;
            jumpLv++;
            // apply jump boost
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP, 20*30, Math.max(0, jumpLv-1), true, false, false));
            // side effect: slowness 1~3, not lower than previous level's (approx by using jumpLv)
            int min = Math.min(3, Math.max(1, (jumpLv+1)/4)); // grows with level
            int amp = new Random().nextInt(3 - (min-1)) + (min); // between min..3
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 20*5, amp-1, true, true, true));
            p.sendMessage(Component.text("점프 강화 레벨 " + jumpLv + " (구속 " + amp + ")"));
        }

        void levelUpRegen() {
            levelUpCommon(regenLv, "재생");
            if (regenLv >= 10) return;
            regenLv++;
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 20*30, Math.max(0, regenLv-1), true, false, false));
            // side: max health cut, up to 2 hearts (4.0)
            double maxBase = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            double cut = Math.min(4.0, 0.4 * regenLv); // at lv10 -> 4.0
            p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0 - cut);
            if (p.getHealth() > p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue()) {
                p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
            }
            p.sendMessage(Component.text("재생 레벨 " + regenLv + " (최대체력 -" + cut + ")"));
        }

        void levelUpHaste() {
            levelUpCommon(hasteLv, "성급함");
            if (hasteLv >= 10) return;
            hasteLv++;
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HASTE, 20*30, Math.max(0, hasteLv-1), true, false, false));
            // side: hunger up to -6 (3 bars)
            int hunger = Math.min(6, hasteLv); // simple scale
            p.setFoodLevel(Math.max(0, p.getFoodLevel() - hunger));
            p.sendMessage(Component.text("성급함 레벨 " + hasteLv + " (허기 -" + hunger + ")"));
        }

        // ===== Enchant UI =====
        void toggleEnchantSlot() {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (storedEnchant == null) {
                if (hand == null || hand.getType().isAir()) {
                    p.sendMessage(Component.text("손에 넣을 아이템을 들고 클릭하세요."));
                    return;
                }
                storedEnchant = hand.clone();
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                dSlot.setItemStack(storedEnchant);
                name(dSlot, "슬롯 (클릭 시 꺼내기)");
            } else {
                // return to hand
                if (p.getInventory().getItemInMainHand() == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                    p.getInventory().setItemInMainHand(storedEnchant);
                    storedEnchant = null;
                    dSlot.setItemStack(new ItemStack(Material.BARRIER));
                    name(dSlot, "아이템 넣기");
                } else {
                    p.getWorld().dropItemNaturally(p.getLocation(), storedEnchant);
                    storedEnchant = null;
                    dSlot.setItemStack(new ItemStack(Material.BARRIER));
                    name(dSlot, "아이템 넣기");
                }
            }
        }

        void clickEnchantButton() {
            if (storedEnchant == null) {
                p.sendMessage(Component.text("슬롯에 아이템이 없습니다."));
                return;
            }
            if (p.getLevel() < 1) {
                p.sendMessage(Component.text("레벨이 부족합니다. (필요: 1레벨)"));
                return;
            }
            p.setLevel(p.getLevel() - 1);
            // random applicable enchant
            List<Enchantment> possible = new ArrayList<>();
            for (Enchantment ench : Enchantment.values()) {
                if (ench.canEnchantItem(storedEnchant)) {
                    possible.add(ench);
                }
            }
            if (possible.isEmpty()) {
                p.sendMessage(Component.text("이 아이템에는 인첸트를 붙일 수 없습니다."));
                return;
            }
            Enchantment pick = possible.get(new Random().nextInt(possible.size()));
            ItemMeta meta = storedEnchant.getItemMeta();
            int cur = meta.hasEnchant(pick) ? meta.getEnchantLevel(pick) : 0;
            int next = Math.min(cur + 1, pick.getMaxLevel());
            meta.addEnchant(pick, next, true);
            storedEnchant.setItemMeta(meta);
            p.sendMessage(Component.text("인첸트 성공: " + pick.getKey().getKey() + " " + next));
        }

        // ===== Enhance UI =====
        int getStarCount(ItemStack it) {
            if (it == null || !it.hasItemMeta()) return 0;
            ItemMeta m = it.getItemMeta();
            String n = m.hasDisplayName() ? Component.text().content("").build().toString() : null;
            // safer: count stars in display name if exists
            if (m.hasDisplayName()) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m.displayName());
                long c = plain.chars().filter(ch -> ch == '☆').count();
                return (int) c;
            }
            return 0;
        }

        void setStarCount(ItemStack it, int stars) {
            ItemMeta m = it.getItemMeta();
            String starStr = " ".repeat(Math.max(0, 0)) + "☆".repeat(Math.max(0, stars));
            Component base = m.hasDisplayName() ? m.displayName() : Component.text(it.getType().translationKey());
            m.displayName(Component.text(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(base) + " " + starStr));
            it.setItemMeta(m);
        }

        void toggleEnhanceSlot() {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (storedEnhance == null) {
                if (hand == null || hand.getType().isAir()) {
                    p.sendMessage(Component.text("손에 넣을 아이템을 들고 클릭하세요."));
                    return;
                }
                storedEnhance = hand.clone();
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                dSlot.setItemStack(storedEnhance);
                name(dSlot, "슬롯 (클릭 시 꺼내기)");
            } else {
                if (p.getInventory().getItemInMainHand() == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                    p.getInventory().setItemInMainHand(storedEnhance);
                } else {
                    p.getWorld().dropItemNaturally(p.getLocation(), storedEnhance);
                }
                storedEnhance = null;
                dSlot.setItemStack(new ItemStack(Material.BARRIER));
                name(dSlot, "아이템 넣기");
            }
        }

        void clickEnhanceButton() {
            if (storedEnhance == null) {
                p.sendMessage(Component.text("슬롯에 아이템이 없습니다."));
                return;
            }
            int stars = getStarCount(storedEnhance);
            if (stars >= 10) {
                p.sendMessage(Component.text("이미 10강입니다."));
                return;
            }
            int next = stars + 1;
            int chance = successChance(next); // in percent
            int roll = new Random().nextInt(100) + 1;
            if (roll <= chance) {
                setStarCount(storedEnhance, next);
                p.sendMessage(Component.text(next + "강 성공! (확률 " + chance + "%)"));
                dSlot.setItemStack(storedEnhance);
            } else {
                p.sendMessage(Component.text(next + "강 실패... (확률 " + chance + "%)"));
            }
        }

        int successChance(int targetLevel) {
            // 1->100, 2->80, 3->60, 4->45, 5->35, 6->25, 7->18, 8->12, 9->8, 10->5
            return switch (targetLevel) {
                case 1 -> 100;
                case 2 -> 80;
                case 3 -> 60;
                case 4 -> 45;
                case 5 -> 35;
                case 6 -> 25;
                case 7 -> 18;
                case 8 -> 12;
                case 9 -> 8;
                case 10 -> 5;
                default -> 0;
            };
        }

        void despawnAll() {
            // return stored items
            if (storedEnchant != null) {
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(storedEnchant);
                if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), storedEnchant);
                storedEnchant = null;
            }
            if (storedEnhance != null) {
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(storedEnhance);
                if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), storedEnhance);
                storedEnhance = null;
            }
            for (Entity e : spawned) {
                if (!e.isDead()) e.remove();
            }
            spawned.clear();
        }
    }
}
