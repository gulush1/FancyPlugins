package de.oliver.fancynpcs.integrations.hover;

/**
 * Eased transition of a scale value, advanced one step per tick.
 */
public final class ScaleAnimation {

    private float from;
    private float to;
    private int elapsedTicks;
    private final int durationTicks;
    private float lastSent;
    private EasingCurve curve = EasingCurve.EASE_OUT_CUBIC;

    public ScaleAnimation(float initial, int durationTicks) {
        this.from = initial;
        this.to = initial;
        this.lastSent = initial;
        this.durationTicks = Math.max(1, durationTicks);
        this.elapsedTicks = this.durationTicks;
    }

    public void retarget(float newTarget, EasingCurve newCurve) {
        if (newTarget == to) {
            return;
        }
        from = current();
        curve = newCurve;
        to = newTarget;
        elapsedTicks = 0;
    }

    public void tick() {
        if (elapsedTicks < durationTicks) {
            elapsedTicks++;
        }
    }

    public float current() {
        float progress = (float) elapsedTicks / durationTicks;
        float eased = curve.apply(progress);
        return from + (to - from) * eased;
    }

    public boolean isFinished() {
        return elapsedTicks >= durationTicks;
    }

    public float getLastSent() {
        return lastSent;
    }

    public void markSent(float value) {
        lastSent = value;
    }
}
