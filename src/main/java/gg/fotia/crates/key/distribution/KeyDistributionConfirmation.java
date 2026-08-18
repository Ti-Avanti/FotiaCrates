package gg.fotia.crates.key.distribution;

public record KeyDistributionConfirmation(
        String token,
        KeyDistributionRequest request,
        long expiresAt
) {
}
