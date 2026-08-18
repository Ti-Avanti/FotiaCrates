package gg.fotia.crates.reward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RewardDisplayNamePolicyTest {

    @Test
    void keepsItemNameWhileAutomaticSyncIsEnabled() {
        assertNull(RewardDisplayNamePolicy.manualOverride("<!i><gold>666金币", true));
    }

    @Test
    void appliesEditedNameAfterAutomaticSyncIsDisabled() {
        assertEquals("<!i><gold>666金币",
                RewardDisplayNamePolicy.manualOverride("<!i><gold>666金币", false));
    }
}
