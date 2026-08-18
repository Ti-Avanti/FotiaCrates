package gg.fotia.crates.crate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateBlockItemTemplateTest {

    @Test
    void acceptsNonBlockCarrierItemsUsedByCustomItemPlugins() {
        assertTrue(CrateBlockItemTemplate.isUsable(new ItemStack(Material.PAPER)));
        assertFalse(CrateBlockItemTemplate.isUsable(new ItemStack(Material.AIR)));
    }

    @Test
    void clonesConfiguredTemplateInsteadOfMutatingStoredItem() {
        ItemStack template = new ItemStack(Material.PAPER, 7);

        ItemStack created = CrateBlockItemTemplate.createBase(template, Material.CHEST);
        created.setAmount(3);

        assertNotSame(template, created);
        assertEquals(Material.PAPER, created.getType());
        assertEquals(7, template.getAmount());
    }

    @Test
    void fallsBackToLegacyMaterialWhenNoTemplateExists() {
        ItemStack created = CrateBlockItemTemplate.createBase(null, Material.CHEST);

        assertEquals(Material.CHEST, created.getType());
    }
}
