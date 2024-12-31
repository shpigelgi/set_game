package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    private final Thread[] playerThreads;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * The list of sets the dealer needs to check.
     */
    private final List<Check> checks;
    private final List<Player> playersWaitingToBeChecked;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * locks for termination
     */
    private final Object endLock = new Object();
    private final Object startLock = new Object();

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private final long reshuffleTime;

    /**
     * stops all threads before removing or placing cards on the table
     */
    private volatile boolean blockAllOthers = false;

    /**
     * the time remaining in game
     */
    private volatile long time;

    private long timeZero;

    private long lastTime;

    /**
     * a Read-Write lock that locks anyone that changes the state of the cards
     */
    ReadWriteLock removeCardsLock;
    /**
     * default dealer constructor
     *
     * @param env     - the environment object
     * @param table   - the table object
     * @param players - the players in the game
     */
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        checks = new CopyOnWriteArrayList<>();
        playersWaitingToBeChecked = new CopyOnWriteArrayList<>();
        playerThreads = new Thread[players.length];
        reshuffleTime = env.config.turnTimeoutMillis;
        if (reshuffleTime > 0) {
            time = reshuffleTime;
        } else {
            time = Long.MAX_VALUE;
        }
        removeCardsLock = new ReentrantReadWriteLock();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

        synchronized (startLock) {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

            //placing all cards on table
            placeCardsOnTable();

            updateTimerDisplay(true);

            //starting all the playerThreads
            startPlayerThreads();
        }

        boolean afterFirst = false;
        while (!shouldFinish()) {
            if (afterFirst) {
                updateTimerDisplay(true);
            }

            //after replacing all cards, print out a hint
            if (env.config.hints) {
                table.hints();
            }

            blockAllOthers = false;
            if (afterFirst) {
                wakeUpAllPlayers();
            } else {
                afterFirst = true;
            }
            timerLoop();
            blockAllOthers = true;

            removeCardsLock.writeLock().lock();
            try {
                removeAllCardsFromTable();
                placeCardsOnTable();
            } finally {
                removeCardsLock.writeLock().unlock();
            }
        }

        //if shouldFinish returns true but terminate is false, we call it ourselves
        if (!terminate) {
            terminate();
        }

        //when game is over, announce the winners
        synchronized (endLock) {
            announceWinners();
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }
    }

    /**
     * start all the player threads
     */
    private void startPlayerThreads() {
        for (int i = 0; i < players.length; i++) {
            playerThreads[i] = new Thread(players[i]);
            players[i].setPlayerThread(playerThreads[i]);
            playerThreads[i].start();
        }
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && (time > 0 || env.config.turnTimeoutMillis <= 0)) {
            //if there is no timer nor sets on the table break out of the loop
            if (env.config.turnTimeoutMillis <= 0 && thereAreNoSetsOnTable()) {
                break;
            }


            //sleep until a player sends a check and wakes you up
            sleepUntilWokenOrTimeout();
            while (!checks.isEmpty() && (time > 0 || env.config.turnTimeoutMillis <= 0)) {

                //if there is no timer nor sets on the table break out of the loop
                if (env.config.turnTimeoutMillis <= 0 && thereAreNoSetsOnTable()) {
                    break;
                }

                if (Math.abs(env.config.turnTimeoutMillis - (System.currentTimeMillis() - timeZero) - lastTime) > 10){
                    if (env.config.turnTimeoutMillis > 0) {
                        time = env.config.turnTimeoutMillis - (System.currentTimeMillis() - timeZero);
                        env.ui.setCountdown(time + 999, false);
                        lastTime = time;
                    } else if (env.config.turnTimeoutMillis == 0){
                        env.ui.setElapsed(time);
                        time = ((System.currentTimeMillis() - timeZero));
                        lastTime = time;
                    }
                }

                //get the player who sent the first check in the list
                Player currPlayer = checks.get(0).getPlayer();

                //check if the check is legal, and act accordingly
                if (env.util.testSet(checks.get(0).getCardsToCheck()) ||
                        ((!deck.isEmpty() || table.findEmptySlots().length != env.config.tableSize) && env.config.featureSize == 1)) {
                    removeCardsLock.writeLock().lock();
                    try {
                        givePoint(currPlayer);
                    } finally {
                        removeCardsLock.writeLock().unlock();
                    }

                    //after replacing cards in point, print a new hint
                    if (env.config.hints) {
                        table.hints();
                    }
                } else {
                    givePenalty(currPlayer);
                }
            }
        }
    }

    /**
     * give currPlayer a point
     *
     * @param currPlayer the player to give a point to
     */
    private void givePoint(Player currPlayer) {

        //remove and place the cards of the set, while blocking all other players.
        blockAllOthers = true;
        removeCardsFromTable();
        placeCardsOnTable();
        blockAllOthers = false;

        //telling currPlayer that he deserves a point and that we have checked his set
        currPlayer.setPoint(true);
        currPlayer.setChecked(true);

        //wake up all players that aren't waiting to be checked
        synchronized (this) {
            for (Player player : players) {
                if (!playersWaitingToBeChecked.contains(player)) {
                    synchronized (player.getPlayerThread()) {
                        player.getPlayerThread().notify();
                    }
                }
            }
            //reset timer
            updateTimerDisplay(true);
        }
    }

    /**
     * give currPlayer a penalty
     *
     * @param currPlayer the player to give a penalty to
     */
    private void givePenalty(Player currPlayer) {

        //tell player that he does not deserve a point BUT he has been checked
        currPlayer.setPoint(false);
        currPlayer.setChecked(true);

        //removing currPlayer's check from the list
        checks.remove(0);
        playersWaitingToBeChecked.remove(0);

        //waking currPlayer up
        synchronized (currPlayer.getPlayerThread()) {
            currPlayer.getPlayerThread().notify();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {

        //stopping terminate if the dealer is still setting up the game

        //stopping the announcement of winners until we have terminated all threads.
        //this is necessary so that the players don't modify the game state during the counting of the winners
        synchronized (endLock) {
            terminate = true;
            for (Player player : players) {
                player.terminate();
                try {
                    player.getPlayerThread().join();
                } catch (InterruptedException ignored) {
                }
            }
            synchronized (this) {
                notify();
            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        if (env.config.featureSize != 1) {
            return terminate || env.util.findSets(deck, 1).isEmpty();
        } else {
            return terminate || deck.isEmpty() && table.findEmptySlots().length == env.config.tableSize;
        }
    }

    /**
     * Checks if any cards should be removed from the table.
     * when entering this function, we already know the first check in the list is legal
     */
    private void removeCardsFromTable() {

        //get the first check and remove the corresponding player
        Check check = checks.remove(0);
        playersWaitingToBeChecked.remove(0);

        //get the cards and the player from the check
        int[] cards = check.getCardsToCheck();
        Player currPlayer = check.getPlayer();

        //for each card in the set, we remove all tokens on it and remove it from the table
        for (int card : cards) {
            Integer[] playersId = table.getSlots()[table.cardToSlot[card]].getTokens();

            //remove all the tokens from this card
            table.removeTokens(table.cardToSlot[card]);

            //for each of the tokens we removed, we add its player thread to the wakeup list
            for (Integer integer : playersId) {
                if (integer != null && integer != currPlayer.id) {

                    //remove the check this player has sent (if any), remove the token from its token list, and set its checked status to false
                    removeCheck(integer);
                    findPlayer(integer).removeToken(table.cardToSlot[card]);
                }
            }
            synchronized (table.slots[table.cardToSlot[card]]) {
                table.removeCard(table.cardToSlot[card]);
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int[] empty = table.findEmptySlots();

        //call shuffle on 'empty'
        empty = shuffle(empty);

        //if the table is empty, shuffle the deck
        if (empty.length == env.config.tableSize) {
            shuffleDeck();
        }

        //place a card in each empty slot (the order is completely random)
        for (int slot : empty) {
            if (!deck.isEmpty()) {
                table.placeCard(deck.remove(deck.size() - 1), slot);
            }
        }
    }

    /**
     * remove a player's check from the checks list
     *
     * @param id the id of the player whose check we want to delete
     */
    private void removeCheck(int id) {
        for (int k = 0; k < checks.size(); k++) {
            if (checks.get(k).getPlayer().id == id) {
                checks.remove(k);
                playersWaitingToBeChecked.remove(k);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        if (checks.isEmpty() && (time > 0 || env.config.turnTimeoutMillis == 0)
                && (!deck.isEmpty() || table.findEmptySlots().length != env.config.tableSize || env.config.turnTimeoutMillis > 0) && !terminate) {
            synchronized (this) {
                updateTimerDisplay(false);
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            timeZero = System.currentTimeMillis();
            if (env.config.turnTimeoutMillis > 0) {
                time = reshuffleTime;
                lastTime = time;
            } else if (env.config.turnTimeoutMillis == 0) {
                time = 0;
                lastTime = time;
            }
        } else {
            if (env.config.turnTimeoutMillis > 0) {
                timer1();
            } else if (env.config.turnTimeoutMillis == 0) {
                timer2();
            }
        }
    }

    private void timer1() {
        //if the time is more than 6 seconds, we increment it by 1000 millis and display it without warning.
        //else if the time is less than 6 seconds, we increment it by 10 millis (more or less) and display it with warning.
        if (time > env.config.turnTimeoutWarningMillis && time <= reshuffleTime) {
            time = env.config.turnTimeoutMillis - (System.currentTimeMillis() - timeZero);
            env.ui.setCountdown(time + 999, false);
            synchronized (this) {
                try {
                    wait(1000 + ((time - lastTime)%1000));
                } catch (InterruptedException ignored) {
                }
            }
            lastTime = time;
        } else if (time <= env.config.turnTimeoutWarningMillis && time >= 0) {
            env.ui.setCountdown(time, true);
            time = env.config.turnTimeoutMillis - ((System.currentTimeMillis() - timeZero));
            synchronized (this) {
                try {
                    wait(8 + (int)(((time - lastTime)%10) * 0.8));
                } catch (InterruptedException ignored) {
                }
            }
            if (time <= 0){
                env.ui.setCountdown(0, true);
            }
            lastTime = time;
        }
    }

    private void timer2() { //TODO: needs checking
        time = ((System.currentTimeMillis() - timeZero));
        env.ui.setElapsed(time);
        synchronized (this) {
            try {
                wait(1000 + ((time - lastTime)%1000));
            } catch (InterruptedException ignored) {
            }
        }
        lastTime = time;
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        if (table.findEmptySlots().length == 0) {
            checks.clear();
            playersWaitingToBeChecked.clear();
            //this array is here to randomize the order of removal
            int[] slots = new int[env.config.tableSize];
            for (int i = 0; i < env.config.tableSize; i++) {
                slots[i] = i;
            }
            slots = shuffle(slots);

            //remove all tokens in the ui
            env.ui.removeTokens();

            //remove all cards from the table and add them to the bottom of the deck
            for (int i = 0; i < env.config.tableSize; i++) {
                deck.add(table.getSlotToCard()[slots[i]]);
                table.removeCard(slots[i]);
            }

            //for each player, remove his tokens
            for (Player player : players) {
                player.removeTokens();
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    protected int[] announceWinners() {
        int max = -1;
        int counter = 0;

        //find the number of players with the maximum amount of sets
        for (Player player : players) {
            if (player.score() == max) {
                counter++;
            }
            if (player.score() > max) {
                max = player.score();
                counter = 1;
            }
        }
        int[] winners = new int[counter];
        int i = 0;

        //find the players with 'max' sets and add them to the winner list
        for (Player player : players) {
            if (player.score() == max) {
                winners[i] = player.id;
                i++;
            }
        }

        //announce winners in ui
        env.ui.announceWinner(winners);
        return winners;
    }

    /**
     * shuffle an array (used here to shuffle the order in which cards are placed in slots)
     *
     * @param toShuffle - the array to shuffle
     * @return a randomly shuffled array
     */
    public int[] shuffle(int[] toShuffle) {
        Random rd = new Random();
        for (int i = toShuffle.length - 1; i > 0; i--) {
            int j = rd.nextInt(i + 1);
            int temp = toShuffle[i];
            toShuffle[i] = toShuffle[j];
            toShuffle[j] = temp;
        }
        return toShuffle;
    }

    /**
     * this method shuffles the 'deck' list in a random order
     */
    public void shuffleDeck() {
        Random rd = new Random();
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = rd.nextInt(i + 1);
            int temp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, temp);
        }
    }

    /**
     * this method sends a check from a player to the dealer
     *
     * @param check - the check to be sent
     * @param valid - true iff the set in the check object is a legal set
     */
    public void send(Check check, boolean valid) {
        if (valid) {

            //if time is not over, add the check to the checks list
            if (time > 0 || env.config.turnTimeoutMillis <= 0) {
                synchronized (playersWaitingToBeChecked) {
                    playersWaitingToBeChecked.add(check.getPlayer());
                    checks.add(check);
                }
            }
            synchronized (this) {
                notify();
            }
        }
    }

    /**
     * this function finds a player based on its id
     *
     * @param id - the id of the player we want to find
     * @return the player with the id
     * @pre id >= 0
     * @pre players.length > id
     * @post @pre(players.length) == players. Length
     */
    public Player findPlayer(Integer id) {
        for (Player player : players) {
            if (player.id == id) {
                return player;
            }
        }
        return null;
    }

    /**
     * this method wakes up all players, regardless of if they are sleeping or not.
     */
    private void wakeUpAllPlayers() {
        for (Player player : players) {
            synchronized (player.getPlayerThread()) {
                player.getPlayerThread().notify();
            }
        }
    }

    public boolean isBlockAllOthers() {
        return blockAllOthers;
    }

    public Player[] getPlayers() {
        return players;
    }

    private LinkedList<Integer> arrayToList(Integer[] arr) {
        LinkedList<Integer> ans = new LinkedList<>();
        for (Integer integer : arr) {
            if (integer != null) {
                int n = integer;
                ans.add(n);
            }
        }
        return ans;
    }

    private boolean thereAreNoSetsOnTable(){
        boolean noNormalSets = env.util.findSets(arrayToList(table.slotToCard), 1).isEmpty();
        boolean oneFeatureSet = ((!deck.isEmpty() || table.findEmptySlots().length != env.config.tableSize) && env.config.featureSize == 1);
        return noNormalSets && !oneFeatureSet;
    }


}
