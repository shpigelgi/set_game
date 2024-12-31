package bguspl.set.ex;

import bguspl.set.Env;

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

    /**
     * Array of slot objects
     */
    protected final Slot[] slots;

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
        slots = new Slot[slotToCard.length];
        for (int i = 0; i < slotToCard.length; i++) {
            slots[i] = new Slot(i);
            if (slotToCard[i] != null) {
                slots[i].setCardId(slotToCard[i]);
            }
        }
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
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
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

        // TODO implement

        slots[slot].setCardId(card);

        //place card in ui
        env.ui.placeCard(card, slot);
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

        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;
        slots[slot].setCardId(-1);

        //remove card in ui
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        slots[slot].placeToken(player);

        //place token in ui
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     * @post slots[slot].getTokens().length = @pre slots[slot].getTokens().length - 1 || @pre slots[slot].getTokens().length == 0
     * @post Arrays.binarySearch(slots[slot].getTokens ()), player) < 0
     */
    public boolean removeToken(int player, int slot) {
        boolean canRemove = slots[slot].removeToken(player);
        if (canRemove) {

            //remove token in ui
            env.ui.removeToken(player, slot);
        }
        return canRemove;
    }

    /**
     * Removes all the tokens from a grid slot.
     *
     * @param slot - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     * @post slots[slot].getTokens().length == 0
     */
    public void removeTokens(int slot) {
        slots[slot].removeTokens();
        env.ui.removeTokens(slot);
    }

    /**
     * find all empty slots in the table
     *
     * @return an array of slots that are empty
     */
    public int[] findEmptySlots() {
        int counter = 0;
        for (Integer slot : slotToCard) {
            if (slot == null) {
                counter++;
            }
        }
        int[] empty = new int[counter];
        counter = 0;
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] == null) {
                empty[counter] = i;
                counter++;
            }
        }
        return empty;
    }

    public int[] findFullSlots() {
        int counter = 0;
        for (Integer slot : slotToCard) {
            if (slot != null) {
                counter++;
            }
        }
        int[] full = new int[counter];
        counter = 0;
        for (int i = 0; i < slotToCard.length && counter < full.length; i++) {
            if (slotToCard[i] != null) {
                full[counter] = i;
                counter++;
            }
        }
        return full;
    }

    public Integer[] getSlotToCard() {
        return slotToCard;
    }

    public Integer[] getCardToSlot() {
        return cardToSlot;
    }

    public Slot[] getSlots() {
        return slots;
    }
}
