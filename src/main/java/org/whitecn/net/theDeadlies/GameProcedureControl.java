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
            throw new IllegalStateException(I18n.get(Messages.GAME_HAS_ALREADY));
        }

        if (players.size() < 2) {
            throw new IllegalStateException(I18n.get(Messages.LEAST_TWO_PLAYERS_2));
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
        broadcastText(message(PREFIX + I18n.get(Messages.GAME_HAS_STARTED_3)));
        broadcastText(message(PREFIX + I18n.get(Messages.NOW) + playerName(getCurrentPlayer())
                + I18n.get(Messages.TURN_PLAY_LEAST)));
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
                            I18n.get(Messages.NOT_ENOUGH_CARDS)
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

        broadcastText(message(PREFIX + "§e" + playerName(uuid) + I18n.get(Messages.FINISHED_TURN)));
        broadcastText(message(PREFIX + I18n.get(Messages.NOW_2) + playerName(getCurrentPlayer())
                + I18n.get(Messages.TURN_PLAY_LEAST)));
        expireLaziness(getCurrentPlayer());
        sendHandControls(getCurrentPlayer());
    }

    /**
     * 广播“玩家打出了某张牌”的描述消息
     */
    public void announceCardPlayed(UUID playerUuid, int suitIndex, int cardValue) {
        TextComponent text = message(PREFIX + "§e" + playerName(playerUuid)
                + I18n.get(Messages.PLAYED));
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
                + I18n.get(Messages.SELECTED) + playerName(targetUuid)
                + I18n.get(Messages.NEXT) + judgmentDescription + "§f"));
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

                    broadcastText(message(PREFIX + I18n.get(Messages.RESOLUTION_BEGINS)
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
        TextComponent text = message(PREFIX + I18n.get(Messages.HAND_CLICK_CARD));
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
                + I18n.get(Messages.DREW_CARD)));
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
        TextComponent card = new TextComponent("[" + cardName + "]");
        card.setColor(description.color);
        card.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(description.effect)
                        .color(description.hoverColor)
                        .create()
        ));
        return card;
    }

    private CardDescription cardDescription(int suitIndex, int cardValue) {
        return switch (suitIndex) {
            case 0 -> new CardDescription(
                    I18n.get(Messages.PURIFICATION),
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    I18n.get(Messages.TAKE_HALO_HAND)
            );
            case 1 -> new CardDescription(
                    I18n.get(Messages.PRIDE),
                    ChatColor.RED,
                    ChatColor.DARK_RED,
                    I18n.get(Messages.ASK_ANOTHER_PLAYER)
            );
            case 2 -> new CardDescription(
                    I18n.get(Messages.ENVY),
                    ChatColor.GREEN,
                    ChatColor.DARK_GREEN,
                    I18n.get(Messages.DRAW_CARDS_THEN)
            );
            case 3 -> new CardDescription(
                    I18n.get(Messages.WRATH),
                    ChatColor.DARK_RED,
                    ChatColor.RED,
                    I18n.get(Messages.CHOOSE_ANOTHER_PLAYER)
                            + I18n.get(Messages.REPEAT_UNTIL_PLAYER)
            );
            case 4 -> new CardDescription(
                    I18n.get(Messages.SLOTH),
                    ChatColor.GRAY,
                    ChatColor.DARK_GRAY,
                    I18n.get(Messages.PLACE_SLOTH_FRONT)
            );
            case 5 -> new CardDescription(
                    I18n.get(Messages.GREED),
                    ChatColor.GOLD,
                    ChatColor.YELLOW,
                    I18n.get(Messages.DEAL_CARDS_ONE)
                            + I18n.get(Messages.TWO_NON_GREED)
            );
            case 6 -> new CardDescription(
                    I18n.get(Messages.GLUTTONY),
                    ChatColor.DARK_PURPLE,
                    ChatColor.LIGHT_PURPLE,
                    I18n.get(Messages.TAKE_RANDOM_CARD)
            );
            case 7 -> new CardDescription(
                    I18n.get(Messages.LUST),
                    ChatColor.LIGHT_PURPLE,
                    ChatColor.DARK_PURPLE,
                    I18n.get(Messages.CHOOSE_ANOTHER_PLAYER_2)
            );
            case 8 -> new CardDescription(
                    I18n.get(Messages.CORRUPTION),
                    ChatColor.DARK_GRAY,
                    ChatColor.GRAY,
                    I18n.get(Messages.CARD_HAS_ALL)
            );
            case 9 -> new CardDescription(
                    I18n.get(Messages.HALO),
                    ChatColor.AQUA,
                    ChatColor.BLUE,
                    I18n.get(Messages.EMPTY_HAND_PLAYED),
                    false
            );
            default -> new CardDescription(
                    I18n.get(Messages.UNKNOWN),
                    ChatColor.WHITE,
                    ChatColor.GRAY,
                    I18n.get(Messages.CARD_EFFECT_NOT),
                    true
            );
        };
    }

    private TextComponent message(String content) {
        return new TextComponent(content);
    }

    private void broadcastText(TextComponent text) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                player.spigot().sendMessage(text);
            }
        }
    }

    private String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? I18n.get(Messages.OFFLINE_PLAYER) : player.getName();
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
                    + I18n.get(Messages.DISCARDED));
            discarded.addExtra(cardComponent(suitIndex, card));
            broadcastText(discarded);
        } else {
            haloPublic = true;
            broadcastText(message(PREFIX + I18n.get(Messages.HALO_DISCARDED_RETURNED)));
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
            promptChoice(uuid, I18n.get(Messages.SAME_NUMBER_PLAY), options, choice -> {
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
            promptChoice(uuid, I18n.get(Messages.CORRUPTION_CHOOSE_SUIT), options, choice ->
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
                options.put("self", I18n.get(Messages.YOURSELF_DRAW));
            }
            if (options.isEmpty()) {
                return false;
            }
            final int selectedEffectSuit = effectSuit;
            final int selectedEffectValue = effectValue;
            promptChoice(uuid, I18n.get(Messages.CHOOSE_CARD_TARGET), options, choice -> {
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
            broadcastText(message(PREFIX + I18n.get(Messages.HALO_RETURNED_CENTER)));
            finish.run();
            return;
        }
        if (suitIndex == 8) {
            if (corruptionSuit < 1 || corruptionSuit > 7) {
                broadcastText(message(PREFIX + I18n.get(Messages.CORRUPTION_REQUIRES_SUIT)));
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
        scheduleJudgment(playerUuid, targetUuid, I18n.get(Messages.PRIDE_RESOLUTION), () -> {
            Map<String, String> options = new LinkedHashMap<>();
            if (hasCard(targetUuid, 1)) {
                options.put("show", I18n.get(Messages.REVEAL_PRIDE));
            }
            options.put("refuse", I18n.get(Messages.NOT_REVEAL));
            promptChoice(targetUuid, I18n.get(Messages.PRIDE_CHOOSE_WHETHER), options, choice -> {
                if (choice.equals("show") && hasCard(targetUuid, 1)) {
                    broadcastText(message(PREFIX + "§e" + playerName(targetUuid)
                            + I18n.get(Messages.REVEALED_PRIDE) + playerName(playerUuid) + I18n.get(Messages.DRAWS_CARD)));
                    drawCard(playerUuid);
                } else {
                    broadcastText(message(PREFIX + "§e" + playerName(targetUuid)
                            + I18n.get(Messages.DID_NOT_REVEAL)));
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
        options.put("skip", I18n.get(Messages.NOT_SWAP));
        promptChoice(playerUuid, I18n.get(Messages.ENVY_CHOOSE_PLAYER), options, choice -> {
            if (!choice.equals("skip")) {
                UUID target = UUID.fromString(choice);
                ArrayList<ArrayList<Integer>> ownHand = playerCards.get(playerUuid);
                ArrayList<ArrayList<Integer>> targetHand = playerCards.get(target);
                playerCards.put(playerUuid, targetHand);
                playerCards.put(target, ownHand);
                broadcastText(message(PREFIX + "§a" + playerName(playerUuid)
                        + I18n.get(Messages.SWAPPED_HANDS) + playerName(target) + I18n.get(Messages.MESSAGE_149)));
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
        scheduleJudgment(playerUuid, targetUuid, I18n.get(Messages.WRATH_RESOLUTION), () ->
                angerStep(playerUuid, targetUuid, targetUuid, finish)
        );
    }

    private void angerStep(UUID playerUuid, UUID targetUuid, UUID responder, Runnable finish) {
        drawCard(responder);
        drawCard(responder);
        Map<String, String> options = new LinkedHashMap<>();
        if (hasCard(responder, 3)) {
            options.put("discard", I18n.get(Messages.DISCARD_WRATH_OTHER));
        }
        options.put("stop", I18n.get(Messages.STOP_WRATH_LOOP));
        promptChoice(responder, I18n.get(Messages.WRATH_DISCARD_CONTINUE), options, choice -> {
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
                + I18n.get(Messages.PLACED_SLOTH_FRONT)));
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
                + I18n.get(Messages.STARTED_TURN_DISCARDED)));
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
            options.put("keep", I18n.get(Messages.STOP_DEALING));
            options.put("clear", I18n.get(Messages.EMPTY_HAND));
            promptChoice(playerUuid, I18n.get(Messages.GREED_CARDS_DEALT), options, choice -> {
                if (choice.equals("clear")) {
                    clearHand(playerUuid);
                }
                finish.run();
            });
            return;
        }

        Map<String, String> options = playerOptions(playerUuid);
        if (options.isEmpty()) {
            broadcastText(message(PREFIX + I18n.get(Messages.THERE_NO_OTHER)));
            finish.run();
            return;
        }
        if (nonGreedCount >= 2
                && !issued.isEmpty()
                && issued.get(issued.size() - 1).suitIndex() != 5) {
            options.put("stop", I18n.get(Messages.STOP_DEALING_2));
        }
        promptChoice(playerUuid, I18n.get(Messages.GREED_CHOOSE_PLAYER), options, choice -> {
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
                    + I18n.get(Messages.DEALT_CARD) + playerName(recipient) + " ");
            dealt.addExtra(cardComponent(drawn.suitIndex(), drawn.value()));
            broadcastText(dealt);
            issued.add(new IssuedCard(recipient, drawn.suitIndex(), drawn.value()));
            if (!issuedSuits.add(drawn.suitIndex())) {
                returnIssuedCards(playerUuid, issued);
                broadcastText(message(PREFIX + I18n.get(Messages.TWO_CARDS_SAME)
                        + I18n.get(Messages.GREED_STOPS_IMMEDIATELY)));
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
                    promptChoice(playerUuid, I18n.get(Messages.GLUTTONY_CHOOSE) + playerName(targetUuid)
                            + I18n.get(Messages.HAND_CARD_SELECTED), options, choice -> {
                        CardSelection selected = available.get(random.nextInt(available.size()));
                        int card = targetHand.get(selected.suitIndex()).remove(selected.cardIndex());
                        playerCards.get(playerUuid).get(selected.suitIndex()).add(card);
                        broadcastText(message(PREFIX + "§5" + playerName(playerUuid)
                                + I18n.get(Messages.TOOK_RANDOM_CARD) + playerName(targetUuid) + I18n.get(Messages.HAND)));
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
            scheduleJudgment(playerUuid, targetUuid, I18n.get(Messages.GLUTTONY_RESOLUTION), resolve);
        } else {
            resolve.run();
        }
    }

    private void completeGluttony(UUID playerUuid) {
        broadcastText(message(PREFIX + I18n.get(Messages.GLUTTONY_RESOLVED) + playerName(playerUuid)
                + I18n.get(Messages.GETS_EXTRA_TURN)));
        finishExtraTurn(playerUuid);
    }

    private void finishExtraTurn(UUID uuid) {
        resolvingEffect = false;
        checkEmptyHand(uuid);
        if (phase == GamePhase.GAME_OVER) {
            return;
        }
        broadcastText(message(PREFIX + "§5" + playerName(uuid)
                + I18n.get(Messages.CONTINUES_EXTRA_TURN)));
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
            throw new IllegalStateException(I18n.get(Messages.PLAYER_ALREADY_HAS));
        }
        pendingChoices.put(
                actor,
                new PendingChoice(actor, new HashSet<>(options.keySet()), callback)
        );

        Player player = Bukkit.getPlayer(actor);
        if (player == null || !player.isOnline()) {
            pendingChoices.remove(actor);
            throw new IllegalStateException(I18n.get(Messages.PLAYER_NEEDS_ACT));
        }

        TextComponent choices = message(
                PREFIX + "§e" + prompt + I18n.get(Messages.MESSAGE_001) + " "
        );
        for (Map.Entry<String, String> option : options.entrySet()) {
            String label = option.getValue();
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
            case 1 -> I18n.get(Messages.PRIDE);
            case 2 -> I18n.get(Messages.ENVY);
            case 3 -> I18n.get(Messages.WRATH);
            case 4 -> I18n.get(Messages.SLOTH);
            case 5 -> I18n.get(Messages.GREED);
            case 6 -> I18n.get(Messages.GLUTTONY);
            case 7 -> I18n.get(Messages.LUST);
            default -> I18n.get(Messages.UNKNOWN);
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
            return I18n.get(Messages.PURIFICATION_2);
        }
        if (suitIndex == 8) {
            return I18n.get(Messages.CORRUPTION_2);
        }
        if (suitIndex == 9) {
            return I18n.get(Messages.HALO);
        }
        return cardName(suitIndex) + "-" + value;
    }

    private void resolveLust(UUID playerUuid, UUID targetUuid, Runnable finish) {
        if (!isOtherPlayer(playerUuid, targetUuid)) {
            finish.run();
            return;
        }
        scheduleJudgment(playerUuid, targetUuid, I18n.get(Messages.LUST_CHOICE), () -> {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("pass", I18n.get(Messages.NOTHING));
            if (getHandSize(playerUuid) > 0 && getHandSize(targetUuid) > 0) {
                options.put("discard", I18n.get(Messages.BOTH_PLAYERS_DISCARD));
            }
            promptChoice(targetUuid, I18n.get(Messages.LUST_CHOOSE_HOW), options, choice -> {
                if (choice.equals("pass")) {
                    finish.run();
                    return;
                }
                promptCardDiscard(playerUuid, I18n.get(Messages.CHOOSE_CARD_DISCARD), first ->
                        promptCardDiscard(targetUuid, I18n.get(Messages.CHOOSE_CARD_DISCARD), second -> {
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
                    + I18n.get(Messages.TOOK_HALO_HAND)));
            return;
        }
        for (UUID player : players) {
            if (!player.equals(uuid) && hasCard(player, 9)) {
                playerCards.get(player).get(9).remove(0);
                playerCards.get(uuid).get(9).add(0);
                broadcastText(message(PREFIX + "§b" + playerName(uuid)
                        + I18n.get(Messages.TOOK_RANDOM_CARD) + playerName(player) + I18n.get(Messages.TOOK_HALO_ANOTHER)));
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
                + I18n.get(Messages.EMPTIED_HAND_SIN) + level));
        for (int i = 0; i < level; i++) {
            drawCard(uuid);
        }
        if (count >= 3 || level == 0) {
            phase = GamePhase.GAME_OVER;
            broadcastText(message(PREFIX + "§a" + playerName(uuid) + I18n.get(Messages.WINS_GAME)));
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