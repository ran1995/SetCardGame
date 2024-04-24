package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class DealerTest {
    Dealer dealer;
    Env env;
    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    private List<Integer> deck;

     Player[] players;



    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];
        env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
        players = new Player[config.players];
        dealer = new Dealer(env, table, players);
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    private void initPlayers(){
        for (int i = 0; i < env.config.players; i++) {
            players[i] = new Player(env, dealer, table, i, true);
        }
    }

    private int fillSomeSlots() {
        slotToCard[1] = 3;
        slotToCard[2] = 5;
        cardToSlot[3] = 1;
        cardToSlot[5] = 2;

        return 2;
    }

    private void fillAllSlots() {
        for (int i = 0; i < slotToCard.length; ++i) {
            slotToCard[i] = i;
            cardToSlot[i] = i;
        }
    }

    @Test //ADDED
    void removeAllCardsFromTable_AllCardsOnTable() {

        fillAllSlots();
        assertEquals(4,table.countCards());
        dealer.removeAllCardsFromTable();
        assertEquals(0,table.countCards());

    }

    @Test //ADDED
    void removeAllCardsFromTable_CardsOnTable() {
        fillSomeSlots();
        assertEquals(2,table.countCards());
        dealer.removeAllCardsFromTable();
        assertEquals(0,table.countCards());

    }



    @Test //ADDED
    void removeAllCardsFromTable_NoCardsOnTable() {
        dealer.removeAllCardsFromTable();
        assertEquals(0,table.countCards());
    }

    @Test //ADDED
    void placeCardsOnTable_SanityCheck(){
        int expectedCards = table.countCards()+4;
        dealer.placeCardsOnTable();
        assertEquals(expectedCards, table.countCards());
    }

    @Test //ADDED
    void placeCardsOnTable_TableNotEmpty(){
        table.placeCard(1,1);
        int expectedCards = table.countCards()+3;
        dealer.placeCardsOnTable();
        assertEquals(expectedCards, table.countCards());
    }

    @Test //ADDED
    void placeCardsOnTable_TableIsFull(){
        fillAllSlots();
        int expectedCards = table.countCards();
        dealer.placeCardsOnTable();
        assertEquals(expectedCards, table.countCards());
    }







    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }
}
