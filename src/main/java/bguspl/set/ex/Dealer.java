package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
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

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    final private long reshuffleTime; // 60 seconds

    final private long warningTime;

    private Integer playerToCheck;

    public volatile boolean dealerActive = true;

    // the current time of the program
    private long currTime;

    private Thread dealerThread;

    private int featureSize;

    private int tableSize;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerToCheck = null;
        reshuffleTime = env.config.turnTimeoutMillis - 1;
        warningTime = env.config.turnTimeoutWarningMillis;
        this.featureSize = env.config.featureSize;
        this.tableSize = env.config.tableSize;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (int i = 0; i < players.length; i++) {
            Thread t = new Thread(players[i]);
            t.start();
        }

        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }

        announceWinners();
        dealerActive = false;
        closePlayerThreads();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() - currTime < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        dealerActive = false;
        terminate = true;
        dealerThread.interrupt();
    }

    public void closePlayerThreads() {
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
            try {
                players[i].playerThread.interrupt();
                players[i].playerThread.join();
            } catch (InterruptedException e) {

            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (table.claims.size() != 0) {
            synchronized (table) {
                playerToCheck = table.claims.remove(0);
                if (players[playerToCheck].getCounter() == featureSize) {
                    if (env.util.testSet(table.getPlayerTokens(false, playerToCheck))) {
                        handleCorrectSet(playerToCheck);
                        players[playerToCheck].point();
                    } else {
                        players[playerToCheck].penalty();
                    }
                }
                players[playerToCheck].changeCheck();
                playerToCheck = null;
                table.notifyAll();
            }
        }
        updateTimerDisplay(false);
    }

    private void handleCorrectSet(int playerToCheck) {
        int[] playerSlots = table.getPlayerTokens(true, playerToCheck);
        for (int i = 0; i < playerSlots.length; i++) {
            Vector<Integer> tokensInSlot = table.getVectorSlot(playerSlots[i]);
            table.removeCard(playerSlots[i]);
            for (int j = 0; j < tokensInSlot.size(); j++) { // for all the players that put a token in this slot
                players[tokensInSlot.get(j)].decreaseCounter();
            }
            updateTimerDisplay(true);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private synchronized void placeCardsOnTable() {
        boolean place = false;
        for (int i = 0; i < tableSize && deck.size() > 0; i++) {
            if (table.getCardfromSlot(i) == -1) {
                table.placeCard(deck.remove(0), i);
                place = true;
            }
        }
        dealerActive = false;
        this.notifyAll();

        if (env.config.hints && place) {
            System.out.println("new hints:");
            table.hints();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (table) {
            long waittime;
            if (reshuffleTime - (System.currentTimeMillis() - currTime) <= warningTime)
                waittime = 1;
            else
                waittime = 500; // in case someone wakes the dealer up too early
            long startWait = System.currentTimeMillis();
            while (waittime > 0 && table.claims.isEmpty()) {
                try {
                    table.wait(waittime); // wait for one second (1000 milliseconds) or until woken up
                    waittime = waittime - (System.currentTimeMillis() - startWait); // to make sure java does not wake
                                                                                    // him up too early
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            env.ui.setCountdown(reshuffleTime, false);
            currTime = System.currentTimeMillis();
        } else {
            long time = reshuffleTime - (System.currentTimeMillis() - currTime);
            if (time < 0)
                time = 0;
            env.ui.setCountdown(time, time <= warningTime);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private synchronized void removeAllCardsFromTable() {
        dealerActive = true;
        for (int i = 0; i < tableSize; i++) {
            if (table.getCardfromSlot(i) != -1) {
                deck.add(table.getCardfromSlot(i));
                table.removeCard(i);
            }
        }
        for (int i = 0; i < players.length; i++) { // to reset the players info
            players[i].resetCounter();
            players[i].resetSetLegal();
        }
        if (terminate) {
            this.notifyAll();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = 0;
        int counter = 0;
        for (int i = 0; i < players.length; i++) {
            if (max < players[i].score()) {
                max = players[i].score();
                counter = 1;
            } else {
                if (max == players[i].score())
                    counter++;
            }
        }

        int[] winners = new int[counter];
        counter = 0;
        for (int i = 0; i < players.length && counter < winners.length; i++) {
            if (max == players[i].score()) {
                winners[counter] = i;
                counter++;
            }
        }

        env.ui.announceWinner(winners);
    }
}
