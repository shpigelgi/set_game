package bguspl.set.ex;

/**
 * this class represents a set that needs to be checked
 */
public class Check {
    /**
     * the cards to check
     */
    private final int[] cardsToCheck;

    /**
     * the player that sent the check
     */
    private final Player player;

    public Check(int[] cardsToCheck, Player player) {
        this.cardsToCheck = cardsToCheck;
        this.player = player;
    }

    public int[] getCardsToCheck() {
        return cardsToCheck;
    }

    public Player getPlayer() {
        return player;
    }
}
