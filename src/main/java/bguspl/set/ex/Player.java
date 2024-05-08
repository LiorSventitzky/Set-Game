package bguspl.set.ex;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import bguspl.set.Env;

import java.util.concurrent.ArrayBlockingQueue;

import java.lang.Math;

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
    protected Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // the queue of keys = blocking queue
    private BlockingQueue<Integer> queue;

    private Dealer dealer;

    private int counter;

    private boolean freeze;

    private Boolean setLegal = null;

    private boolean check = false;

    private int featureSize;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.queue = new ArrayBlockingQueue<Integer>(env.config.featureSize);
        this.dealer = dealer;
        this.counter = 0;
        this.freeze = false;
        this.featureSize = env.config.featureSize;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        if (!human)
            createArtificialIntelligence();

        while (!terminate) {
            returnWhenDealerDone();
            int key = -1;
            try {
                key = queue.take();
            } catch (InterruptedException e) {
            }

            if (key != -1 && table.getCardfromSlot(key) != -1) {
                handleKey(key);
            }

            if (getCounter() == featureSize && setLegal == null) {
                table.claims.add(id);
                synchronized (table) {
                    table.notifyAll(); // to wake the dealer up
                    while (!terminate && !check) {
                        try {
                            table.wait();
                        } catch (InterruptedException e) {

                        }
                    }
                }
                if (setLegal != null) {
                    setFreeze();
                }
                changeCheck();
            }
        }

        if (!human)
            try {
                aiThread.interrupt();
                aiThread.join();
            } catch (InterruptedException ignored) {

            }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

    }

    public void returnWhenDealerDone() {
        synchronized (dealer) {
            // notice the while statement and not an if statement
            while (dealer.dealerActive) {
                try {
                    dealer.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            int tableSize = env.config.tableSize;
            while (!terminate) {
                try {
                    while (!freeze && !dealer.dealerActive) {
                        queue.put((int) (Math.random() * tableSize));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try {
            if (!freeze && !dealer.dealerActive)
                queue.put(slot);
        } catch (InterruptedException e) {
        }
    }

    private void handleKey(int key) {
        if (table.doesTokenExist(id, key) == -1) {
            if (getCounter() < featureSize) {
                table.placeToken(id, key);
                increaseCounter();
            }
        } else {
            table.removeToken(id, key);
            decreaseCounter();
            setLegal = null;
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        setLegal = true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        setLegal = false;
    }

    private void setFreeze() {
        long time;
        if (setLegal) {
            time = env.config.pointFreezeMillis;
            setLegal = null;
        } else
            time = env.config.penaltyFreezeMillis;
        freeze = true;
        while (!terminate && time > 0) {
            env.ui.setFreeze(id, time);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            time = time - 1000;
        }
        env.ui.setFreeze(id, 0);
        freeze = false;
    }

    public void resetSetLegal() {
        this.setLegal = null;
    }

    public int score() {
        return score;
    }

    public void resetCounter() {
        counter = 0;
    }

    public synchronized void decreaseCounter() {
        counter--;
    }

    public synchronized void increaseCounter() {
        counter++;
    }

    public synchronized int getCounter() {
        return counter;
    }

    public void changeCheck() {
        this.check = !this.check;
    }
}
