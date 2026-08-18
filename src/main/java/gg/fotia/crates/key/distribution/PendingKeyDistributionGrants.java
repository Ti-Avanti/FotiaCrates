package gg.fotia.crates.key.distribution;

import java.util.List;

public record PendingKeyDistributionGrants(
        List<KeyDistributionGrant> virtualGrants,
        List<KeyDistributionGrant> physicalGrants
) {

    public PendingKeyDistributionGrants {
        virtualGrants = List.copyOf(virtualGrants);
        physicalGrants = List.copyOf(physicalGrants);
    }

    public boolean isEmpty() {
        return virtualGrants.isEmpty() && physicalGrants.isEmpty();
    }
}
