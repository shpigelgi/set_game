package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TableTest {


    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    @Mock
    private UserInterface ui;

    @Mock
    private Util util;

    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue((table.slotToCard.length == table.cardToSlot.length) && (table.cardToSlot.length == table.slots.length));
        for (int i = 0; i < table.slotToCard.length; i++) {
            assertTrue(i == table.cardToSlot[table.slotToCard[i]]);
            assertTrue(table.slotToCard[i] == table.slots[i].getCardId());
        }
    }

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

        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];

        Env env = new Env(logger, config, ui, util);
        table = new Table(env, slotToCard, cardToSlot);

        //assertInvariants();
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

    private void placeSomeCardsAndAssert() throws InterruptedException {
        table.placeCard(8, 2);

        assertEquals(8, (int) slotToCard[2]);
        assertEquals(2, (int) cardToSlot[8]);
    }

    @Test
    void countCards_NoSlotsAreFilled() {

        assertEquals(0, table.countCards());
    }

    @Test
    void countCards_SomeSlotsAreFilled() {

        int slotsFilled = fillSomeSlots();
        assertEquals(slotsFilled, table.countCards());
    }

    @Test
    void countCards_AllSlotsAreFilled() {

        fillAllSlots();
        assertEquals(slotToCard.length, table.countCards());
    }

    @Test
    void placeCard_SomeSlotsAreFilled() throws InterruptedException {

        fillSomeSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void placeCard_AllSlotsAreFilled() throws InterruptedException {

        fillAllSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void removeToken_ThereAreTokens() {
        table.placeToken(1,3);
        int preLength = table.slots[3].getTokens().length;
        table.removeToken(1,3);
        int postLength = table.slots[3].getTokens().length;
        assertTrue(preLength == postLength + 1 && Arrays.binarySearch(table.slots[3].getTokens(),1) < 0);
    }

    @Test
    void removeToken_ThereArentTokens() {
        table.removeToken(1,3);
        assertTrue(table.slots[3].getTokens().length == 0 && Arrays.binarySearch(table.slots[3].getTokens(),1) < 0);
    }

    @Test
    void removeTokens_ThereAreTokens() {
        table.placeToken(1,3);
        table.placeToken(2,3);
        table.placeToken(4,3);
        table.removeTokens(3);
        assertTrue(table.slots[3].getTokens().length == 0);
    }

    @Test
    void removeTokens_ThereArentTokens() {
        table.removeTokens(3);
        assertTrue(table.slots[3].getTokens().length == 0);
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
