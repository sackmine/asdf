package me.yourname.statusfloatui;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StatusUIPlugin extends JavaPlugin {

    private FloatingUIManager ui;

    @Override
    public void onEnable() {
        ui = new FloatingUIManager(this);
        Bukkit.getPluginManager().registerEvents(ui, this);
        ui.startTicker();
        getLogger().info("StatusFloatUI enabled");
    }

    @Override
    public void onDisable() {
        if (ui != null) {
            ui.cleanupAll();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("플레이어만 사용 가능");
            return true;
        }
        if (!cmd.getName().equalsIgnoreCase("상태창")) return false;

        if (args.length == 0) {
            p.sendMessage("사용법: /상태창 [on|off] [스탯|인첸트|강화]");
            return true;
        }
        String sub = args[0];
        switch (sub) {
            case "on" -> {
                if (args.length < 2) {
                    p.sendMessage("예: /상태창 on 스탯");
                    return true;
                }
                String mode = args[1];
                switch (mode) {
                    case "스탯" -> ui.openStats(p);
                    case "인첸트" -> ui.openEnchant(p);
                    case "강화" -> ui.openEnhance(p);
                    default -> p.sendMessage("알 수 없는 모드: " + mode);
                }
            }
            case "off" -> ui.closeAllFor(p);
            case "인첸트" -> ui.openEnchant(p);
            case "강화" -> ui.openEnhance(p);
            default -> p.sendMessage("사용법: /상태창 [on|off] [스탯|인첸트|강화]");
        }
        return true;
    }
}
