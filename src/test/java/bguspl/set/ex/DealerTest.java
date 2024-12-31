package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    Env env;
    Player[] players;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;


    @BeforeEach
    void setUp() {
        env = new Env(logger, new Config(logger, (String) null), ui, util);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void findPlayer() {

    }

    @Test
    void announceWinners_onePlayer() {
        players = new Player[1];
        dealer = new Dealer(env, table, players);
        Player player = new Player(env, dealer, table, 0, true);
        players[0] = player;
        player.setScore(1);
        assertEquals(1, dealer.announceWinners().length);
        assertEquals(1, dealer.getPlayers()[dealer.announceWinners()[0]].score());
    }

    @Test
    void announceWinners_noPlayers() {
        players = new Player[0];
        dealer = new Dealer(env, table, players);
        assertEquals(0, dealer.announceWinners().length);
    }

    @Test
    void announceWinners_multiplePlayers() {
        players = new Player[4];
        dealer = new Dealer(env, table, players);
        players[0] = new Player(env, dealer, table, 0, true);
        players[1] = new Player(env, dealer, table, 1, true);
        players[2] = new Player(env, dealer, table, 2, true);
        players[3] = new Player(env, dealer, table, 3, true);
        players[0].setScore(8);
        players[1].setScore(9);
        players[2].setScore(1);
        players[3].setScore(4);

        assertEquals(1, dealer.announceWinners().length);
        assertEquals(1, dealer.announceWinners()[0]);
        assertEquals(9, dealer.getPlayers()[dealer.announceWinners()[0]].score());

        players[1].setScore(8);

        assertEquals(2, dealer.announceWinners().length);
        assertEquals(0, dealer.announceWinners()[0]);
        assertEquals(1, dealer.announceWinners()[1]);
        assertEquals(8, dealer.getPlayers()[dealer.announceWinners()[0]].score());

        players[0].setScore(0);
        players[1].setScore(0);
        players[2].setScore(0);
        players[3].setScore(0);

        assertEquals(4, dealer.announceWinners().length);
    }
}