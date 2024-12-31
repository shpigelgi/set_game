package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The Dealer in the current game.
     */
    private Dealer dealer;

    /**
     * The current score of the player.
     */
    private volatile int score;

    /**
     * the tokens that this player has placed
     */
    private volatile List<Integer> tokensPlaced;

    /**
     * the incoming action queue for this player
     */
    private ArrayBlockingQueue<Integer> actionQueue;

    /**
     * boolean checks
     */
    private volatile boolean waiting;

    private volatile boolean point;

    private volatile boolean checked;

    private boolean penalized = false;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        tokensPlaced = new CopyOnWriteArrayList<>();
        actionQueue = new ArrayBlockingQueue<>(env.config.featureSize);
        waiting = false;
        checked = true;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

        //if this player is not human, create the AI for this player
        if (!human) {
            createArtificialIntelligence();

            //notify the dealer that the aiThread has been created (used in Player::terminate()).
            synchronized (dealer) {
                dealer.notify();
            }
        }
        while (!terminate) {
            if (!human) {
                //while this player has placed less than 3 tokens, take the actions from the action queue and perform them.
                while ((tokensPlaced.size() < env.config.featureSize || penalized) && !terminate) {
                    if (!actionQueue.isEmpty())
                        keyPressed(actionQueue.remove());
                }
            }
            //if we have placed 3 tokens, and we are not penalized, we send the check to the dealer
            if (tokensPlaced.size() == env.config.featureSize && !penalized) {
                waiting = true;

                //create a copy of tokensPlaced that cannot be changed by other threads
                List<Integer> copy = new CopyOnWriteArrayList<>(tokensPlaced.subList(0, tokensPlaced.size()));
                int[] cards = new int[env.config.featureSize];

                //check again that tokensPlaced has not changed since entering.
                if (tokensPlaced.size() == env.config.featureSize && !penalized) {
                    boolean removedToken = false;

                    //for each token, we add the card it is on to the 'cards' array
                    for (int i = 0; i < env.config.featureSize; i++) {
                        synchronized (table.slots[copy.get(i)]) {
                            if (table.slotToCard[copy.get(i)] == null) {
                                tokensPlaced.remove(copy.get(i));
                                removedToken = true;
                            } else {
                                cards[i] = table.slotToCard[copy.get(i)];
                            }
                        }
                    }

                    //if any of our tokens have been removed by other threads, break from the loop and start placing tokens again
                    if (removedToken) {
                        break;
                    }

                    //init the check to be sent
                    Check check = new Check(cards, playerThread, this);

                    //send the check and wait until awakened by dealer.
                    synchronized (playerThread) {

                        dealer.send(check, tokensPlaced.size() == copy.size());
                        if (tokensPlaced.size() == copy.size() && !terminate) {
                            try {
                                if (!terminate) {
                                    playerThread.wait();
                                } else {
                                    break;
                                }
                            } catch (InterruptedException ignored) {
                                break;
                            }
                        }
                    }

                    //dealer changes checked and point booleans to tell player what to do.
                    if (checked) {
                        if (point) {
                            point();
                        } else {
                            penalty();
                        }
                    }
                    checked = false;
                }
            }
            waiting = false;

            //if I am not human, wake up the aiThread
            if (!human) {
                synchronized (aiThread) {
                    aiThread.notify();
                }
            }
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // NOTE: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                //while the actionQueue is not full, insert slots into it
                while ((actionQueue.size() < env.config.featureSize) && !terminate) {
                    Random rand = new Random();

                    //lock 'removeCardsLock' as a reader until I am finished looking at the cards
                    dealer.removeCardsLock.readLock().lock();
                    int[] fullSlots;
                    try {
                        fullSlots = table.findFullSlots();
                    } finally {
                        dealer.removeCardsLock.readLock().unlock();
                    }

                    //if there are any full slots, pick one and add it to the actionQueue
                    if (fullSlots.length != 0) {
                        int slot = rand.nextInt(fullSlots.length);
                        try {
                            actionQueue.put(fullSlots[slot]);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }

                //if this is waiting for a reply from the dealer, don't add actions to the queue
                if (waiting) {
                    synchronized (aiThread) {
                        if (waiting) {
                            try {
                                if (!terminate)
                                    aiThread.wait();
                            } catch (InterruptedException ignored) {
                                break;
                            }
                        }
                    }
                }
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());

            //notify the dealer that the AI has been terminated.
            synchronized (dealer) {
                dealer.notify();
            }
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {

        //if this player is not human, check that the aiThread has already been created, else, wait until it has been.
        if (!human) {
            if (aiThread == null) {
                synchronized (dealer) {
                    while (aiThread == null) {
                        try {
                            dealer.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        }
        terminate = true;

        //if this player is not human, interrupt the aiThread and stop it.
        if (!human) {
            try {
                synchronized (aiThread) {
                    aiThread.interrupt();
                }
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        }

        //if the aiThread has not terminated yet, the dealer will wait until it has.
        if (!human && aiThread.isAlive()) {
            synchronized (dealer) {
                if (aiThread.isAlive()) {
                    try {
                        dealer.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        //now that the aiThread has been stopped, we interrupt the playerThread.
        try {
            synchronized (playerThread) {
                playerThread.interrupt();
            }
            playerThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!waiting) {

            //if dealer is blocking all players, wait until notified.
            if (dealer.isBlockAllOthers()) {
                try {
                    synchronized (playerThread) {
                        if (dealer.isBlockAllOthers()) {
                            if (!terminate)
                                playerThread.wait();
                        }
                    }
                } catch (InterruptedException ignored) {
                    return;
                }
            }

            //if there is already a token on the slot, we remove it, otherwise, we add the slot to the list.
            dealer.removeCardsLock.writeLock().lock();
            try {
                if (tokensPlaced.contains(slot)) {
                    if (table.removeToken(id, slot)) {
                        tokensPlaced.remove((Integer) slot);
                        penalized = false;
                    }
                } else if (tokensPlaced.size() < env.config.featureSize && table.slotToCard[slot] != null) {
                    table.placeToken(id, slot);
                    tokensPlaced.add(slot);
                    penalized = false;
                }
            } finally {
                dealer.removeCardsLock.writeLock().unlock();
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //clear token placed by this player in the tokensPlaced list.
        removeTokens();

        //increment the score of the player
        env.ui.setScore(id, score + 1);
        score++;

        //stop player for 'env.config.pointFreezeMillis' milliseconds
        try {
            for (long i = env.config.pointFreezeMillis; i > 0; i = i - 1000) {
                env.ui.setFreeze(id, i);
                Thread.sleep(1000);
            }
            env.ui.setFreeze(id, 0);
        } catch (InterruptedException ignored) {
        }

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        //stop player for 'env.config.penaltyFreezeMillis' milliseconds
        try {
            for (long i = env.config.penaltyFreezeMillis; i > 0; i = i - 1000) {
                env.ui.setFreeze(id, i);
                playerThread.sleep(1000);
            }
            env.ui.setFreeze(id, 0);
        } catch (InterruptedException ignored) {
        }
        penalized = true;
    }

    /**
     * remove this player's token from a given slot
     *
     * @param slot - the slot from which to remove the token
     */
    public void removeToken(int slot) {
        tokensPlaced.remove((Integer) slot);
    }

    /**
     * remove all tokens placed by this player.
     */
    public void removeTokens() {
        tokensPlaced.clear();
    }

    public void placeTokenForTest(int slot){
        tokensPlaced.add(slot);
    }

    public List<Integer> getTokensPlaced() {
        return tokensPlaced;
    }

    public int score() {
        return score;
    }

    public Thread getPlayerThread() {
        return playerThread;
    }

    public void setPoint(boolean point) {
        this.point = point;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void setPlayerThread(Thread playerThread) {
        this.playerThread = playerThread;
    }

    public boolean isPenalized() {
        return penalized;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
