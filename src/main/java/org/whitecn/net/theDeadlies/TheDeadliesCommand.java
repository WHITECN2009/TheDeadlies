package org.whitecn.net.theDeadlies;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

import static org.whitecn.net.theDeadlies.Vars.COMMAND_USAGE;
import static org.whitecn.net.theDeadlies.Vars.PREFIX;

public class TheDeadliesCommand implements CommandExecutor, TabCompleter {
    private final TheDeadlies plugin;
    private final HashMap<UUID, InviteRequest> invites = new HashMap<>();
    private final HashMap<UUID, GameRoom> gameRooms = new HashMap<>();
    private final HashMap<UUID, JoinRequest> joinRequests = new HashMap<>();

    public TheDeadliesCommand(TheDeadlies plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§c该命令只能被玩家执行");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gpc")) {
            player.sendMessage(COMMAND_USAGE);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("invite")) {
            GameRoom inviterRoom = getPlayerGameRoom(player.getUniqueId());
            if (inviterRoom != null
                    && (inviterRoom.started
                    || !inviterRoom.owner.equals(player.getUniqueId()))) {
                player.sendMessage(PREFIX + "§c你当前不能发起房间邀请");
                return true;
            }
            Set<UUID> invitedPlayers = new HashSet<>();
            for (int i = 1; i < args.length; i++) {
                Player target = Bukkit.getPlayer(args[i]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(PREFIX + "§c找不到在线玩家：§f" + args[i]);
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(PREFIX + "§c你不能邀请自己");
                    return true;
                }
                if (isInGameRoom(target.getUniqueId())) {
                    player.sendMessage(PREFIX + "§c玩家 §f" + target.getName()
                            + " §c已经在游戏房间中，不能邀请");
                    return true;
                }
                if (!invitedPlayers.add(target.getUniqueId())) {
                    player.sendMessage(PREFIX + "§e玩家 §f"
                            + target.getName()
                            + " §e已经在邀请列表中");
                }
            }
            if (invitedPlayers.isEmpty()) {
                player.sendMessage(PREFIX + "§c没有有效的受邀玩家");
                return true;
            }
            InviteRequest request = new InviteRequest(player.getUniqueId(), invitedPlayers);
            UUID inviteId = UUID.randomUUID();
            invites.put(inviteId, request);
            for (UUID uuid : invitedPlayers) {
                Player target = Bukkit.getPlayer(uuid);
                if (target == null || !target.isOnline()) {
                    request.pendingPlayers.remove(uuid);
                    continue;
                }
                TextComponent message = new TextComponent();
                message.addExtra(PREFIX + "§b玩家§a " + player.getName() + " §b向您发送了赎罪之旅游戏请求\n");
                message.addExtra(PREFIX + "§b一同受邀的还有：§a ");
                boolean first = true;
                for (UUID u : invitedPlayers) {
                    if (u.equals(uuid)) {
                        continue;
                    }
                    Player invitedPlayer = Bukkit.getPlayer(u);
                    if (invitedPlayer != null) {
                        if (!first) {
                            message.addExtra("§f, ");
                        }
                        message.addExtra("§a" + invitedPlayer.getName());
                        first = false;
                    }
                }
                message.addExtra("\n               ");
                TextComponent agree = new TextComponent("§a[同意]");
                agree.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/thedeadlies invite-accept " + inviteId
                ));
                agree.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("点击同意邀请")
                                .color(ChatColor.GREEN)
                                .create()
                ));
                TextComponent disagree = new TextComponent("§c[拒绝]");
                disagree.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/thedeadlies invite-deny " + inviteId
                ));
                disagree.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("点击拒绝邀请")
                                .color(ChatColor.RED)
                                .create()
                ));
                message.addExtra(agree);
                message.addExtra("    ");
                message.addExtra(disagree);
                target.spigot().sendMessage(message);
            }
            if (request.pendingPlayers.isEmpty()) {
                invites.remove(inviteId);
                player.sendMessage(PREFIX + "§c没有可用的受邀玩家");
                return true;
            }
            player.sendMessage(PREFIX + "§a邀请已发送，等待受邀玩家确认");
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        InviteRequest current = invites.remove(inviteId);
                        if (current == null) {
                            return;
                        }
                        Player inviterPlayer = Bukkit.getPlayer(current.inviter);
                        if (!current.pendingPlayers.isEmpty()) {
                            if (inviterPlayer != null && inviterPlayer.isOnline()) {
                                inviterPlayer.sendMessage(PREFIX + "§e以下玩家在15秒内没有回应 " + "邀请已自动拒绝：");
                                for (UUID uuid : current.pendingPlayers) {
                                    Player pending = Bukkit.getPlayer(uuid);
                                    if (pending != null) {
                                        inviterPlayer.sendMessage(PREFIX + "§7- §f" + pending.getName());
                                    }
                                }
                            }

                            for (UUID uuid : current.pendingPlayers) {
                                Player pending = Bukkit.getPlayer(uuid);
                                if (pending != null && pending.isOnline()) {
                                    pending.sendMessage(PREFIX + "§c邀请已超时 " + "系统自动拒绝了本次邀请");
                                }
                            }
                            completeInviteRequest(inviteId, current);
                        } else {
                            if (inviterPlayer != null && inviterPlayer.isOnline()) {
                                inviterPlayer.sendMessage(PREFIX + "§a所有玩家都已经处理了邀请");
                            }
                            completeInviteRequest(inviteId, current);
                        }
                    },
                    20L * 15);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite-accept")) {
            UUID inviteId;
            try {
                inviteId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(PREFIX + "§c无效的邀请");
                return true;
            }
            InviteRequest request = invites.get(inviteId);
            if (request == null) {
                player.sendMessage(PREFIX + "§c该邀请已经过期");
                return true;
            }
            UUID uuid = player.getUniqueId();
            if (uuid.equals(request.inviter)) {
                player.sendMessage(PREFIX + "§c你是这次邀请的发起者");
                return true;
            }
            Player inviter = Bukkit.getPlayer(request.inviter);
            if (!request.pendingPlayers.remove(uuid)) {
                player.sendMessage(PREFIX + "§c你已经处理过这个邀请了");
                return true;
            }
            if (isInGameRoom(uuid)) {
                player.sendMessage(PREFIX + "§c你已经加入了其他游戏房间");
                if (inviter != null && inviter.isOnline()) {
                    inviter.sendMessage(PREFIX + "§c玩家 §f" + player.getName()
                            + " §c已加入其他房间，无法加入本局");
                }
                if (request.pendingPlayers.isEmpty()) {
                    completeInviteRequest(inviteId, request);
                }
                return true;
            }
            request.acceptedPlayers.add(uuid);
            player.sendMessage(PREFIX + "§a你同意了游戏邀请");
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(PREFIX + "§a玩家 §f" + player.getName() + " §a同意了你的游戏邀请");
            }
            if (request.pendingPlayers.isEmpty()) {
                completeInviteRequest(inviteId, request);
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite-deny")) {
            UUID inviteId;
            try {
                inviteId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(PREFIX + "§c无效的邀请");
                return true;
            }
            InviteRequest request = invites.get(inviteId);
            if (request == null) {
                player.sendMessage(PREFIX + "§c该邀请已经过期");
                return true;
            }
            UUID uuid = player.getUniqueId();
            if (uuid.equals(request.inviter)) {
                player.sendMessage(PREFIX + "§c你是这次邀请的发起者");
                return true;
            }
            if (!request.pendingPlayers.remove(uuid)) {
                player.sendMessage(PREFIX + "§c你已经处理过这个邀请了");
                return true;
            }
            player.sendMessage(PREFIX + "§c你拒绝了游戏邀请");
            Player inviter = Bukkit.getPlayer(request.inviter);
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(PREFIX + "§c玩家 §f" + player.getName() + " §c拒绝了你的游戏邀请");
            }
            if (request.pendingPlayers.isEmpty()) {
                completeInviteRequest(inviteId, request);
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(PREFIX + "§c找不到玩家：§f" + args[1]);
                return true;
            }
            GameRoom room = gameRooms.get(target.getUniqueId());
            if (room == null || !room.valid) {
                player.sendMessage(PREFIX + "§c该玩家没有有效的游戏房间");
                return true;
            }
            if (room.players.contains(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§e你已经在这个游戏房间中");
                return true;
            }
            if (isInGameRoom(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c你已经在其他游戏房间中");
                return true;
            }
            if (room.started) {
                player.sendMessage(PREFIX + "§c该游戏已经开始，无法申请加入");
                return true;
            }
            UUID requestId = UUID.randomUUID();
            JoinRequest request = new JoinRequest(
                    requestId,
                    player.getUniqueId(),
                    target.getUniqueId()
            );
            joinRequests.put(requestId, request);
            TextComponent message = new TextComponent();
            message.addExtra(PREFIX + "§b玩家§a " + player.getName() + " §b申请加入你的赎罪之旅游戏\n");
            message.addExtra(PREFIX + "§b有效期：§f15秒\n               ");
            TextComponent agree = new TextComponent("§a[同意]");
            agree.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/thedeadlies joingame-accept " + requestId
            ));
            agree.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("点击允许玩家加入")
                            .color(ChatColor.GREEN)
                            .create()
            ));
            TextComponent disagree = new TextComponent("§c[拒绝]");
            disagree.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/thedeadlies joingame-deny " + requestId
            ));
            disagree.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("点击拒绝玩家加入")
                            .color(ChatColor.RED)
                            .create()
            ));
            message.addExtra(agree);
            message.addExtra("    ");
            message.addExtra(disagree);
            target.spigot().sendMessage(message);
            player.sendMessage(PREFIX + "§a已向 §f" + target.getName() + " §a发送加入申请");
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        JoinRequest current = joinRequests.remove(requestId);
                        if (current == null) {
                            return;
                        }
                        Player applicant = Bukkit.getPlayer(current.applicant);
                        if (applicant != null && applicant.isOnline()) {
                            applicant.sendMessage(PREFIX + "§c加入申请已超时");
                        }
                    },
                    20L * 15);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame-accept")) {
            UUID requestId;
            try {
                requestId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(PREFIX + "§c无效的加入申请");
                return true;
            }
            JoinRequest request = joinRequests.remove(requestId);
            if (request == null) {
                player.sendMessage(PREFIX + "§c该加入申请已经过期");
                return true;
            }
            if (!request.owner.equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c你不是该游戏房间的房主");
                return true;
            }
            GameRoom room = gameRooms.get(player.getUniqueId());
            if (room == null || !room.valid) {
                player.sendMessage(PREFIX + "§c你的游戏房间已经无效");
                return true;
            }
            if (room.started) {
                player.sendMessage(PREFIX + "§c游戏已经开始，无法加入");
                return true;
            }
            Player applicant = Bukkit.getPlayer(request.applicant);
            if (applicant == null || !applicant.isOnline()) {
                player.sendMessage(PREFIX + "§c申请玩家已经离线");
                return true;
            }
            if (isInGameRoom(applicant.getUniqueId())) {
                applicant.sendMessage(PREFIX + "§c你已经在其他游戏房间中");
                return true;
            }
            room.players.add(applicant.getUniqueId());
            player.sendMessage(PREFIX + "§a已同意玩家 §f" + applicant.getName() + " §a加入游戏房间"
            );
            applicant.sendMessage(PREFIX + "§a房主 §f" + player.getName() + " §a同意了你的加入申请");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame-deny")) {
            UUID requestId;
            try {
                requestId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(PREFIX + "§c无效的加入申请");
                return true;
            }
            JoinRequest request = joinRequests.remove(requestId);
            if (request == null) {
                player.sendMessage(PREFIX + "§c该加入申请已经过期");
                return true;
            }
            if (!request.owner.equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c你不是该游戏房间的房主");
                return true;
            }
            Player applicant = Bukkit.getPlayer(request.applicant);
            player.sendMessage(PREFIX + "§c你拒绝了加入申请");
            if (applicant != null && applicant.isOnline()) {
                applicant.sendMessage(PREFIX + "§c房主 §f" + player.getName() + " §c拒绝了你的加入申请");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("quitgame")) {
            quitGame(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("endgame")) {
            endGame(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("choose")) {
            GameRoom room = getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null
                    || !room.getProcedure().resolveChoice(
                    player.getUniqueId(),
                    args[1]
            )) {
                player.sendMessage(PREFIX + "§c当前没有等待你处理的游戏选择");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("play")) {
            GameRoom room = getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null) {
                player.sendMessage(PREFIX + "§c你当前没有正在进行的游戏");
                return true;
            }
            try {
                List<GameProcedureControl.CardSelection> selections = new ArrayList<>();
                for (String selected : args[1].split(",")) {
                    String[] parts = selected.split(":");
                    if (parts.length != 2) {
                        throw new IllegalArgumentException();
                    }
                    selections.add(new GameProcedureControl.CardSelection(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])
                    ));
                }
                UUID target = args.length >= 3
                        ? UUID.fromString(args[2])
                        : null;
                int corruptionSuit = args.length >= 4
                        ? Integer.parseInt(args[3])
                        : -1;
                if (!room.getProcedure().playCards(
                        player.getUniqueId(),
                        selections,
                        target,
                        corruptionSuit
                )) {
                    player.sendMessage(PREFIX + "§c出牌不合法，或当前不能出牌");
                }
            } catch (IllegalArgumentException exception) {
                player.sendMessage(PREFIX
                        + "§c用法：/thedeadlies play 花色:位置[,花色:位置] [目标UUID] [腐化花色]");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("startgame")) {
            startGame(player.getUniqueId());
            return true;
        }

        player.sendMessage(PREFIX + "§c未知的子命令");
        return true;
    }

    private void createGameRoom(UUID owner, Set<UUID> invitedPlayers) {
        if (gameRooms.containsKey(owner)) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + "§e你已经有一个有效的游戏房间");
            }

            return;
        }
        GameRoom room = new GameRoom(plugin, owner);
        room.players.add(owner);
        room.players.addAll(invitedPlayers);
        gameRooms.put(owner, room);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            ownerPlayer.sendMessage(PREFIX + "§a游戏房间创建成功");
        }
        for (UUID uuid : room.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + "§a你已经加入赎罪之旅游戏房间");
            }
        }
    }

    private void completeInviteRequest(UUID inviteId, InviteRequest request) {
        invites.remove(inviteId);
        Player inviter = Bukkit.getPlayer(request.inviter);
        if (request.acceptedPlayers.isEmpty()) {
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(PREFIX + "§e没有玩家接受本次邀请");
            }
            return;
        }

        GameRoom inviterRoom = getPlayerGameRoom(request.inviter);
        if (inviterRoom != null
                && (inviterRoom.started
                || !inviterRoom.owner.equals(request.inviter))) {
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(PREFIX + "§c你已加入其他房间或当前游戏已经开始，"
                        + "无法完成邀请");
            }
            return;
        }

        GameRoom existingRoom = gameRooms.get(request.inviter);
        if (existingRoom != null && existingRoom.valid && !existingRoom.started) {
            for (UUID accepted : request.acceptedPlayers) {
                if (!isInGameRoom(accepted)) {
                    existingRoom.players.add(accepted);
                    Player member = Bukkit.getPlayer(accepted);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(PREFIX + "§a你已加入赎罪之旅游戏房间");
                    }
                }
            }
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(PREFIX + "§a已将接受邀请的玩家加入你的游戏房间");
            }
            return;
        }

        createGameRoom(request.inviter, request.acceptedPlayers);
    }

    private void quitGame(Player player) {
        GameRoom room = getPlayerGameRoom(player.getUniqueId());
        if (room == null) {
            player.sendMessage(PREFIX + "§c你当前没有加入任何游戏房间");
            return;
        }
        if (!room.started) {
            room.players.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "§a你已退出游戏房间");
            Player owner = Bukkit.getPlayer(room.owner);
            if (owner != null && owner.isOnline() && !room.owner.equals(player.getUniqueId())) {
                owner.sendMessage(PREFIX + "§e玩家 §f" + player.getName() + " §e退出了游戏房间");
            }

            if (room.owner.equals(player.getUniqueId())) {
                room.valid = false;
                gameRooms.remove(room.owner);
                for (UUID uuid : room.players) {
                    Player member = Bukkit.getPlayer(uuid);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(PREFIX + "§c房主退出了房间，游戏房间已解散");
                    }
                }
                room.players.clear();
                return;
            }
            if (room.players.isEmpty()) {
                room.valid = false;
                gameRooms.remove(room.owner);
            }
            return;
        }

        if (room.owner.equals(player.getUniqueId())) {
            dissolveGameRoom(room, "房主退出了房间，本局游戏已结束，游戏房间已解散");
            return;
        }

        room.players.remove(player.getUniqueId());
        player.sendMessage(PREFIX + "§e你已退出游戏，同时退出了游戏房间");
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(PREFIX + "§e玩家 §f" + player.getName() + " §e已退出游戏");
            }
        }

        if (room.players.isEmpty()) {
            room.valid = false;
            gameRooms.remove(room.owner);
        }
    }

    private void endGame(Player player) {
        GameRoom room = getPlayerGameRoom(player.getUniqueId());
        if (room == null || !room.started || room.procedure == null) {
            player.sendMessage(PREFIX + "§c你当前没有正在进行的游戏");
            return;
        }

        room.procedure.finishGame();
        player.sendMessage(PREFIX + "§a本局游戏已结束，房间仍然保留");
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()
                    && !uuid.equals(player.getUniqueId())) {
                member.sendMessage(PREFIX + "§c本局游戏已被 " + player.getName()
                        + " §c结束，房间仍然保留");
            }
        }
    }

    private void dissolveGameRoom(GameRoom room, String reason) {
        room.valid = false;
        if (room.procedure != null) {
            room.procedure.finishGame();
        }

        for (UUID uuid : new HashSet<>(room.players)) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(PREFIX + "§c" + reason);
            }
        }

        room.players.clear();
        gameRooms.remove(room.owner);
    }

    private void startGame(UUID owner) {
        GameRoom room = gameRooms.get(owner);
        if (room == null || !room.valid || room.started) {
            return;
        }
        if (room.players.size() < 2) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + "§c至少需要两名玩家才能开始游戏");
            }
            return;
        }
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || !member.isOnline()) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null && player.isOnline()) {
                    player.sendMessage(PREFIX + "§c有房间成员已经离线，无法开始游戏");
                }
                return;
            }
        }
        room.started = true;
        ArrayList<String> playersIn = new ArrayList<>();
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            playersIn.add(member.getName());
        }
        String names = String.join(", ", playersIn);
        for (UUID uuid : room.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + "§a游戏开始，本局玩家：§e" + names);
            }
        }
        try {
            room.startProcedure();
        } catch (IllegalStateException exception) {
            room.resetAfterGame();
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                player.sendMessage(PREFIX + "§c无法启动游戏：" + exception.getMessage());
            }
        }
    }

    private boolean isInGameRoom(UUID uuid) {
        return getPlayerGameRoom(uuid) != null;
    }

    private GameRoom getPlayerGameRoom(UUID uuid) {
        for (GameRoom room : gameRooms.values()) {
            if (room.valid && room.players.contains(uuid)) {
                return room;
            }
        }
        return null;
    }

    private static class InviteRequest {
        private final UUID inviter;
        private final Set<UUID> invitedPlayers = new HashSet<>();
        private final Set<UUID> pendingPlayers = new HashSet<>();
        private final Set<UUID> acceptedPlayers = new HashSet<>();
        private InviteRequest(UUID inviter, Set<UUID> invitedPlayers) {
            this.inviter = inviter;
            this.invitedPlayers.addAll(invitedPlayers);
            this.pendingPlayers.addAll(invitedPlayers);
        }
    }

    public static class GameRoom {
        private final TheDeadlies plugin;
        private final UUID owner;
        public final Set<UUID> players = new HashSet<>();
        private boolean started = false;
        private boolean valid = true;
        private GameProcedureControl procedure;
        private GameRoom(TheDeadlies plugin, UUID owner) {
            this.plugin = plugin;
            this.owner = owner;
        }
        public GameProcedureControl getProcedure() {
            return procedure;
        }
        public void startProcedure() {
            if (procedure != null) {
                throw new IllegalStateException("游戏流程已经启动");
            }
            procedure = new GameProcedureControl(
                    plugin,
                    players,
                    this::resetAfterGame
            );
            procedure.start();
        }
        private void resetAfterGame() {
            procedure = null;
            started = false;
        }
        public boolean isStarted() {
            return started;
        }
        public boolean isValid() {
            return valid;
        }
    }

    private static class JoinRequest {
        private final UUID id;
        private final UUID applicant;
        private final UUID owner;
        private JoinRequest(UUID id, UUID applicant, UUID owner) {
            this.id = id;
            this.applicant = applicant;
            this.owner = owner;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList(
                    "invite",
                    "joingame",
                    "quitgame",
                    "startgame",
                    "endgame"
            );
        }
        return Collections.emptyList();
    }
}