package gg.fotia.crates.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrateParticleEffectLimitsTest {

    @Test
    void clampsHandEditedConfigurationToRuntimeSafeLimits() {
        CrateParticleEffect effect = new CrateParticleEffect(
                ParticleStage.OPEN,
                true,
                "FLAME",
                ParticleEffectMode.DOUBLE_HELIX,
                ParticleTarget.CRATE,
                10_000,
                10_000,
                10_000,
                10.0,
                100.0,
                100.0,
                0.0,
                0.0,
                0.0,
                "#FFFFFF",
                "#000000",
                20.0f,
                null,
                null
        );

        assertEquals(500, effect.getCount());
        assertEquals(200, effect.getInterval());
        assertEquals(400, effect.getDuration());
        assertEquals(2.0, effect.getSpeed());
        assertEquals(8.0, effect.getRadius());
        assertEquals(8.0, effect.getHeight());
        assertEquals(5.0f, effect.getSize());
    }
}
