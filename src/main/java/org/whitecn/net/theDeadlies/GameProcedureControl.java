package org.whitecn.net.theDeadlies;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.whitecn.net.theDeadlies.Vars.PREFIX;

public class GameProcedureControl {

    private static final long JUDGMENT_DELAY_TICKS = 40L;
    private static final int MAX_SIN_LEVEL = 6;
    private static final int HAND_SIZE = 6;

    /*
     * 玩家 UUID -> 玩家手牌
     *
     * 手牌内部下标对应牌组：
     *
     * 0: 净化牌
     * 1: 傲慢
     * 2: 嫉妒
     * 3: 愤怒
     * 4: 懒惰
     * 5: 贪婪
     * 6: 暴食
     * 7: 色欲
     * 8: 光环牌
     */
    private final Map<UUID, ArrayList<ArrayList<Integer>>> playerCards =
            new HashMap<>();
    private final Map<UUID, Integer> sinLevels = new HashMap<>();
    private final Map<UUID, Integer> emptyHandCount = new HashMap<>();

    /*
     * 每个内部 ArrayList 代表一个牌组
     */
    private final ArrayList<ArrayList<Integer>> cardPile =
            new ArrayList<>(9);

    /*
     * 弃牌堆
     */
    private final ArrayList<ArrayList<Integer>> discardPile =
            new ArrayList<>(9);

    /*
     * 本局玩家 使用副本 避免游戏开始后外部继续修改玩家列表
     */
    private final List<UUID> players;

    /*
     * 当前回合顺序
     */
    private final List<UUID> turnOrder = new ArrayList<>();

    private final Random random = new Random();

    private int currentPlayerIndex = 0;
    private int round = 1;
    private GamePhase phase = GamePhase.CREATED;
    private final TheDeadlies plugin;
    private boolean haloPublic = true;
    private final Map<UUID, PendingChoice> pendingChoices = new HashMap<>();
    private final Map<UUID, List<Integer>> lazinessInFront = new HashMap<>();
    private final Runnable onGameFinished;
    private boolean finishNotified;
    private boolean resolvingEffect;

    public GameProcedureControl(
            TheDeadlies plugin,
            Set<UUID> players,
            Runnable onGameFinished
    ) {
        this.plugin = plugin;
        this.players = new ArrayList<>(new HashSet<>(players));
        this.turnOrder.addAll(this.players);
        this.onGameFinished = onGameFinished;
    }

    /**
     * 启动一局游戏
     */
    public void start() {
        if (phase != GamePhase.CREATED) {
            throw new IllegalStateException("这局游戏已经启动");
        }

        if (players.size() < 2) {
            throw new IllegalStateException("至少需要两名玩家才能开始游戏");
        }

        for (UUID uuid : players) {
            ensurePlayerCards(uuid);
            sinLevels.put(uuid, MAX_SIN_LEVEL);
            emptyHandCount.put(uuid, 0);
        }

        shuffleCardPile();
        phase = GamePhase.DEALING;
        dealInitialCards();

        phase = GamePhase.PLAYER_TURN;
        currentPlayerIndex = 0;
        round = 1;
        broadcastText(message(PREFIX + "§a游戏已经开始"));
        broadcastText(message(PREFIX + "§e当前轮到 " + playerName(getCurrentPlayer())
                + " §e行动，请打出至少一张手牌"));
        expireLaziness(getCurrentPlayer());
        for (UUID uuid : players) {
            sendHandControls(uuid);
        }
    }

    private record CardLocation(int suitIndex, int cardIndex) {
    }

    /**
     * 初始化玩家手牌
     */
    private void ensurePlayerCards(UUID uuid) {
        playerCards.computeIfAbsent(uuid, ignored -> createEmptyCardGroups());
    }

    /**
     * 创建九组空牌
     */
    private ArrayList<ArrayList<Integer>> createEmptyCardGroups() {
        ArrayList<ArrayList<Integer>> cardGroups = new ArrayList<>(9);

        for (int i = 0; i < 10; i++) {
            cardGroups.add(new ArrayList<>());
        }

        return cardGroups;
    }

    /**
     * 创建并洗混新牌堆 或者将弃牌堆重新洗回牌堆
     */
    private void shuffleCardPile() {
        cardPile.clear();

        if (getDiscardPileSize() == 0) {
            if (phase != GamePhase.CREATED) {
                return;
            }
            createNewCardPile();
        } else {
            recycleDiscardPile();
        }
    }

    /**
     * 创建一套新牌
     * 0       -> 净化牌
     * 1 - 7   -> 七宗罪牌组
     * 9       -> 光环牌
     */
    private void createNewCardPile() {
        cardPile.add(new ArrayList<>(List.of(0)));

        for (int suit = 1; suit <= 7; suit++) {
            ArrayList<Integer> cards = new ArrayList<>(
                    List.of(1, 2, 3, 4, 5, 6, 7)
            );

            Collections.shuffle(cards, random);
            cardPile.add(cards);
        }

        cardPile.add(new ArrayList<>(List.of(8)));
    }

    /**
     * 将弃牌堆中的牌洗回牌堆
     */
    private void recycleDiscardPile() {
        for (ArrayList<Integer> discardedCards : discardPile) {
            ArrayList<Integer> shuffledCards =
                    new ArrayList<>(discardedCards);

            Collections.shuffle(shuffledCards, random);
            cardPile.add(shuffledCards);
        }

        discardPile.clear();
    }

