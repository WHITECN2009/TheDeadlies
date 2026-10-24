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

import static org.whitecn.net.theDeadlies.Vars.PREFIX;

public class TheDeadliesCommand implements CommandExecutor, TabCompleter {
    private final TheDeadlies plugin;
    private final HashMap<UUID, InviteRequest> invites = new HashMap<>();
    private final GameRoomManager gameRoomManager;
    private final HashMap<UUID, JoinRequest> joinRequests = new HashMap<>();

    public TheDeadliesCommand(TheDeadlies plugin) {
        this.plugin = plugin;
        this.gameRoomManager = new GameRoomManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, PREFIX + I18n.get(Messages.COMMAND_CAN_ONLY));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gpc")) {
            sendMessage(player, PREFIX + I18n.get(Messages.COMMAND_USAGE));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("invite")) {
            GameRoom inviterRoom = gameRoomManager.getPlayerGameRoom(player.getUniqueId());
            if (inviterRoom != null
                    && (inviterRoom.started
                    || !inviterRoom.owner.equals(player.getUniqueId()))) {
                sendMessage(player, PREFIX + I18n.get(Messages.CANNOT_SEND_ROOM));
                return true;
            }
            Set<UUID> invitedPlayers = new HashSet<>();
            for (int i = 1; i < args.length; i++) {
                Player target = Bukkit.getPlayer(args[i]);
                if (target == null || !target.isOnline()) {
                    sendMessage(player, PREFIX + I18n.get(Messages.ONLINE_PLAYER_NOT) + args[i]);
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    sendMessage(player, PREFIX + I18n.get(Messages.CANNOT_INVITE_YOURSELF));
                    return true;
                }
                if (gameRoomManager.isInGameRoom(target.getUniqueId())) {
                    sendMessage(player, PREFIX + I18n.get(Messages.PLAYER_2) + target.getName()
                            + I18n.get(Messages.ALREADY_GAME_ROOM));
                    return true;
                }
                if (!invitedPlayers.add(target.getUniqueId())) {
                    sendMessage(player, PREFIX + I18n.get(Messages.PLAYER)
                            + target.getName()
                            + I18n.get(Messages.ALREADY_INVITATION_LIST));
                }
            }
            if (invitedPlayers.isEmpty()) {
                sendMessage(player, PREFIX + I18n.get(Messages.THERE_NO_VALID));
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
                message.addExtra(PREFIX + I18n.get(Messages.PLAYER_3) + player.getName() + I18n.get(Messages.SENT_THEDEADLIES_GAME));
                message.addExtra(PREFIX + I18n.get(Messages.ALSO_INVITED));
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
                message.addExtra(actionButton(
                        Messages.ACCEPT,
                        "/thedeadlies invite-accept " + inviteId,
                        Messages.CLICK_ACCEPT_INVITATION,
                        ChatColor.GREEN
                ));
                message.addExtra("    ");
                message.addExtra(actionButton(
                        Messages.DECLINE,
                        "/thedeadlies invite-deny " + inviteId,
                        Messages.CLICK_DECLINE_INVITATION,
                        ChatColor.RED
                ));
                target.spigot().sendMessage(message);
            }
            if (request.pendingPlayers.isEmpty()) {
                invites.remove(inviteId);
                sendMessage(player, PREFIX + I18n.get(Messages.THERE_NO_AVAILABLE));
                return true;
            }
            sendMessage(player, PREFIX + I18n.get(Messages.INVITATION_SENT_WAITING));
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
                                sendMessage(inviterPlayer, PREFIX + I18n.get(Messages.THESE_PLAYERS_DID) + I18n.get(Messages.INVITATION_AUTOMATICALLY_DECLINED));
                                for (UUID uuid : current.pendingPlayers) {
                                    Player pending = Bukkit.getPlayer(uuid);
                                    if (pending != null) {
                                        sendMessage(inviterPlayer, PREFIX + "§7- §f" + pending.getName());
                                    }
                                }
                            }

                            for (UUID uuid : current.pendingPlayers) {
                                Player pending = Bukkit.getPlayer(uuid);
                                if (pending != null && pending.isOnline()) {
                                    sendMessage(pending, PREFIX + I18n.get(Messages.INVITATION_TIMED_OUT) + I18n.get(Messages.AUTOMATICALLY_DECLINED));
                                }
                            }
                        } else {
                            if (inviterPlayer != null && inviterPlayer.isOnline()) {
                                sendMessage(inviterPlayer, PREFIX + I18n.get(Messages.ALL_INVITED_PLAYERS));
                            }
                        }
                        completeInviteRequest(inviteId, current);
                    },
                    20L * 15);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite-accept")) {
            UUID inviteId = parseRequestId(player, args[1], Messages.INVALID_INVITATION);
            if (inviteId == null) {
                return true;
            }
            InviteRequest request = invites.get(inviteId);
            if (request == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.INVITATION_HAS_EXPIRED));
                return true;
            }
            UUID uuid = player.getUniqueId();
            if (uuid.equals(request.inviter)) {
                sendMessage(player, PREFIX + I18n.get(Messages.SENT_INVITATION));
                return true;
            }
            Player inviter = Bukkit.getPlayer(request.inviter);
            if (!request.pendingPlayers.remove(uuid)) {
                sendMessage(player, PREFIX + I18n.get(Messages.HAVE_ALREADY_RESPONDED));
                return true;
            }
            if (gameRoomManager.isInGameRoom(uuid)) {
                sendMessage(player, PREFIX + I18n.get(Messages.ALREADY_ANOTHER_GAME));
                if (inviter != null && inviter.isOnline()) {
                    sendMessage(inviter, PREFIX + I18n.get(Messages.PLAYER_2) + player.getName()
                            + I18n.get(Messages.HAS_JOINED_ANOTHER));
                }
                if (request.pendingPlayers.isEmpty()) {
                    completeInviteRequest(inviteId, request);
                }
                return true;
            }
            request.acceptedPlayers.add(uuid);
            sendMessage(player, PREFIX + I18n.get(Messages.ACCEPTED_GAME_INVITATION));
            if (inviter != null && inviter.isOnline()) {
                sendMessage(inviter, PREFIX + I18n.get(Messages.PLAYER_4) + player.getName() + I18n.get(Messages.ACCEPTED_GAME_INVITATION_2));
            }
            if (request.pendingPlayers.isEmpty()) {
                completeInviteRequest(inviteId, request);
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite-deny")) {
            UUID inviteId = parseRequestId(player, args[1], Messages.INVALID_INVITATION);
            if (inviteId == null) {
                return true;
            }
            InviteRequest request = invites.get(inviteId);
            if (request == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.INVITATION_HAS_EXPIRED));
                return true;
            }
            UUID uuid = player.getUniqueId();
            if (uuid.equals(request.inviter)) {
                sendMessage(player, PREFIX + I18n.get(Messages.SENT_INVITATION));
                return true;
            }
            if (!request.pendingPlayers.remove(uuid)) {
                sendMessage(player, PREFIX + I18n.get(Messages.HAVE_ALREADY_RESPONDED));
                return true;
            }
            sendMessage(player, PREFIX + I18n.get(Messages.DECLINED_GAME_INVITATION));
            Player inviter = Bukkit.getPlayer(request.inviter);
            if (inviter != null && inviter.isOnline()) {
                sendMessage(inviter, PREFIX + I18n.get(Messages.PLAYER_2) + player.getName() + I18n.get(Messages.DECLINED_GAME_INVITATION_2));
            }
            if (request.pendingPlayers.isEmpty()) {
                completeInviteRequest(inviteId, request);
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.PLAYER_NOT_FOUND) + args[1]);
                return true;
            }
            GameRoom room = gameRoomManager.getOwnedRoom(target.getUniqueId());
            if (room == null || !room.valid) {
                sendMessage(player, PREFIX + I18n.get(Messages.THAT_PLAYER_HAS));
                return true;
            }
            if (room.players.contains(player.getUniqueId())) {
                sendMessage(player, PREFIX + I18n.get(Messages.ALREADY_GAME_ROOM_2));
                return true;
            }
            if (gameRoomManager.isInGameRoom(player.getUniqueId())) {
                sendMessage(player, PREFIX + I18n.get(Messages.ALREADY_ANOTHER_GAME_2));
                return true;
            }
            if (room.started) {
                sendMessage(player, PREFIX + I18n.get(Messages.GAME_HAS_STARTED));
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
            message.addExtra(PREFIX + I18n.get(Messages.PLAYER_3) + player.getName() + I18n.get(Messages.REQUESTED_JOIN_THEDEADLIES));
            message.addExtra(PREFIX + I18n.get(Messages.EXPIRES_15_SECONDS));
            message.addExtra(actionButton(
                    Messages.ACCEPT,
                    "/thedeadlies joingame-accept " + requestId,
                    Messages.CLICK_ACCEPT_JOIN,
                    ChatColor.GREEN
            ));
            message.addExtra("    ");
            message.addExtra(actionButton(
                    Messages.DECLINE,
                    "/thedeadlies joingame-deny " + requestId,
                    Messages.CLICK_DECLINE_JOIN,
                    ChatColor.RED
            ));
            target.spigot().sendMessage(message);
            sendMessage(player, PREFIX + I18n.get(Messages.JOIN_REQUEST_SENT) + target.getName() + I18n.get(Messages.MESSAGE_050));
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        JoinRequest current = joinRequests.remove(requestId);
                        if (current == null) {
                            return;
                        }
                        Player applicant = Bukkit.getPlayer(current.applicant);
                        if (applicant != null && applicant.isOnline()) {
                            sendMessage(applicant, PREFIX + I18n.get(Messages.JOIN_REQUEST_TIMED));
                        }
                    },
                    20L * 15);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame-accept")) {
            UUID requestId = parseRequestId(player, args[1], Messages.INVALID_JOIN_REQUEST);
            if (requestId == null) {
                return true;
            }
            JoinRequest request = joinRequests.remove(requestId);
            if (request == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.JOIN_REQUEST_HAS));
                return true;
            }
            if (!request.owner.equals(player.getUniqueId())) {
                sendMessage(player, PREFIX + I18n.get(Messages.NOT_OWN_GAME));
                return true;
            }
            GameRoom room = gameRoomManager.getOwnedRoom(player.getUniqueId());
            if (room == null || !room.valid) {
                sendMessage(player, PREFIX + I18n.get(Messages.GAME_ROOM_NO));
                return true;
            }
            if (room.started) {
                sendMessage(player, PREFIX + I18n.get(Messages.GAME_HAS_STARTED_2));
                return true;
            }
            Player applicant = Bukkit.getPlayer(request.applicant);
            if (applicant == null || !applicant.isOnline()) {
                sendMessage(player, PREFIX + I18n.get(Messages.APPLICANT_OFFLINE));
                return true;
            }
            if (gameRoomManager.isInGameRoom(applicant.getUniqueId())) {
                sendMessage(applicant, PREFIX + I18n.get(Messages.ALREADY_ANOTHER_GAME_2));
                return true;
            }
            room.players.add(applicant.getUniqueId());
            sendMessage(player, PREFIX + I18n.get(Messages.ACCEPTED) + applicant.getName() + I18n.get(Messages.GAME_ROOM)
            );
            sendMessage(applicant, PREFIX + I18n.get(Messages.ROOM_OWNER) + player.getName() + I18n.get(Messages.ACCEPTED_JOIN_REQUEST));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("joingame-deny")) {
            UUID requestId = parseRequestId(player, args[1], Messages.INVALID_JOIN_REQUEST);
            if (requestId == null) {
                return true;
            }
            JoinRequest request = joinRequests.remove(requestId);
            if (request == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.JOIN_REQUEST_HAS));
                return true;
            }
            if (!request.owner.equals(player.getUniqueId())) {
                sendMessage(player, PREFIX + I18n.get(Messages.NOT_OWN_GAME));
                return true;
            }
            Player applicant = Bukkit.getPlayer(request.applicant);
            sendMessage(player, PREFIX + I18n.get(Messages.DECLINED_JOIN_REQUEST));
            if (applicant != null && applicant.isOnline()) {
                sendMessage(applicant, PREFIX + I18n.get(Messages.ROOM_OWNER_2) + player.getName() + I18n.get(Messages.DECLINED_JOIN_REQUEST_2));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("quitgame")) {
            gameRoomManager.quitGame(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("endgame")) {
            gameRoomManager.endGame(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("choose")) {
            GameRoom room = gameRoomManager.getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null
                    || !room.getProcedure().resolveChoice(
                    player.getUniqueId(),
                    args[1]
            )) {
                sendMessage(player, PREFIX + I18n.get(Messages.THERE_NO_PENDING));
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            GameRoom room = gameRoomManager.getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.NOT_ACTIVE_GAME));
                return true;
            }
            try {
                String[] parts = args[1].split(":");
                if (parts.length != 2 || !room.getProcedure().toggleCardSelection(
                        player.getUniqueId(),
                        new GameProcedureControl.CardSelection(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1])
                        )
                )) {
                    sendMessage(player, PREFIX + I18n.get(Messages.INVALID_PLAY_NOT));
                }
            } catch (IllegalArgumentException exception) {
                sendMessage(player, PREFIX + I18n.get(Messages.USAGE_THEDEADLIES_PLAY));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("play-selected")) {
            if (args.length > 3) {
                sendMessage(player, PREFIX + I18n.get(Messages.USAGE_THEDEADLIES_PLAY));
                return true;
            }
            GameRoom room = gameRoomManager.getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.NOT_ACTIVE_GAME));
                return true;
            }
            try {
                UUID target = args.length >= 2
                        ? UUID.fromString(args[1])
                        : null;
                int corruptionSuit = args.length == 3
                        ? Integer.parseInt(args[2])
                        : -1;
                if (!room.getProcedure().playSelectedCards(
                        player.getUniqueId(),
                        target,
                        corruptionSuit
                )) {
                    sendMessage(player, PREFIX + I18n.get(Messages.INVALID_PLAY_NOT));
                }
            } catch (IllegalArgumentException exception) {
                sendMessage(player, PREFIX + I18n.get(Messages.USAGE_THEDEADLIES_PLAY));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("play")) {
            GameRoom room = gameRoomManager.getPlayerGameRoom(player.getUniqueId());
            if (room == null || room.getProcedure() == null) {
                sendMessage(player, PREFIX + I18n.get(Messages.NOT_ACTIVE_GAME));
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
                    sendMessage(player, PREFIX + I18n.get(Messages.INVALID_PLAY_NOT));
                }
            } catch (IllegalArgumentException exception) {
                sendMessage(player, PREFIX
                        + I18n.get(Messages.USAGE_THEDEADLIES_PLAY));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("startgame")) {
            gameRoomManager.startGame(player.getUniqueId());
            return true;
        }

        sendMessage(player, PREFIX + I18n.get(Messages.UNKNOWN_SUBCOMMAND));
        return true;
    }

    private void completeInviteRequest(UUID inviteId, InviteRequest request) {
        invites.remove(inviteId);
        gameRoomManager.completeInviteRequest(request.inviter, request.acceptedPlayers);
    }

    private void sendMessage(CommandSender recipient, String message) {
        recipient.sendMessage(message);
    }

    private UUID parseRequestId(Player player, String value, String invalidMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sendMessage(player, PREFIX + I18n.get(invalidMessage));
            return null;
        }
    }

    private TextComponent actionButton(
            String label,
            String command,
            String hoverText,
            ChatColor color
    ) {
        TextComponent button = new TextComponent(I18n.get(label));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(I18n.get(hoverText))
                        .color(color)
                        .create()
        ));
        return button;
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
        final UUID owner;
        public final Set<UUID> players = new HashSet<>();
        boolean started;
        boolean valid = true;
        GameProcedureControl procedure;

        GameRoom(TheDeadlies plugin, UUID owner) {
            this.plugin = plugin;
            this.owner = owner;
        }

        public GameProcedureControl getProcedure() {
            return procedure;
        }

        public void startProcedure() {
            if (procedure != null) {
                throw new IllegalStateException(I18n.get(Messages.GAME_PROCEDURE_HAS));
            }
            procedure = new GameProcedureControl(
                    plugin,
                    players,
                    this::resetAfterGame
            );
            procedure.start();
        }

        void resetAfterGame() {
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
                    "select",
                    "play-selected",
                    "quitgame",
                    "startgame",
                    "endgame"
            );
        }
        return Collections.emptyList();
    }
}