package gg.fotia.crates.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class CardFlickerSequence {

    private CardFlickerSequence() {
    }

    public static List<List<Integer>> create(int rewardCount, int cardCount,
                                              int minimumCycles, int minimumFrames,
                                              Random random) {
        if (rewardCount <= 0 || cardCount <= 0) {
            return List.of();
        }
        Random source = random == null ? new Random() : random;
        int cycles = Math.max(1, minimumCycles);
        List<List<Integer>> frames = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>(rewardCount);
        for (int index = 0; index < rewardCount; index++) {
            indexes.add(index);
        }

        for (int cycle = 0; cycle < cycles; cycle++) {
            Collections.shuffle(indexes, source);
            for (int offset = 0; offset < indexes.size(); offset += cardCount) {
                List<Integer> frame = new ArrayList<>(cardCount);
                int end = Math.min(indexes.size(), offset + cardCount);
                frame.addAll(indexes.subList(offset, end));
                while (frame.size() < cardCount) {
                    frame.add(source.nextInt(rewardCount));
                }
                Collections.shuffle(frame, source);
                frames.add(List.copyOf(frame));
            }
        }
        while (frames.size() < Math.max(1, minimumFrames)) {
            List<Integer> frame = new ArrayList<>(cardCount);
            while (frame.size() < cardCount) {
                frame.add(source.nextInt(rewardCount));
            }
            Collections.shuffle(frame, source);
            frames.add(List.copyOf(frame));
        }
        return List.copyOf(frames);
    }
}
