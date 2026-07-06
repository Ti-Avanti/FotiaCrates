package gg.fotia.crates.animation;

final class AnimationCompletion {

    private boolean completed;

    boolean complete(Runnable onComplete) {
        if (completed) {
            return false;
        }
        completed = true;
        onComplete.run();
        return true;
    }

    boolean completeIfUnavailable(boolean available, Runnable onComplete) {
        if (available) {
            return false;
        }
        return complete(onComplete);
    }

    boolean isCompleted() {
        return completed;
    }
}
