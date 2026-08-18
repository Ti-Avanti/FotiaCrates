package gg.fotia.crates.key.distribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyDistributionTargetTest {

    @Test
    void recognizesOnlineAndAllSelectorsWithoutCapturingPlayerNames() {
        assertEquals(KeyDistributionTarget.Kind.ONLINE,
                KeyDistributionTarget.parse("@online").kind());
        assertEquals(KeyDistributionTarget.Kind.ALL,
                KeyDistributionTarget.parse("@ALL").kind());
        assertEquals(KeyDistributionTarget.Kind.PLAYER,
                KeyDistributionTarget.parse("OnlinePlayer").kind());
        assertEquals("OnlinePlayer", KeyDistributionTarget.parse("OnlinePlayer").playerName());
    }
}
