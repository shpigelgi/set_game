package bguspl.set;

import java.util.List;

/**
 * An interface for general utilities provided for convenience.
 */
public interface Util {

    /**
     * Converts an array of card ids to an array of features (see cardToFeatures method).
     *
     * @param cards - an array of card ids.
     * @return - a 2d array of features (respectively).
     */
    int[][] cardsToFeatures(int[] cards);

    /**
     * Checks if an array of cards forms a legal set.
     *
     * @param cards - the array of cards.
     * @return - true iff the array forms a legal set.
     */
    boolean testSet(int[] cards);

    /**
     * Finds and returns up to count sets in the given collection of cards.
     *
     * @param deck  - a collection of cards (may not include null objects).
     * @param count - the maximum number of sets to find.
     * @return - a list of up to count integer arrays, each one contains the card ids of a legal set.
     */
    List<int[]> findSets(List<Integer> deck, int count);

    /**
     * Spin a random number of times (for debugging/testing).
     */
    void spin();
}
