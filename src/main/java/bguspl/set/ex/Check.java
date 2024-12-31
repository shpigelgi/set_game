package bguspl.set.ex;

/**
 * this class represents a set that needs to be checked
 */
public class Check {
    /**
     * the cards to check
     */
    private int[] cardsToCheck;

    /**
     * the thread of the player
     */
    private Thread playerThread;

    /**
     * the player that sent the check
     */
    private Player player;

    public Check(int[] cardsToCheck, Thread playerThread, Player player) {
        this.cardsToCheck = cardsToCheck;
        this.playerThread = playerThread;
        this.player = player;
    }

    public int[] getCardsToCheck() {
        return cardsToCheck;
    }

    public Thread getPlayerThread() {
        return playerThread;
    }

    public Player getPlayer() {
        return player;
    }
}
