# TheDeadlies | Journey of Atonement

A Journey of Atonement board-game plugin for Minecraft 1.20.1. Players play cards, choose targets, and resolve card effects using clickable components in chat.

**中文版本：[README.md](README.md)**

## Requirements and Installation

- Server: Paper / Purpur 1.20.1
- Java: 17
- Put `build/libs/TheDeadlies-1.0.0-all.jar` in the server's `plugins` folder, then restart the server.

After the first launch, set the interface language in `plugins/TheDeadlies/config.yml` and restart the server for the change to take effect:

```yaml
language: EN # Supported values: ZH_CN (default) or EN
```

Build from source:

```text
gradlew.bat shadowJar
```

## Commands

| Command | Purpose |
| --- | --- |
| `/thedeadlies invite <player> [player...]` | Invite one or more online players; every invited player must respond to the invitation. |
| `/thedeadlies joingame <host>` | Ask the host to join a room that has not started. |
| `/thedeadlies quitgame` | Leave the room or current game; if the host leaves during a game, the game ends and the room is disbanded. |
| `/thedeadlies startgame` | The host starts the game; at least two players must be online. |
| `/thedeadlies endgame` | End the current game but keep the room, so another game can be started. |

Accepting or declining invitations and join requests, playing cards, and responding to effects can all be done by clicking chat components. You must play at least one card on your turn; you cannot skip voluntarily.

## Rules

- The game has 52 cards: 1 Purification, 1 Corruption, 7 cards for each of the seven deadly sins, and 1 Halo. The Halo is placed face up separately; the other 51 cards form the deck.
- Each player starts with 6 cards and a sin value of 6.
- Play one or more cards each turn. Multiple cards must have the same number, the same name, or form a numeric straight of at least three cards. Resolve the card with the highest number. If multiple cards tie for the highest number, choose one of them to resolve.
- When your hand is empty, your sin value decreases by 2, and you draw cards equal to your current sin value. You win when your sin value reaches 0 or when you empty your hand for the third time.
- When the deck runs out, shuffle the discard pile back into the deck. The Halo is never added to the deck.

## Card Effects

| Card | Effect |
| --- | --- |
| Pride | Ask another player whether they will reveal a Pride card. If they do, you draw 1 card; otherwise, they draw 1 card. |
| Envy | Draw 2 cards. If you have no Envy in your hand, you may exchange hands with another player. |
| Wrath | Choose another player; they draw 2 cards. Then the two of you take turns choosing to discard a Wrath card to continue, or to stop the loop. |
| Sloth | Place this card in front of yourself. Opponents with a Sloth in front of them draw 1 card. Discard this card at the start of your next turn. |
| Greed | Choose another player and deal cards to them one at a time. After dealing two non-Greed cards, you may stop if the last card dealt was not Greed. Two cards with the same name force you to stop and take back the cards you dealt. After dealing 5 cards, you may empty your hand. |
| Gluttony | Either randomly draw 1 card from another player's hand or draw 3 cards yourself, then take an extra turn. |
| Lust | Choose another player. They may do nothing, or you both discard 1 card each. Whoever discards Lust makes the other player draw 3 cards. |
| Purification (0) | Take the Halo into your hand, even if another player currently has it. |
| Corruption (8) | When played, choose one of the seven deadly sins and perform its effect. |
| Halo | Empty your hand. After being played or discarded, return the Halo to the center of the table face up. |

> The maximum sin value is 6; an optional variant allows a maximum of 4.

## Controls

After the game starts, your hand is displayed as clickable cards. Click a card to play it. When prompted to choose a target, a Corruption suit, or a response to a card effect, click the corresponding option. Hover over a card to view its effect.
