package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private final int SECOND = 1000;

    private boolean reset;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        reset = false;

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : players) {
            Thread t = new Thread(p);
            t.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        if (env.config.hints) table.hints();

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(reset);
            reset = false;
            testSetDealer();
            //removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        while (!table.playersOrderList.isEmpty()) {
            int tmp = table.playersOrderList.remove(table.playersOrderList.size() - 1);
            players[tmp].terminate();
            try {
                players[tmp].getPlayerThread().join();
            } catch (InterruptedException ignored) {
            }
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    public void removeCards(int arr[]) {
        synchronized (table.ignoreAllPlayersActions) {
            table.ignoreAllPlayersActions = true;
        }
        List<Integer> indexs = new ArrayList<Integer>();
        for (int i = 0; i < arr.length; i++) {
            indexs.add(table.cardToSlot[arr[i]]);
        }

        for (int i = 0; i < arr.length; i++) {
            int nextSlot = randomFromList(indexs);
            table.removeCard(nextSlot);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    protected void placeCardsOnTable() {
        if (terminate) return;
        List<Integer> emptySlots = new ArrayList<>();
        int i = 0;
        for (Integer slot : table.slotToCard) {
            if (slot == null)
                emptySlots.add(i);
            i++;
        }
        while (table.countCards() != (env.config.columns * env.config.rows) && deck.size() != 0) {
            int nextRandomSlot = randomFromList(emptySlots);
            int deckSize = deck.size();
            int randomCardIndex = getRandomNumber(0, deckSize);
            int randomCardId = deck.get(randomCardIndex);
            table.placeCard(randomCardId, nextRandomSlot);
            deck.remove(randomCardIndex);
        }
        synchronized (table.ignoreAllPlayersActions) {
            table.ignoreAllPlayersActions = false;
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(SECOND / 100);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (!reset) {
            if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
                env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), true);
            } else {
                env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
            }
        } else {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }

        for (Player p : players) {
            if (p.playerTimer - System.currentTimeMillis() > 1000) {
                env.ui.setFreeze(p.id, Math.max(p.playerTimer - System.currentTimeMillis(), 0));
            } else
                env.ui.setFreeze(p.id, 0);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        synchronized (table.ignoreAllPlayersActions) {
            table.ignoreAllPlayersActions = true;
        }
        List<Integer> indexs = new ArrayList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++) {
            indexs.add(i);
        }
        while (!indexs.isEmpty()) {
            int nextRandomSlot = randomFromList(indexs);
            if (table.slotToCard[nextRandomSlot] != null) {
                deck.add(table.slotToCard[nextRandomSlot]);
                table.removeCard(nextRandomSlot);
            }
            reset = true;
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int highScore = -1;
        for (Player p : players) {
            int tmpScore = p.score();
            if (tmpScore > highScore)
                highScore = tmpScore;
        }
        int counter = 0;
        for (Player p : players) {
            if (p.score() == highScore)
                counter++;
        }
        int[] winners = new int[counter];
        int i = 0;
        for (Player p : players) {
            if (p.score() == highScore) {
                winners[i] = p.id;
                i++;
            }
        }
        env.ui.announceWinner(winners);
    }


    public void testSetDealer() {
        if (!table.setsToTest.isEmpty()) {
            synchronized (table.setsToTest) {
                int[] tmp = table.setsToTest.remove(0);
                int id = tmp[3];
                if (table.playersTokens[id].size() == 3) {
                    int[] toTest = new int[3];
                    for (int i = 0; i < 3; i++) {
                        toTest[i] = tmp[i];
                    }
                    boolean ans = env.util.testSet(toTest);
                    if (ans) {
                        if (deck.size() > 0)
                            reset = true;
                        players[id].point();
                        table.playersTested[id] = true;
                        removeCards(toTest);
                        existingSets();

                    } else {
                        reset = false;
                        players[id].penalty();
                        table.playersTested[id] = true;
                    }
                }
            }
        }
    }

    public void existingSets() {
        List deckAndTableCards = new ArrayList<>(deck);
        for (Integer i : table.slotToCard) {
            if (i != null)
                deckAndTableCards.add(i);
        }
        if (env.util.findSets(deckAndTableCards, 1).size() == 0) {
            if (env.config.turnTimeoutMillis <= 0)
                terminate();
        }
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    private Integer randomFromList(List<Integer> list) {
        int len = list.size();
        int nextRandomSlot = getRandomNumber(0, len);
        return (list.remove(nextRandomSlot));
    }

}
