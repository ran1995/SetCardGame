package bguspl.set.ex;

import bguspl.set.Env;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private BlockingQueue<Integer> actionQueue;

    private volatile long sleepingTime;

    protected long playerTimer;

    Integer dealerPlayerLock;

    Integer terminateLock;

    Integer AiLock;


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
        actionQueue = new LinkedBlockingQueue<>(3);
        sleepingTime = 0;
        playerTimer = 0;
        dealerPlayerLock = 0;
        terminateLock = 0;
        AiLock = 0;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        synchronized (table.playersOrderList) {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            table.playersOrderList.add(id);
        }
        if (!human) createArtificialIntelligence();


        while (!terminate) {

            if (!actionQueue.isEmpty() && table.playersTokens[id].size() < 3 || (!actionQueue.isEmpty() && table.playersTokens[id].size() == 3 && table.playersTested[id])) { // want also && sleeping time = 0.
                int slot = actionQueue.poll();
                table.keyPressed(id, slot);
            }

            if (sleepingTime > 0 && !terminate) {
                try {
                    playerThread.sleep(sleepingTime);
                } catch (Exception e) {
                }
                sleepingTime = 0;
            }

            synchronized (dealerPlayerLock) {
                synchronized (terminateLock) {  // maybe redundent
                    if (actionQueue.isEmpty() && sleepingTime == 0 && !terminate) {
                        try {
                            dealerPlayerLock.wait();
                        } catch (InterruptedException exception) {
                        }
                    }
                }
            }

        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if (actionQueue.size() < 3) {
                    int slot = getRandomNumber(0, env.config.tableSize);
                    keyPressed(slot);
                }
                // TODO implement player key press simulator
                synchronized (AiLock) {
                    try {
                        AiLock.wait(10);
                    } catch (InterruptedException ignored) {}
               }
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        synchronized (terminateLock) {
            terminate = true;
            playerThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        boolean validPress = false;

        synchronized (table.ignoreAllPlayersActions) {
            if (!table.ignoreAllPlayersActions && sleepingTime == 0) {
                actionQueue.offer(slot);
                validPress = true;
            }
        }
        synchronized (dealerPlayerLock) {
            if (validPress)
                dealerPlayerLock.notifyAll();
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

        synchronized (dealerPlayerLock) {
            sleepingTime = env.config.pointFreezeMillis;
            playerTimer = System.currentTimeMillis() + sleepingTime + 1000;
            dealerPlayerLock.notifyAll();
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized (dealerPlayerLock) {
            sleepingTime = env.config.penaltyFreezeMillis;
            playerTimer = System.currentTimeMillis() + sleepingTime + 1000;
            dealerPlayerLock.notifyAll();
        }
    }

    public int score() {
        return score;
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public Thread getPlayerThread() {
        return playerThread;
    }

    public long getSleepingTime(){ // FOR TESTS ONLY!
        return  sleepingTime;
    }

    public boolean getTerminateState(){ // FOR TESTS ONLY!
        return terminate;
    }

    public BlockingQueue<Integer> getActionQueue(){
        return actionQueue;
    }
}
