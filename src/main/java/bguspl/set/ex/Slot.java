package bguspl.set.ex;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * this class represents a slot
 */
public class Slot {

    /**
     * the tokens placed on the slot
     */
    private final ConcurrentLinkedDeque<Integer> tokens;

    /**
     * the id of the card on the slot
     */
    private int cardId;

    public Slot() {
        cardId = -1;
        tokens = new ConcurrentLinkedDeque<>();
    }

    /**
     * place a token on the slot
     *
     * @param player the player whose token we are placing
     */
    public void placeToken(int player) {
        tokens.add(player);
    }

    /**
     * remove a player's token from the slot
     *
     * @param player the player whose token we are removing
     * @return true if the removal was successful, false otherwise
     */
    public boolean removeToken(int player) {
        return tokens.remove(player);
    }

    /**
     * remove all tokens from the slot
     */
    public void removeTokens() {
        tokens.clear();
    }

    public Integer[] getTokens() {
        return tokens.toArray(new Integer[0]);
    }

    public int getCardId() {
        return cardId;
    }

    public void setCardId(int cardId) {
        this.cardId = cardId;
    }

}