    /**
     * 初始发牌
     *
     * 当前规则：每位玩家获得六张牌
     */
    private void dealInitialCards() {
        for (UUID uuid : players) {
            for (int i = 0; i < HAND_SIZE; i++) {
                if (!drawCard(uuid)) {
                    throw new IllegalStateException(
                            "牌堆不足 无法完成初始发牌"
                    );
                }
            }
        }
    }

    /**
     * 给指定玩家抽一张牌
     *
     * @return 是否成功抽牌
     */
    public boolean drawCard(UUID uuid) {
        return drawCardAndGet(uuid) != null;
    }

    private DrawnCard drawCardAndGet(UUID uuid) {
        if (!players.contains(uuid)) {
            return null;
        }

        if (phase != GamePhase.DEALING && phase != GamePhase.PLAYER_TURN) {
            return null;
        }

        ArrayList<ArrayList<Integer>> playerHand = playerCards.get(uuid);

        if (playerHand == null) {
            return null;
        }

        if (getCardPileSize() == 0) {
            if (getDiscardPileSize() == 0) {
                return null;
            }

            shuffleCardPile();
            if (getCardPileSize() == 0) {
                return null;
            }
        }

        ArrayList<CardLocation> availableCards = new ArrayList<>();
        for (int suitIndex = 0; suitIndex < cardPile.size(); suitIndex++) {
            for (int cardIndex = 0; cardIndex < cardPile.get(suitIndex).size(); cardIndex++) {
                availableCards.add(new CardLocation(suitIndex, cardIndex));
            }
        }

        CardLocation selected = availableCards.get(
                random.nextInt(availableCards.size())
        );
        Integer card = cardPile.get(selected.suitIndex()).remove(
                selected.cardIndex()
        );

        playerHand.get(selected.suitIndex()).add(card);
        if (phase == GamePhase.PLAYER_TURN) {
            announceCardDrawn(uuid);
        }
        return new DrawnCard(selected.suitIndex(), card);
    }

    private record DrawnCard(int suitIndex, int value) {
    }

    private record IssuedCard(UUID recipient, int suitIndex, int value) {
    }

    /**
     * 获取牌堆剩余牌数量
     */
    private int getCardPileSize() {
        int size = 0;

        for (ArrayList<Integer> suit : cardPile) {
            size += suit.size();
        }

        return size;
    }

    private int getDiscardPileSize() {
        int size = 0;
        for (List<Integer> suit : discardPile) {
            size += suit.size();
        }
        return size;
    }

    /**
     * 判断是否轮到指定玩家
     */
    public boolean isPlayerTurn(UUID uuid) {
        return phase == GamePhase.PLAYER_TURN
                && !resolvingEffect
                && pendingChoices.isEmpty()
                && getCurrentPlayer().equals(uuid);
    }

    /**
     * 获取当前回合玩家
     */
    public UUID getCurrentPlayer() {
        if (turnOrder.isEmpty()) {
            return null;
        }

        return turnOrder.get(currentPlayerIndex);
    }

    private void advanceTurn(UUID uuid) {
        currentPlayerIndex =
                (currentPlayerIndex + 1) % turnOrder.size();

        if (currentPlayerIndex == 0) {
            round++;
        }

        broadcastText(message(PREFIX + "§e" + playerName(uuid) + " §f完成了本回合"));
        broadcastText(message(PREFIX + "§e现在轮到 " + playerName(getCurrentPlayer())
                + " §e行动，请打出至少一张手牌"));
        expireLaziness(getCurrentPlayer());
        sendHandControls(getCurrentPlayer());
    }

    /**
     * 广播“玩家打出了某张牌”的描述消息
     */
    public void announceCardPlayed(UUID playerUuid, int suitIndex, int cardValue) {
        TextComponent text = message(PREFIX + "§e" + playerName(playerUuid)
                + " §f打出了 ");
        text.addExtra(cardComponent(suitIndex, cardValue));
        broadcastText(text);
    }

    /**
     * 广播玩家选择目标并准备判定的消息
     */
    public void announceJudgmentSelection(
            UUID playerUuid,
            UUID targetUuid,
            String judgmentDescription
    ) {
        broadcastText(message(PREFIX + "§e" + playerName(playerUuid)
                + " §f选择了 §e" + playerName(targetUuid)
                + " §f 将要进行 §d" + judgmentDescription + "§f"));
    }

