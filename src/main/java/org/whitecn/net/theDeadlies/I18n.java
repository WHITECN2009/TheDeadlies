package org.whitecn.net.theDeadlies;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class I18n {
    private static final Map<String, String> ENGLISH = new HashMap<>();
    private static final List<Map.Entry<String, String>> TRANSLATIONS;
    private static boolean english;

    static {
        put("：", ":");
        put("插件已启用", "TheDeadlies enabled.");
        put("插件已禁用", "TheDeadlies disabled.");
        put("用法: ", "Usage: ");
        put("§c该命令只能被玩家执行", "§cThis command can only be used by players.");
        put("§c你当前不能发起房间邀请", "§cYou cannot send room invitations right now.");
        put("§c找不到在线玩家：§f", "§cOnline player not found: §f");
        put("§e玩家 §f", "§ePlayer §f");
        put("§c你不能邀请自己", "§cYou cannot invite yourself.");
        put("§c玩家 §f", "§cPlayer §f");
        put(" §c已经在游戏房间中，不能邀请", " §cis already in a game room and cannot be invited.");
        put(" §e已经在邀请列表中", " §eis already in the invitation list.");
        put("§c没有有效的受邀玩家", "§cThere are no valid invitees.");
        put("§b玩家§a ", "§bPlayer §a");
        put(" §b向您发送了赎罪之旅游戏请求\n", " §bsent you a TheDeadlies game invitation.\n");
        put("§b一同受邀的还有：§a ", "§bAlso invited: §a");
        put("§a[同意]", "§a[Accept]");
        put("点击同意邀请", "Click to accept the invitation");
        put("§c[拒绝]", "§c[Decline]");
        put("点击拒绝邀请", "Click to decline the invitation");
        put("§c没有可用的受邀玩家", "§cThere are no available invitees.");
        put("§a邀请已发送，等待受邀玩家确认", "§aInvitation sent. Waiting for responses.");
        put("§e以下玩家在15秒内没有回应 ", "§eThese players did not respond within 15 seconds ");
        put("邀请已自动拒绝：", "The invitation was automatically declined:");
        put("§c邀请已超时 ", "§cThe invitation timed out ");
        put("系统自动拒绝了本次邀请", "and was automatically declined.");
        put("§a所有玩家都已经处理了邀请", "§aAll invited players have responded.");
        put("§c无效的邀请", "§cInvalid invitation.");
        put("§c该邀请已经过期", "§cThis invitation has expired.");
        put("§c你是这次邀请的发起者", "§cYou sent this invitation.");
        put("§c你已经处理过这个邀请了", "§cYou have already responded to this invitation.");
        put("§c你已经加入了其他游戏房间", "§cYou are already in another game room.");
        put(" §c已加入其他房间，无法加入本局", " §chas joined another room and cannot join this game.");
        put("§a你同意了游戏邀请", "§aYou accepted the game invitation.");
        put("§a玩家 §f", "§aPlayer §f");
        put(" §a同意了你的游戏邀请", " §aaccepted your game invitation.");
        put("§c你拒绝了游戏邀请", "§cYou declined the game invitation.");
        put(" §c拒绝了你的游戏邀请", " §cdeclined your game invitation.");
        put("§c找不到玩家：§f", "§cPlayer not found: §f");
        put("§c该玩家没有有效的游戏房间", "§cThat player has no valid game room.");
        put("§e你已经在这个游戏房间中", "§eYou are already in this game room.");
        put("§c你已经在其他游戏房间中", "§cYou are already in another game room.");
        put("§c该游戏已经开始，无法申请加入", "§cThis game has started; you cannot request to join.");
        put("§a你已加入赎罪之旅游戏房间", "§aYou joined a TheDeadlies game room.");
        put(" §b申请加入你的赎罪之旅游戏\n", " §brequested to join your TheDeadlies game.\n");
        put("§b有效期：§f15秒\n               ", "§bExpires in: §f15 seconds\n               ");
        put("点击允许玩家加入", "Click to accept the join request");
        put("点击拒绝玩家加入", "Click to decline the join request");
        put("§a已向 §f", "§aJoin request sent to §f");
        put(" §a发送加入申请", " §a.");
        put("§c加入申请已超时", "§cThe join request timed out.");
        put("§c无效的加入申请", "§cInvalid join request.");
        put("§c该加入申请已经过期", "§cThis join request has expired.");
        put("§c你不是该游戏房间的房主", "§cYou do not own this game room.");
        put("§c你的游戏房间已经无效", "§cYour game room is no longer valid.");
        put("§c游戏已经开始，无法加入", "§cThe game has started; the player cannot join.");
        put("§c申请玩家已经离线", "§cThe applicant is offline.");
        put("§a已同意玩家 §f", "§aYou accepted §f");
        put(" §a加入游戏房间", " §ato the game room.");
        put("§a房主 §f", "§aRoom owner §f");
        put(" §a同意了你的加入申请", " §aaccepted your join request.");
        put("§c你拒绝了加入申请", "§cYou declined the join request.");
        put(" §c拒绝了你的加入申请", " §cdeclined your join request.");
        put("§c当前没有等待你处理的游戏选择", "§cThere is no pending game choice for you.");
        put("§c你当前没有正在进行的游戏", "§cYou are not in an active game.");
        put("§c出牌不合法，或当前不能出牌", "§cInvalid play, or it is not your turn.");
        put("§c用法：/thedeadlies play 花色:位置[,花色:位置] [目标UUID] [腐化花色]",
                "§cUsage: /thedeadlies play suit:index[,suit:index] [target UUID] [corruption suit]");
        put("§c未知的子命令", "§cUnknown subcommand.");
        put("§e你已经有一个有效的游戏房间", "§eYou already have a valid game room.");
        put("§a游戏房间创建成功", "§aGame room created.");
        put("§a你已经加入赎罪之旅游戏房间", "§aYou joined a TheDeadlies game room.");
        put("§e没有玩家接受本次邀请", "§eNo players accepted this invitation.");
        put("§c你已加入其他房间或当前游戏已经开始，", "§cYou joined another room or this game has started; ");
        put("无法完成邀请", "the invitation cannot be completed.");
        put("§a已将接受邀请的玩家加入你的游戏房间", "§aAccepted invitees have joined your game room.");
        put("§c你当前没有加入任何游戏房间", "§cYou are not in a game room.");
        put("§a你已退出游戏房间", "§aYou left the game room.");
        put(" §e退出了游戏房间", " §eleft the game room.");
        put("§c房主退出了房间，游戏房间已解散", "§cThe owner left; the game room has been disbanded.");
        put("§c房主 §f", "§cRoom owner §f");
        put("房主退出了房间，本局游戏已结束，游戏房间已解散",
                "The owner left. This game has ended and the room has been disbanded.");
        put("§e你已退出游戏，同时退出了游戏房间", "§eYou left the game and the game room.");
        put(" §e已退出游戏", " §eleft the game.");
        put("§a本局游戏已结束，房间仍然保留", "§aThe game has ended. The room is still open.");
        put("§c本局游戏已被 ", "§cThe game was ended by ");
        put(" §c结束，房间仍然保留", "§c. The room is still open.");
        put("§c至少需要两名玩家才能开始游戏", "§cAt least two players are required to start.");
        put("§c有房间成员已经离线，无法开始游戏", "§cA room member is offline; the game cannot start.");
        put("§a游戏开始，本局玩家：§e", "§aGame started. Players: §e");
        put("§c无法启动游戏：", "§cCould not start the game: ");
        put("这局游戏已经启动", "This game has already started.");
        put("游戏流程已经启动", "The game procedure has already started.");
        put("至少需要两名玩家才能开始游戏", "At least two players are required to start.");
        put("牌堆不足 无法完成初始发牌", "Not enough cards to deal the starting hands.");
        put("§a游戏已经开始", "§aThe game has started.");
        put("§e当前轮到 ", "§eIt is now ");
        put(" §e行动，请打出至少一张手牌", "'s turn. Play at least one card.");
        put(" §f完成了本回合", " §ffinished their turn.");
        put("§e现在轮到 ", "§eIt is now ");
        put(" §f打出了 ", " §fplayed ");
        put(" §f选择了 §e", " §fselected §e");
        put(" §f 将要进行 §d", " §f. Next: §d");
        put("§d判定开始：§f", "§dResolution begins: §f");
        put("§f你的手牌（点击出牌）： ", "§fYour hand (click a card to play): ");
        put(" §f抽取了1张牌", " §fdrew a card.");
        put("净化", "Purification");
        put("将光环放入你的手牌，即使它当前在其他玩家手中。",
                "Take the Halo into your hand, even if another player currently holds it.");
        put("傲慢", "Pride");
        put("询问另一名玩家是否有傲慢。若他展示傲慢，你抽1张牌；否则他抽1张牌。",
                "Ask another player to reveal Pride. If they do, draw a card; otherwise, they draw a card.");
        put("嫉妒", "Envy");
        put("抽2张牌。然后，若你手中没有嫉妒，可以与任意玩家交换手牌。",
                "Draw 2 cards. Then, if you have no Envy in hand, you may swap hands with any player.");
        put("愤怒", "Wrath");
        put("选择另一名玩家，他抽2张牌。然后他可以弃掉1张愤怒；若他这么做，你抽2张牌。",
                "Choose another player; they draw 2 cards and may discard a Wrath. If they do, you draw 2 cards.");
        put("如此循环，直到有人不能或不想弃掉愤怒。",
                "Repeat until a player cannot or does not want to discard Wrath.");
        put("懒惰", "Sloth");
        put("将懒惰放在自己面前。所有面前有懒惰的对手各抽1张牌；你的下个回合开始时弃掉此牌。",
                "Place Sloth in front of you. Each opponent with Sloth in front of them draws a card. Discard it at the start of your next turn.");
        put("贪婪", "Greed");
        put("向其他玩家逐张发牌。发出2张非贪婪牌且最后一张不是贪婪时，可选择停止。",
                "Deal cards one at a time to other players. After 2 non-Greed cards, you may stop if the last card was not Greed.");
        put("发出两张同花色的非贪婪牌时强制停止，并将发出的牌收回手牌。发出5张牌后可清空手牌。",
                "Two non-Greed cards of the same suit force you to stop and take all dealt cards into your hand. After dealing 5 cards, you may empty your hand.");
        put("暴食", "Gluttony");
        put("选择从另一名玩家手中随机抽1张牌，或自己抽3张牌，然后立即再进行一次回合。",
                "Take a random card from another player's hand, or draw 3 cards yourself, then take another turn.");
        put("色欲", "Lust");
        put("选择另一名玩家。对方可以选择什么也不做，或你们各弃1张牌；若有人弃掉色欲，另一人抽3张牌。",
                "Choose another player. They may do nothing or each of you may discard a card. If anyone discards Lust, the other player draws 3 cards.");
        put("腐化", "Corruption");
        put("这张牌拥有全部7种花色。打出时选择一种花色，并执行对应的效果。",
                "This card has all 7 suits. Choose a suit when played and resolve its effect.");
        put("光环", "Halo");
        put("清空你的手牌。打出或弃掉光环时，将它正面朝上放回桌面中央。",
                "Empty your hand. When played or discarded, return the Halo face up to the center of the table.");
        put("未知", "Unknown");
        put("这张牌的效果尚未定义", "This card's effect is not defined.");
        put("离线玩家", "Offline player");
        put(" §f弃掉了 ", " §fdiscarded ");
        put("§b光环被弃置，回到桌面中央", "§bThe Halo was discarded and returned to the center of the table.");
        put("同数字出牌：选择要执行效果的牌", "Same-number play: choose which card effect to resolve");
        put("腐化牌：选择要执行的花色", "Corruption: choose a suit for its effect");
        put("自己（抽3张）", "Yourself (draw 3)");
        put("选择卡牌目标", "Choose a card target");
        put("§b光环回到桌面中央", "§bThe Halo returned to the center of the table.");
        put("§c腐化牌需要选择1至7的花色", "§cCorruption requires a suit from 1 to 7.");
        put("傲慢判定", "Pride resolution");
        put("展示傲慢", "Reveal Pride");
        put("不展示", "Do not reveal");
        put("傲慢：选择是否展示手中的傲慢", "Pride: choose whether to reveal Pride in your hand");
        put(" §f展示了傲慢，", " §frevealed Pride; ");
        put(" §f抽1张牌", " §fdraws 1 card.");
        put(" §f没有展示傲慢，抽1张牌", " §fdid not reveal Pride and draws 1 card.");
        put("不交换", "Do not swap");
        put("嫉妒：选择一名玩家交换手牌，或不交换", "Envy: choose a player to swap hands with, or do not swap");
        put(" §f与 ", " §fswapped hands with ");
        put(" §f交换了手牌", "");
        put("愤怒判定", "Wrath resolution");
        put("弃掉1张愤怒，让对方抽2张", "Discard a Wrath; the other player draws 2");
        put("停止愤怒循环", "Stop the Wrath loop");
        put("愤怒：选择弃牌继续，或停止", "Wrath: discard to continue or stop");
        put(" §f将懒惰放在自己面前", " §fplaced Sloth in front of themselves");
        put(" §f回合开始，面前的懒惰牌已弃置", " §fstarted their turn and discarded the Sloth in front of them");
        put("结束发牌", "Stop dealing");
        put("清空自己的手牌", "Empty your hand");
        put("贪婪：已发出5张牌，选择结算", "Greed: 5 cards dealt; choose how to resolve");
        put("§6没有可接收贪婪牌的其他在线玩家", "§6There are no other online players to receive Greed cards.");
        put("停止发牌", "Stop dealing");
        put("贪婪：选择接收牌的玩家", "Greed: choose a player to receive a card");
        put(" §f发给了 ", " §fdealt a card to ");
        put("§6已发出两张相同牌名的牌，", "§6Two cards of the same name were dealt; ");
        put("贪婪立即停止，并将发出的牌收回手牌", "Greed stops immediately and all dealt cards return to your hand.");
        put("暴食：选择 ", "Gluttony: choose ");
        put(" §f手牌中的一张（实际随机抽取）", " §f's hand ([n]; the card is selected randomly)");
        put(" §f从 ", " §ftook a random card from ");
        put(" §f手中随机抽取了1张牌", "§f's hand.");
        put("暴食判定", "Gluttony resolution");
        put("§5暴食效果结算完成：", "§5Gluttony resolved: ");
        put(" §f获得一次额外回合", " §fgets an extra turn.");
        put(" §f继续进行额外回合", " §fcontinues with the extra turn.");
        put("当前玩家已有待处理的选择", "This player already has a pending choice.");
        put("需要操作的玩家已离线", "The player who needs to act is offline.");
        put("净化-0", "Purification-0");
        put("腐化-8", "Corruption-8");
        put("色欲选择", "Lust choice");
        put("什么也不做", "Do nothing");
        put("双方各弃1张牌", "Both players discard a card");
        put("色欲：选择如何响应", "Lust: choose how to respond");
        put("选择弃掉一张牌", "Choose a card to discard");
        put(" §f将光环拿入手牌", " §ftook the Halo into their hand");
        put(" §f手中夺得光环", " §ftook the Halo from another player");
        put(" §f打空了手牌，罪恶值降为 §c", " §femptied their hand. Sin level is now §c");
        put(" §f获得胜利", " §fwins the game.");

        TRANSLATIONS = ENGLISH.entrySet().stream()
                .sorted((left, right) -> Integer.compare(
                        right.getKey().length(),
                        left.getKey().length()
                ))
                .toList();
    }

    private I18n() {
    }

    private static void put(String chinese, String englishText) {
        ENGLISH.put(chinese, englishText);
    }

    public static void load(JavaPlugin plugin) {
        String configured = plugin.getConfig().getString("language", "ZH_CN");
        if (configured == null) {
            configured = "ZH_CN";
        }
        String language = configured.trim().toUpperCase(Locale.ROOT);
        if (language.equals("EN")) {
            english = true;
        } else if (language.equals("ZH_CN")) {
            english = false;
        } else {
            english = false;
            plugin.getLogger().warning(
                    "Unsupported language '" + configured + "'; using ZH_CN."
            );
        }
    }

    public static String tr(String text) {
        if (!english || text == null) {
            return text;
        }

        String translated = text;
        for (Map.Entry<String, String> entry : TRANSLATIONS) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }
        return translated;
    }

    public static void translate(BaseComponent component) {
        if (component instanceof TextComponent textComponent) {
            textComponent.setText(tr(textComponent.getText()));
        }
        if (component.getExtra() != null) {
            for (BaseComponent extra : component.getExtra()) {
                translate(extra);
            }
        }
    }

    public static String commandUsage() {
        return tr(Vars.PREFIX
                + "§c用法: /thedeadlies [invite...|joingame...|quitgame|startgame|endgame]");
    }
}
