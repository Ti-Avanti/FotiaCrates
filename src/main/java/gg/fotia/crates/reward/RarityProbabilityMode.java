package gg.fotia.crates.reward;

public enum RarityProbabilityMode {
    PER_REWARD,
    RARITY_TOTAL;

    public RarityProbabilityMode next() {
        return this == PER_REWARD ? RARITY_TOTAL : PER_REWARD;
    }
}
