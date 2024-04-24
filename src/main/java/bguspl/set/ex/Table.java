package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected final List<Integer>[] playersTokens;

    protected List<int[]> setsToTest;

    protected boolean[] playersTested;

    protected List<Integer> playersOrderList;

    protected volatile Boolean ignoreAllPlayersActions;


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        playersTokens = new ArrayList[env.config.players];
        for (int i = 0; i < playersTokens.length; i++) {
            playersTokens[i] = new ArrayList<Integer>();
        }

        setsToTest = new ArrayList<>();

        playersTested = new boolean[env.config.players];
        for (int i = 0; i < playersTested.length; i++) {
            playersTested[i] = true;
        }

        playersOrderList = new ArrayList<Integer>();

        ignoreAllPlayersActions = true;

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        if (env.config.hints) {
            List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
            env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
                StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
                List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
                int[][] features = env.util.cardsToFeatures(set);
                System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
            });
        }
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot); // added
    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        synchronized (playersTokens) {

            for (int i = 0; i < env.config.players; i++) {
                if (playersTokens[i].contains(slot)) {
                    removeToken(i, slot);
                    env.ui.removeToken(i, slot);
                }
            }
            env.ui.removeCard(slot);
            slotToCard[slot] = null;
        }
    }

    public void keyPressed(int player, int slot) {
        if (!playersTokens[player].contains(slot)) {
            if (playersTokens[player].size() < 3)
                placeToken(player, slot);
        } else {
            removeToken(player, slot);
        }
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */

    public void placeToken(int player, int slot) {
        synchronized (playersTokens) {
            if (slotToCard[slot] != null) {
                playersTokens[player].add(slot);
                env.ui.placeToken(player, slot);

                if (playersTokens[player].size() == 3) {
                    addSet(player);
                }
            }
        }
    }

    public void addSet(int id) {
        int[] tmp = new int[4];
        synchronized (playersTokens) {
            for (int i = 0; i < 3; i++) {
                if(playersTokens[id].size()<=i)
                    return;
                if(playersTokens[id].get(i) == null)
                    return;
                if (slotToCard[playersTokens[id].get(i)] == null)
                    return;
                tmp[i] = slotToCard[playersTokens[id].get(i)];
            }
            playersTested[id] = false;
            tmp[3] = id;
            setsToTest.add(tmp);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */

    public boolean removeToken(int player, int slot) {
        if (!playersTokens[player].contains(slot) ) return false;
        playersTokens[player].remove((Integer) slot);
        env.ui.removeToken(player, slot);
        return true;


    }
}
