package org.whitecn.net.theDeadlies;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.whitecn.net.theDeadlies.TheDeadliesCommand.GameRoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.whitecn.net.theDeadlies.Vars.PREFIX;

final class GameRoomManager {
    private final TheDeadlies plugin;
    private final HashMap<UUID, GameRoom> gameRooms = new HashMap<>();

    GameRoomManager(TheDeadlies plugin) {
        this.plugin = plugin;
    }

    GameRoom getOwnedRoom(UUID owner) {
        return gameRooms.get(owner);
    }

    GameRoom getPlayerGameRoom(UUID uuid) {
        for (GameRoom room : gameRooms.values()) {
            if (room.valid && room.players.contains(uuid)) {
                return room;
            }
        }
        return null;
    }

    boolean isInGameRoom(UUID uuid) {
        return getPlayerGameRoom(uuid) != null;
    }

    void completeInviteRequest(UUID inviterId, Set<UUID> acceptedPlayers) {
        Player inviter = Bukkit.getPlayer(inviterId);
        if (acceptedPlayers.isEmpty()) {
            if (inviter != null && inviter.isOnline()) {
                sendMessage(inviter, PREFIX + I18n.get(Messages.NO_PLAYERS_ACCEPTED));
            }
            return;
        }

        GameRoom inviterRoom = getPlayerGameRoom(inviterId);
        if (inviterRoom != null
                && (inviterRoom.started
                || !inviterRoom.owner.equals(inviterId))) {
            if (inviter != null && inviter.isOnline()) {
                sendMessage(inviter, PREFIX + I18n.get(Messages.JOINED_ANOTHER_ROOM)
                        + I18n.get(Messages.INVITATION_CANNOT_COMPLETED));
            }
            return;
        }

        GameRoom existingRoom = gameRooms.get(inviterId);
        if (existingRoom != null && existingRoom.valid && !existingRoom.started) {
            for (UUID accepted : acceptedPlayers) {
                if (!isInGameRoom(accepted)) {
                    existingRoom.players.add(accepted);
                    Player member = Bukkit.getPlayer(accepted);
                    if (member != null && member.isOnline()) {
                        sendMessage(member, PREFIX + I18n.get(Messages.JOINED_THEDEADLIES_GAME));
                    }
                }
            }
            if (inviter != null && inviter.isOnline()) {
                sendMessage(inviter, PREFIX + I18n.get(Messages.ACCEPTED_INVITEES_HAVE));
            }
            return;
        }

        createGameRoom(inviterId, acceptedPlayers);
    }

    void quitGame(Player player) {
        GameRoom room = getPlayerGameRoom(player.getUniqueId());
        if (room == null) {
            sendMessage(player, PREFIX + I18n.get(Messages.NOT_GAME_ROOM));
            return;
        }
        if (!room.started) {
            room.players.remove(player.getUniqueId());
            sendMessage(player, PREFIX + I18n.get(Messages.LEFT_GAME_ROOM));
            Player owner = Bukkit.getPlayer(room.owner);
            if (owner != null && owner.isOnline() && !room.owner.equals(player.getUniqueId())) {
                sendMessage(owner, PREFIX + I18n.get(Messages.PLAYER) + player.getName() + I18n.get(Messages.LEFT_GAME_ROOM_2));
            }

            if (room.owner.equals(player.getUniqueId())) {
                room.valid = false;
                gameRooms.remove(room.owner);
                for (UUID uuid : room.players) {
                    Player member = Bukkit.getPlayer(uuid);
                    if (member != null && member.isOnline()) {
                        sendMessage(member, PREFIX + I18n.get(Messages.OWNER_LEFT_GAME));
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
            dissolveGameRoom(room, I18n.get(Messages.OWNER_LEFT_GAME_2));
            return;
        }

        room.players.remove(player.getUniqueId());
        sendMessage(player, PREFIX + I18n.get(Messages.LEFT_GAME_GAME));
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()) {
                sendMessage(member, PREFIX + I18n.get(Messages.PLAYER) + player.getName() + I18n.get(Messages.LEFT_GAME));
            }
        }

        if (room.players.isEmpty()) {
            room.valid = false;
            gameRooms.remove(room.owner);
        }
    }

    void endGame(Player player) {
        GameRoom room = getPlayerGameRoom(player.getUniqueId());
        if (room == null || !room.started || room.procedure == null) {
            sendMessage(player, PREFIX + I18n.get(Messages.NOT_ACTIVE_GAME));
            return;
        }

        room.procedure.finishGame();
        sendMessage(player, PREFIX + I18n.get(Messages.GAME_HAS_ENDED));
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.isOnline()
                    && !uuid.equals(player.getUniqueId())) {
                sendMessage(member, PREFIX + I18n.get(Messages.GAME_ENDED) + player.getName()
                        + I18n.get(Messages.ROOM_STILL_OPEN));
            }
        }
    }

    void startGame(UUID owner) {
        GameRoom room = gameRooms.get(owner);
        if (room == null || !room.valid || room.started) {
            return;
        }
        if (room.players.size() < 2) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                sendMessage(player, PREFIX + I18n.get(Messages.LEAST_TWO_PLAYERS));
            }
            return;
        }
        for (UUID uuid : room.players) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || !member.isOnline()) {
                Player player = Bukkit.getPlayer(owner);
                if (player != null && player.isOnline()) {
                    sendMessage(player, PREFIX + I18n.get(Messages.ROOM_MEMBER_OFFLINE));
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
                sendMessage(player, PREFIX + I18n.get(Messages.GAME_STARTED_PLAYERS) + names);
            }
        }
        try {
            room.startProcedure();
        } catch (IllegalStateException exception) {
            room.resetAfterGame();
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                sendMessage(player, PREFIX + I18n.get(Messages.COULD_NOT_START) + exception.getMessage());
            }
        }
    }

    private void createGameRoom(UUID owner, Set<UUID> invitedPlayers) {
        if (gameRooms.containsKey(owner)) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null && player.isOnline()) {
                sendMessage(player, PREFIX + I18n.get(Messages.ALREADY_HAVE_VALID));
            }
            return;
        }
        GameRoom room = new GameRoom(plugin, owner);
        room.players.add(owner);
        room.players.addAll(invitedPlayers);
        gameRooms.put(owner, room);
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null && ownerPlayer.isOnline()) {
            sendMessage(ownerPlayer, PREFIX + I18n.get(Messages.GAME_ROOM_CREATED));
        }
        for (UUID uuid : room.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                sendMessage(player, PREFIX + I18n.get(Messages.JOINED_THEDEADLIES_GAME_2));
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
                sendMessage(member, PREFIX + "§c" + reason);
            }
        }

        room.players.clear();
        gameRooms.remove(room.owner);
    }

    private void sendMessage(Player recipient, String message) {
        recipient.sendMessage(message);
    }
}