    /**
     * 延迟执行主动或被动判定
     *
     * 判定逻辑由调用方传入 延迟任务仍然运行在 Bukkit 主线程
     */
    public void scheduleJudgment(
            UUID playerUuid,
            UUID targetUuid,
            String judgmentDescription,
            Runnable judgment
    ) {
        announceJudgmentSelection(
                playerUuid,
                targetUuid,
                judgmentDescription
        );

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (phase == GamePhase.GAME_OVER) {
                        return;
                    }

                    broadcastText(message(PREFIX + "§d判定开始：§f"
                            + judgmentDescription));
                    judgment.run();
                },
                JUDGMENT_DELAY_TICKS
        );
    }

    private void sendHandControls(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        if (player == null || !player.isOnline() || hand == null) {
            return;
        }
        TextComponent text = message(PREFIX + "§f你的手牌（点击出牌）： ");
        for (int suit = 0; suit < hand.size(); suit++) {
            for (int index = 0; index < hand.get(suit).size(); index++) {
                TextComponent card = cardComponent(suit, hand.get(suit).get(index));
                card.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/thedeadlies play " + suit + ":" + index
                ));
                text.addExtra(card);
                text.addExtra(" ");
            }
        }
        player.spigot().sendMessage(text);
    }

    /**
     * 广播抽牌数量，并只向该玩家刷新手牌。
     */
    private void announceCardDrawn(UUID playerUuid) {
        broadcastText(message(PREFIX + "§e" + playerName(playerUuid)
                + " §f抽取了1张牌"));
        sendHandControls(playerUuid);
    }

    /**
     * 创建带颜色和悬浮提示的卡牌组件
     */
    private TextComponent cardComponent(int suitIndex, int cardValue) {
        CardDescription description = cardDescription(suitIndex, cardValue);
        String cardName = description.numbered
                ? description.name + "-" + cardValue
                : description.name;
        cardName = I18n.tr(cardName);
        TextComponent card = new TextComponent("[" + cardName + "]");
        card.setColor(description.color);
        card.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(I18n.tr(description.effect))
                        .color(description.hoverColor)
                        .create()
        ));
        return card;
    }

    private CardDescription cardDescription(int suitIndex, int cardValue) {
        return switch (suitIndex) {
            case 0 -> new CardDescription(
                    "净化",
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    "将光环放入你的手牌，即使它当前在其他玩家手中。"
            );
            case 1 -> new CardDescription(
                    "傲慢",
                    ChatColor.RED,
                    ChatColor.DARK_RED,
                    "询问另一名玩家是否有傲慢。若他展示傲慢，你抽1张牌；否则他抽1张牌。"
            );
            case 2 -> new CardDescription(
                    "嫉妒",
                    ChatColor.GREEN,
                    ChatColor.DARK_GREEN,
                    "抽2张牌。然后，若你手中没有嫉妒，可以与任意玩家交换手牌。"
            );
            case 3 -> new CardDescription(
                    "愤怒",
                    ChatColor.DARK_RED,
                    ChatColor.RED,
                    "选择另一名玩家，他抽2张牌。然后他可以弃掉1张愤怒；若他这么做，你抽2张牌。"
                            + "如此循环，直到有人不能或不想弃掉愤怒。"
            );
            case 4 -> new CardDescription(
                    "懒惰",
                    ChatColor.GRAY,
                    ChatColor.DARK_GRAY,
                    "将懒惰放在自己面前。所有面前有懒惰的对手各抽1张牌；你的下个回合开始时弃掉此牌。"
            );
            case 5 -> new CardDescription(
                    "贪婪",
                    ChatColor.GOLD,
                    ChatColor.YELLOW,
                    "向其他玩家逐张发牌。发出2张非贪婪牌且最后一张不是贪婪时，可选择停止。"
                            + "发出两张同花色的非贪婪牌时强制停止，并将发出的牌收回手牌。发出5张牌后可清空手牌。"
            );
            case 6 -> new CardDescription(
                    "暴食",
                    ChatColor.DARK_PURPLE,
                    ChatColor.LIGHT_PURPLE,
                    "选择从另一名玩家手中随机抽1张牌，或自己抽3张牌，然后立即再进行一次回合。"
            );
            case 7 -> new CardDescription(
                    "色欲",
                    ChatColor.LIGHT_PURPLE,
                    ChatColor.DARK_PURPLE,
                    "选择另一名玩家。对方可以选择什么也不做，或你们各弃1张牌；若有人弃掉色欲，另一人抽3张牌。"
            );
            case 8 -> new CardDescription(
                    "腐化",
                    ChatColor.DARK_GRAY,
                    ChatColor.GRAY,
                    "这张牌拥有全部7种花色。打出时选择一种花色，并执行对应的效果。"
            );
            case 9 -> new CardDescription(
                    "光环",
                    ChatColor.AQUA,
                    ChatColor.BLUE,
                    "清空你的手牌。打出或弃掉光环时，将它正面朝上放回桌面中央。",
                    false
            );
            default -> new CardDescription(
                    "未知",
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    "这张牌的效果尚未定义",
                    true
            );
        };
    }

    private TextComponent message(String content) {
        return new TextComponent(I18n.tr(content));
    }

    private void broadcastText(TextComponent text) {
        I18n.translate(text);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                player.spigot().sendMessage(text);
            }
        }
    }

    private String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "离线玩家" : player.getName();
    }

    /**
     * 将玩家的一张牌放入弃牌堆
     */
    public boolean discardCard(UUID uuid, int suitIndex, int cardIndex) {
        ArrayList<ArrayList<Integer>> playerHand = playerCards.get(uuid);

        if (playerHand == null) {
            return false;
        }

        if (suitIndex < 0 || suitIndex >= playerHand.size()) {
            return false;
        }

        ArrayList<Integer> suit = playerHand.get(suitIndex);

        if (cardIndex < 0 || cardIndex >= suit.size()) {
            return false;
        }

        Integer card = suit.remove(cardIndex);

        while (discardPile.size() < 10) {
            discardPile.add(new ArrayList<>());
        }
        if (suitIndex != 9) {
            discardPile.get(suitIndex).add(card);
            TextComponent discarded = message(PREFIX + "§e" + playerName(uuid)
                    + " §f弃掉了 ");
            discarded.addExtra(cardComponent(suitIndex, card));
            broadcastText(discarded);
        } else {
            haloPublic = true;
            broadcastText(message(PREFIX + "§b光环被弃置，回到桌面中央"));
        }
        sendHandControls(uuid);
        return true;
    }

    /**
     * 获取某位玩家的手牌
     *
     * 返回副本 避免外部直接修改内部状态
     */
    public ArrayList<ArrayList<Integer>> getPlayerCards(UUID uuid) {
        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);

        if (hand == null) {
            return null;
        }

        ArrayList<ArrayList<Integer>> copy = new ArrayList<>(hand.size());

        for (ArrayList<Integer> suit : hand) {
            copy.add(new ArrayList<>(suit));
        }

        return copy;
    }

    public int getSinLevel(UUID uuid) {
        return sinLevels.getOrDefault(uuid, 0);
    }

    public boolean isHaloPublic() {
        return haloPublic;
    }

    public int getHandSize(UUID uuid) {
        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        if (hand == null) {
            return 0;
        }
        return hand.stream().mapToInt(List::size).sum();
    }

    /**
     * 出牌入口。index 是对应花色牌组中的位置，允许一次出一张或多张。
     */
    public boolean playCards(
            UUID uuid,
            List<CardSelection> selections,
            UUID targetUuid,
            int corruptionSuit
    ) {
        if (!isPlayerTurn(uuid) || selections == null || selections.isEmpty()) {
            return false;
        }
        if (!isValidSelection(uuid, selections)) {
            return false;
        }

        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        final List<CardSelection> selectedCards = List.copyOf(selections);
        int effectSuit = -1;
        int effectValue = Integer.MIN_VALUE;
        List<CardSelection> effectCards = new ArrayList<>();
        for (CardSelection selection : selections) {
            int value = hand.get(selection.suitIndex()).get(selection.cardIndex());
            if (value > effectValue) {
                effectValue = value;
                effectSuit = selection.suitIndex();
                effectCards.clear();
                effectCards.add(selection);
            } else if (value == effectValue) {
                effectCards.add(selection);
            }
        }

        if (effectCards.size() > 1) {
            Map<String, String> options = new LinkedHashMap<>();
            for (CardSelection effectCard : effectCards) {
                options.put(
                        effectCard.suitIndex() + ":" + effectCard.cardIndex(),
                        cardNameFor(
                                effectCard.suitIndex(),
                                hand.get(effectCard.suitIndex()).get(effectCard.cardIndex())
                        )
                );
            }
            promptChoice(uuid, "同数字出牌：选择要执行效果的牌", options, choice -> {
                String[] parts = choice.split(":");
                CardSelection chosen = new CardSelection(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1])
                );
                int chosenValue = playerCards.get(uuid).get(chosen.suitIndex())
                        .get(chosen.cardIndex());
                preparePlay(
                        uuid,
                        selectedCards,
                        targetUuid,
                        corruptionSuit,
                        chosen.suitIndex(),
                        chosenValue
                );
            });
            return true;
        }

        return preparePlay(
                uuid,
                selectedCards,
                targetUuid,
                corruptionSuit,
                effectSuit,
                effectValue
        );
    }

    private boolean preparePlay(
            UUID uuid,
            List<CardSelection> selectedCards,
            UUID targetUuid,
            int corruptionSuit,
            int effectSuit,
            int effectValue
    ) {
        if (effectSuit == 8 && (corruptionSuit < 1 || corruptionSuit > 7)) {
            Map<String, String> options = new LinkedHashMap<>();
            for (int suit = 1; suit <= 7; suit++) {
                options.put(Integer.toString(suit), cardName(suit));
            }
            promptChoice(uuid, "腐化牌：选择要执行的花色", options, choice ->
                    preparePlay(
                            uuid,
                            selectedCards,
                            targetUuid,
                            Integer.parseInt(choice),
                            effectSuit,
                            effectValue
                    )
            );
            return true;
        }

        int resolvedSuit = effectSuit == 8 ? corruptionSuit : effectSuit;
        if (requiresTarget(resolvedSuit)
                && targetUuid != null
                && !isOtherPlayer(uuid, targetUuid)) {
            return false;
        }
        if (requiresTarget(resolvedSuit) && targetUuid == null) {
            Map<String, String> options = playerOptions(uuid);
            if (resolvedSuit == 6) {
                options.entrySet().removeIf(
                        entry -> getHandSize(UUID.fromString(entry.getKey())) == 0
                );
                options.put("self", "自己（抽3张）");
            }
            if (options.isEmpty()) {
                return false;
            }
            final int selectedEffectSuit = effectSuit;
            final int selectedEffectValue = effectValue;
            promptChoice(uuid, "选择卡牌目标", options, choice -> {
                UUID target = choice.equals("self") ? null : UUID.fromString(choice);
                commitPlay(
                        uuid,
                        selectedCards,
                        target,
                        corruptionSuit,
                        selectedEffectSuit,
                        selectedEffectValue
                );
            });
            return true;
        }

        commitPlay(uuid, selectedCards, targetUuid, corruptionSuit, effectSuit, effectValue);
        return true;
    }

    private void commitPlay(
            UUID uuid,
            List<CardSelection> selections,
            UUID targetUuid,
            int corruptionSuit,
            int effectSuit,
            int effectValue
    ) {
        if (!isPlayerTurn(uuid) || !isValidSelection(uuid, selections)) {
            return;
        }
        resolvingEffect = true;

        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        List<CardSelection> ordered = new ArrayList<>(selections);
        CardSelection lazinessCard = effectSuit == 4
                ? findEffectCard(selections, hand, effectSuit, effectValue)
                : null;
        ordered.sort((a, b) -> {
            int suitCompare = Integer.compare(b.suitIndex(), a.suitIndex());
            return suitCompare != 0
                    ? suitCompare
                    : Integer.compare(b.cardIndex(), a.cardIndex());
        });

        for (CardSelection selection : ordered) {
            int value = hand.get(selection.suitIndex()).get(selection.cardIndex());
            hand.get(selection.suitIndex()).remove(selection.cardIndex());
            if (selection.equals(lazinessCard)) {
                lazinessInFront.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(value);
            } else {
                addDiscard(selection.suitIndex(), value);
            }
        }

        announceCardPlayed(uuid, effectSuit, effectValue);
        resolvePlayedCard(uuid, effectSuit, effectValue, targetUuid, corruptionSuit,
                () -> finishPlayedAction(uuid));
    }

    private CardSelection findEffectCard(
            List<CardSelection> selections,
            ArrayList<ArrayList<Integer>> hand,
            int effectSuit,
            int effectValue
    ) {
        for (CardSelection selection : selections) {
            if (selection.suitIndex() == effectSuit
                    && hand.get(selection.suitIndex()).get(selection.cardIndex()) == effectValue) {
                return selection;
            }
        }
        return null;
    }

    private void finishPlayedAction(UUID uuid) {
        if (phase == GamePhase.GAME_OVER) {
            return;
        }
        resolvingEffect = false;
        checkEmptyHand(uuid);
        if (phase != GamePhase.GAME_OVER) {
            advanceTurn(uuid);
        }
    }

    private boolean requiresTarget(int suitIndex) {
        return suitIndex == 1 || suitIndex == 3 || suitIndex == 6 || suitIndex == 7;
    }

    public record CardSelection(int suitIndex, int cardIndex) {
    }

    private boolean isValidSelection(UUID uuid, List<CardSelection> selections) {
        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        Set<String> used = new HashSet<>();
        ArrayList<Integer> values = new ArrayList<>();
        Integer sameSuit = null;
        Integer sameValue = null;

        for (CardSelection selection : selections) {
            if (selection.suitIndex() < 0
                    || selection.suitIndex() >= hand.size()
                    || selection.cardIndex() < 0
                    || selection.cardIndex()
                    >= hand.get(selection.suitIndex()).size()
                    || !used.add(selection.suitIndex() + ":" + selection.cardIndex())) {
                return false;
            }
            int value = hand.get(selection.suitIndex()).get(selection.cardIndex());
            if (selection.suitIndex() == 0 || selection.suitIndex() == 9) {
                return selections.size() == 1;
            }
            values.add(value);
            if (sameSuit == null) {
                sameSuit = selection.suitIndex();
            }
            if (sameValue == null) {
                sameValue = value;
            }
        }
        if (selections.size() == 1) {
            return true;
        }
        final int selectedValue = sameValue;
        final int selectedSuit = sameSuit;
        boolean sameNumber = values.stream().allMatch(value -> value == selectedValue);
        boolean sameName = selections.stream().allMatch(
                selection -> selection.suitIndex() == selectedSuit
        );
        Collections.sort(values);
        boolean straight = values.size() >= 3;
        for (int i = 1; i < values.size() && straight; i++) {
            straight = values.get(i) == values.get(i - 1) + 1;
        }
        return sameNumber || sameName || straight;
    }

    private void addDiscard(int suitIndex, int value) {
        while (discardPile.size() < 10) {
            discardPile.add(new ArrayList<>());
        }
        if (suitIndex != 9) {
            discardPile.get(suitIndex).add(value);
        }
    }

    private void resolvePlayedCard(
            UUID playerUuid,
            int suitIndex,
            int value,
            UUID targetUuid,
            int corruptionSuit,
            Runnable finish
    ) {
        if (suitIndex == 0) {
            takeHalo(playerUuid);
            finish.run();
            return;
        }
        if (suitIndex == 9) {
            clearHand(playerUuid);
            haloPublic = true;
            broadcastText(message(PREFIX + "§b光环回到桌面中央"));
            finish.run();
            return;
        }
        if (suitIndex == 8) {
            if (corruptionSuit < 1 || corruptionSuit > 7) {
                broadcastText(message(PREFIX + "§c腐化牌需要选择1至7的花色"));
                finish.run();
                return;
            }
            suitIndex = corruptionSuit;
        }
        switch (suitIndex) {
            case 1 -> resolvePride(playerUuid, targetUuid, finish);
            case 2 -> resolveJealousy(playerUuid, finish);
            case 3 -> resolveAnger(playerUuid, targetUuid, finish);
            case 4 -> resolveLaziness(playerUuid, finish);
            case 5 -> resolveGreed(
                    playerUuid,
                    finish,
                    new ArrayList<>(),
                    new HashSet<>(),
                    0
            );
            case 6 -> resolveGluttony(playerUuid, targetUuid, finish);
            case 7 -> resolveLust(playerUuid, targetUuid, finish);
            default -> finish.run();
        }
    }

    private void resolvePride(UUID playerUuid, UUID targetUuid, Runnable finish) {
        if (!isOtherPlayer(playerUuid, targetUuid)) {
            finish.run();
            return;
        }
        scheduleJudgment(playerUuid, targetUuid, "傲慢判定", () -> {
            Map<String, String> options = new LinkedHashMap<>();
            if (hasCard(targetUuid, 1)) {
                options.put("show", "展示傲慢");
            }
            options.put("refuse", "不展示");
            promptChoice(targetUuid, "傲慢：选择是否展示手中的傲慢", options, choice -> {
                if (choice.equals("show") && hasCard(targetUuid, 1)) {
                    broadcastText(message(PREFIX + "§e" + playerName(targetUuid)
                            + " §f展示了傲慢，" + playerName(playerUuid) + " §f抽1张牌"));
                    drawCard(playerUuid);
                } else {
                    broadcastText(message(PREFIX + "§e" + playerName(targetUuid)
                            + " §f没有展示傲慢，抽1张牌"));
                    drawCard(targetUuid);
                }
                finish.run();
            });
        });
    }

    private void resolveJealousy(UUID playerUuid, Runnable finish) {
        drawCard(playerUuid);
        drawCard(playerUuid);
        if (hasCard(playerUuid, 2)) {
            finish.run();
            return;
        }
        Map<String, String> options = playerOptions(playerUuid);
        if (options.isEmpty()) {
            finish.run();
            return;
        }
        options.put("skip", "不交换");
        promptChoice(playerUuid, "嫉妒：选择一名玩家交换手牌，或不交换", options, choice -> {
            if (!choice.equals("skip")) {
                UUID target = UUID.fromString(choice);
                ArrayList<ArrayList<Integer>> ownHand = playerCards.get(playerUuid);
                ArrayList<ArrayList<Integer>> targetHand = playerCards.get(target);
                playerCards.put(playerUuid, targetHand);
                playerCards.put(target, ownHand);
                broadcastText(message(PREFIX + "§a" + playerName(playerUuid)
                        + " §f与 " + playerName(target) + " §f交换了手牌"));
                sendHandControls(playerUuid);
                sendHandControls(target);
            }
            finish.run();
        });
    }

    private void resolveAnger(UUID playerUuid, UUID targetUuid, Runnable finish) {
        if (!isOtherPlayer(playerUuid, targetUuid)) {
            finish.run();
            return;
        }
        scheduleJudgment(playerUuid, targetUuid, "愤怒判定", () ->
                angerStep(playerUuid, targetUuid, targetUuid, finish)
        );
    }

    private void angerStep(UUID playerUuid, UUID targetUuid, UUID responder, Runnable finish) {
        drawCard(responder);
        drawCard(responder);
        Map<String, String> options = new LinkedHashMap<>();
        if (hasCard(responder, 3)) {
            options.put("discard", "弃掉1张愤怒，让对方抽2张");
        }
        options.put("stop", "停止愤怒循环");
        promptChoice(responder, "愤怒：选择弃牌继续，或停止", options, choice -> {
            if (!choice.equals("discard")) {
                finish.run();
                return;
            }
            int angerIndex = playerCards.get(responder).get(3).size() - 1;
            discardCard(responder, 3, angerIndex);
            UUID nextResponder = responder.equals(targetUuid) ? playerUuid : targetUuid;
            angerStep(playerUuid, targetUuid, nextResponder, finish);
        });
    }

    private void resolveLaziness(UUID playerUuid, Runnable finish) {
        broadcastText(message(PREFIX + "§7" + playerName(playerUuid)
                + " §f将懒惰放在自己面前"));
        for (UUID opponent : players) {
            if (!opponent.equals(playerUuid) && hasLaziness(opponent)) {
                drawCard(opponent);
            }
        }
        finish.run();
    }

    private boolean hasLaziness(UUID uuid) {
        return !lazinessInFront.getOrDefault(uuid, Collections.emptyList()).isEmpty();
    }

    private void expireLaziness(UUID uuid) {
        List<Integer> expired = lazinessInFront.remove(uuid);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (int value : expired) {
            addDiscard(4, value);
        }
        broadcastText(message(PREFIX + "§7" + playerName(uuid)
                + " §f回合开始，面前的懒惰牌已弃置"));
    }

    private void resolveGreed(
            UUID playerUuid,
            Runnable finish,
            List<IssuedCard> issued,
            Set<Integer> issuedSuits,
            int nonGreedCount
    ) {
        if (issued.size() >= 5) {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("keep", "结束发牌");
            options.put("clear", "清空自己的手牌");
            promptChoice(playerUuid, "贪婪：已发出5张牌，选择结算", options, choice -> {
                if (choice.equals("clear")) {
                    clearHand(playerUuid);
                }
                finish.run();
            });
            return;
        }

        Map<String, String> options = playerOptions(playerUuid);
        if (options.isEmpty()) {
            broadcastText(message(PREFIX + "§6没有可接收贪婪牌的其他在线玩家"));
            finish.run();
            return;
        }
        if (nonGreedCount >= 2
                && !issued.isEmpty()
                && issued.get(issued.size() - 1).suitIndex() != 5) {
            options.put("stop", "停止发牌");
        }
        promptChoice(playerUuid, "贪婪：选择接收牌的玩家", options, choice -> {
            if (choice.equals("stop")) {
                finish.run();
                return;
            }

            UUID recipient = UUID.fromString(choice);
            DrawnCard drawn = drawCardAndGet(recipient);
            if (drawn == null) {
                finish.run();
                return;
            }
            TextComponent dealt = message(PREFIX + "§6" + playerName(playerUuid)
                    + " §f发给了 " + playerName(recipient) + " ");
            dealt.addExtra(cardComponent(drawn.suitIndex(), drawn.value()));
            broadcastText(dealt);
            issued.add(new IssuedCard(recipient, drawn.suitIndex(), drawn.value()));
            if (!issuedSuits.add(drawn.suitIndex())) {
                returnIssuedCards(playerUuid, issued);
                broadcastText(message(PREFIX + "§6已发出两张相同牌名的牌，"
                        + "贪婪立即停止，并将发出的牌收回手牌"));
                finish.run();
                return;
            }
            int nextNonGreedCount = nonGreedCount
                    + (drawn.suitIndex() == 5 ? 0 : 1);
            resolveGreed(
                    playerUuid,
                    finish,
                    issued,
                    issuedSuits,
                    nextNonGreedCount
            );
        });
    }

    private void returnIssuedCards(UUID owner, List<IssuedCard> issued) {
        for (IssuedCard issuedCard : issued) {
            ArrayList<Integer> recipientSuit =
                    playerCards.get(issuedCard.recipient()).get(issuedCard.suitIndex());
            recipientSuit.remove(Integer.valueOf(issuedCard.value()));
            playerCards.get(owner).get(issuedCard.suitIndex()).add(issuedCard.value());
        }
    }

    private void resolveGluttony(UUID playerUuid, UUID targetUuid, Runnable finish) {
        Runnable resolve = () -> {
            if (isOtherPlayer(playerUuid, targetUuid)) {
                ArrayList<ArrayList<Integer>> targetHand = playerCards.get(targetUuid);
                List<CardSelection> available = new ArrayList<>();
                for (int suit = 0; suit < targetHand.size(); suit++) {
                    for (int index = 0; index < targetHand.get(suit).size(); index++) {
                        available.add(new CardSelection(suit, index));
                    }
                }
                if (!available.isEmpty()) {
                    Map<String, String> options = new LinkedHashMap<>();
                    for (int i = 1; i <= available.size(); i++) {
                        options.put(Integer.toString(i), "[" + i + "]");
                    }
                    promptChoice(playerUuid, "暴食：选择 " + playerName(targetUuid)
                            + " §f手牌中的一张（实际随机抽取）", options, choice -> {
                        CardSelection selected = available.get(random.nextInt(available.size()));
                        int card = targetHand.get(selected.suitIndex()).remove(selected.cardIndex());
                        playerCards.get(playerUuid).get(selected.suitIndex()).add(card);
                        broadcastText(message(PREFIX + "§5" + playerName(playerUuid)
                                + " §f从 " + playerName(targetUuid) + " §f手中随机抽取了1张牌"));
                        sendHandControls(targetUuid);
                        sendHandControls(playerUuid);
                        completeGluttony(playerUuid);
                    });
                    return;
                } else {
                    drawCards(playerUuid, 3);
                }
            }
            else {
                drawCards(playerUuid, 3);
            }
            completeGluttony(playerUuid);
        };
        if (isOtherPlayer(playerUuid, targetUuid)) {
            scheduleJudgment(playerUuid, targetUuid, "暴食判定", resolve);
        } else {
            resolve.run();
        }
    }

    private void completeGluttony(UUID playerUuid) {
        broadcastText(message(PREFIX + "§5暴食效果结算完成：" + playerName(playerUuid)
                + " §f获得一次额外回合"));
        finishExtraTurn(playerUuid);
    }

    private void finishExtraTurn(UUID uuid) {
        resolvingEffect = false;
        checkEmptyHand(uuid);
        if (phase == GamePhase.GAME_OVER) {
            return;
        }
        broadcastText(message(PREFIX + "§5" + playerName(uuid)
                + " §f继续进行额外回合"));
        sendHandControls(uuid);
    }

    public boolean resolveChoice(UUID actor, String value) {
        PendingChoice pending = pendingChoices.get(actor);
        if (pending == null || !pending.actor().equals(actor)
                || !pending.values().contains(value)) {
            return false;
        }
        pendingChoices.remove(actor);
        pending.callback().accept(value);
        return true;
    }

    private void promptChoice(
            UUID actor,
            String prompt,
            Map<String, String> options,
            Consumer<String> callback
    ) {
        if (options.isEmpty() || pendingChoices.containsKey(actor)) {
            throw new IllegalStateException("当前玩家已有待处理的选择");
        }
        pendingChoices.put(
                actor,
                new PendingChoice(actor, new HashSet<>(options.keySet()), callback)
        );

        Player player = Bukkit.getPlayer(actor);
        if (player == null || !player.isOnline()) {
            pendingChoices.remove(actor);
            throw new IllegalStateException("需要操作的玩家已离线");
        }

        TextComponent choices = message(PREFIX + "§e" + prompt + "： ");
        for (Map.Entry<String, String> option : options.entrySet()) {
            String label = I18n.tr(option.getValue());
            TextComponent button = new TextComponent("§a[" + label + "]");
            button.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/thedeadlies choose " + option.getKey()
            ));
            button.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(label)
                            .color(ChatColor.GREEN)
                            .create()
            ));
            choices.addExtra(button);
            choices.addExtra(" ");
        }
        player.spigot().sendMessage(choices);
    }

    private Map<String, String> playerOptions(UUID excludedPlayer) {
        Map<String, String> options = new LinkedHashMap<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (!uuid.equals(excludedPlayer) && player != null && player.isOnline()) {
                options.put(uuid.toString(), playerName(uuid));
            }
        }
        return options;
    }

    private String cardName(int suitIndex) {
        return switch (suitIndex) {
            case 1 -> "傲慢";
            case 2 -> "嫉妒";
            case 3 -> "愤怒";
            case 4 -> "懒惰";
            case 5 -> "贪婪";
            case 6 -> "暴食";
            case 7 -> "色欲";
            default -> "未知";
        };
    }

    private void drawCards(UUID uuid, int amount) {
        for (int i = 0; i < amount && drawCard(uuid); i++) {
            // Draw until the requested amount is reached or the pile is empty.
        }
    }

    private void promptCardDiscard(
            UUID actor,
            String prompt,
            Consumer<CardSelection> callback
    ) {
        ArrayList<ArrayList<Integer>> hand = playerCards.get(actor);
        Map<String, String> options = new LinkedHashMap<>();
        for (int suit = 0; suit < hand.size(); suit++) {
            for (int index = 0; index < hand.get(suit).size(); index++) {
                int value = hand.get(suit).get(index);
                options.put(suit + ":" + index, cardNameFor(suit, value));
            }
        }
        promptChoice(actor, prompt, options, choice -> {
            String[] parts = choice.split(":");
            callback.accept(new CardSelection(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])
            ));
        });
    }

    private String cardNameFor(int suitIndex, int value) {
        if (suitIndex == 0) {
            return "净化-0";
        }
        if (suitIndex == 8) {
            return "腐化-8";
        }
        if (suitIndex == 9) {
            return "光环";
        }
        return cardName(suitIndex) + "-" + value;
    }

    private void resolveLust(UUID playerUuid, UUID targetUuid, Runnable finish) {
        if (!isOtherPlayer(playerUuid, targetUuid)) {
            finish.run();
            return;
        }
        scheduleJudgment(playerUuid, targetUuid, "色欲选择", () -> {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("pass", "什么也不做");
            if (getHandSize(playerUuid) > 0 && getHandSize(targetUuid) > 0) {
                options.put("discard", "双方各弃1张牌");
            }
            promptChoice(targetUuid, "色欲：选择如何响应", options, choice -> {
                if (choice.equals("pass")) {
                    finish.run();
                    return;
                }
                promptCardDiscard(playerUuid, "选择弃掉一张牌", first ->
                        promptCardDiscard(targetUuid, "选择弃掉一张牌", second -> {
                            boolean firstLust = first.suitIndex() == 7;
                            boolean secondLust = second.suitIndex() == 7;
                            if (firstLust) {
                                drawCards(targetUuid, 3);
                            }
                            if (secondLust) {
                                drawCards(playerUuid, 3);
                            }
                            finish.run();
                        })
                );
            });
        });
    }

    private boolean isOtherPlayer(UUID playerUuid, UUID targetUuid) {
        Player target = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
        return targetUuid != null && !playerUuid.equals(targetUuid)
                && players.contains(targetUuid)
                && target != null && target.isOnline();
    }

    private boolean hasCard(UUID uuid, int suitIndex) {
        return !playerCards.get(uuid).get(suitIndex).isEmpty();
    }

    private void takeHalo(UUID uuid) {
        if (haloPublic) {
            playerCards.get(uuid).get(9).add(0);
            haloPublic = false;
            broadcastText(message(PREFIX + "§b" + playerName(uuid)
                    + " §f将光环拿入手牌"));
            return;
        }
        for (UUID player : players) {
            if (!player.equals(uuid) && hasCard(player, 9)) {
                playerCards.get(player).get(9).remove(0);
                playerCards.get(uuid).get(9).add(0);
                broadcastText(message(PREFIX + "§b" + playerName(uuid)
                        + " §f从 " + playerName(player) + " §f手中夺得光环"));
                return;
            }
        }
    }

    private void clearHand(UUID uuid) {
        ArrayList<ArrayList<Integer>> hand = playerCards.get(uuid);
        for (int suit = 0; suit < hand.size(); suit++) {
            while (!hand.get(suit).isEmpty()) {
                addDiscard(suit, hand.get(suit).remove(hand.get(suit).size() - 1));
            }
        }
    }

    private void checkEmptyHand(UUID uuid) {
        if (getHandSize(uuid) != 0) {
            return;
        }
        int count = emptyHandCount.merge(uuid, 1, Integer::sum);
        int level = Math.max(0, sinLevels.get(uuid) - 2);
        sinLevels.put(uuid, level);
        broadcastText(message(PREFIX + "§e" + playerName(uuid)
                + " §f打空了手牌，罪恶值降为 §c" + level));
        for (int i = 0; i < level; i++) {
            drawCard(uuid);
        }
        if (count >= 3 || level == 0) {
            phase = GamePhase.GAME_OVER;
            broadcastText(message(PREFIX + "§a" + playerName(uuid) + " §f获得胜利"));
            pendingChoices.clear();
            resolvingEffect = false;
            notifyGameFinished();
        }
    }

    public int getRound() {
        return round;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void finishGame() {
        phase = GamePhase.GAME_OVER;
        pendingChoices.clear();
        resolvingEffect = false;
        notifyGameFinished();
    }

    private void notifyGameFinished() {
        if (!finishNotified) {
            finishNotified = true;
            onGameFinished.run();
        }
    }

    private record PendingChoice(
            UUID actor,
            Set<String> values,
            Consumer<String> callback
    ) {
    }

    public enum GamePhase {
        CREATED,
        DEALING,
        PLAYER_TURN,
        GAME_OVER
    }

    private record CardDescription(
            String name,
            ChatColor color,
            ChatColor hoverColor,
            String effect,
            boolean numbered
    ) {
        private CardDescription(
                String name,
                ChatColor color,
                ChatColor hoverColor,
                String effect
        ) {
            this(name, color, hoverColor, effect, true);
        }
    }
}