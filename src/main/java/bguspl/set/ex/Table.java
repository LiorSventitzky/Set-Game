package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.text.html.HTMLDocument.Iterator;

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

    private Vector<Integer>[] tokens;

    public Vector<Integer> claims;

    private int featureSize;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.featureSize = env.config.featureSize;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        this.tokens = new Vector[env.config.tableSize];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = new Vector<>();
        }
        claims = new Vector<>();
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
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
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        synchronized (tokens[slot]) {
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot);
        }
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

        synchronized (tokens[slot]) {
            if (slotToCard[slot] != null) {
                cardToSlot[slotToCard[slot]] = null;
                slotToCard[slot] = null;
                tokens[slot] = new Vector<>();
                env.ui.removeTokens(slot);
                env.ui.removeCard(slot);
            }
        }
    }

    public Vector<Integer> getVectorSlot(int slot) {
        return tokens[slot];
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if (slotToCard[slot] != null) {
            synchronized (tokens[slot]) {
                env.ui.placeToken(player, slot);
                tokens[slot].add(player);
            }
        }
    }

    public int doesTokenExist(int player, int slot) {
        return tokens[slot].indexOf(player); // return the index of the palyer and -1 if doesnt exist
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (doesTokenExist(player, slot) != -1) {
            synchronized (tokens[slot]) {
                tokens[slot].remove(doesTokenExist(player, slot));
                env.ui.removeToken(player, slot);
                return true;
            }
        }
        return false;
    }

    public int[] getPlayerTokens(boolean bySlot, int player) {
        int[] cards = new int[featureSize];
        int counter = 0; // the amount of tokesns the player can place
        for (int i = 0; i < tokens.length && counter < featureSize; i++) {
            if (tokens[i].indexOf(player) != -1) {
                if (!bySlot)
                    cards[counter] = slotToCard[i]; // return the card number
                else
                    cards[counter] = i; // return the slot number
                counter++;
            }
        }
        return cards;
    }

    public int getCardfromSlot(int slot) {
        if (slotToCard[slot] == null)
            return -1;
        return slotToCard[slot];
    }

}
