package de.oliver.fancynpcs.integrations.hover;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

/**
 * Standard easing curves (see easings.net). Some of them overshoot the [0, 1] range on purpose.
 */
public enum EasingCurve {

    LINEAR(t -> t),
    EASE_IN_SINE(t -> 1 - Math.cos(t * Math.PI / 2)),
    EASE_OUT_SINE(t -> Math.sin(t * Math.PI / 2)),
    EASE_IN_OUT_SINE(t -> -(Math.cos(Math.PI * t) - 1) / 2),
    EASE_IN_QUAD(t -> t * t),
    EASE_OUT_QUAD(t -> 1 - (1 - t) * (1 - t)),
    EASE_IN_OUT_QUAD(t -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
    EASE_IN_CUBIC(t -> t * t * t),
    EASE_OUT_CUBIC(t -> 1 - Math.pow(1 - t, 3)),
    EASE_IN_OUT_CUBIC(t -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
    EASE_IN_QUART(t -> t * t * t * t),
    EASE_OUT_QUART(t -> 1 - Math.pow(1 - t, 4)),
    EASE_IN_OUT_QUART(t -> t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2),
    EASE_IN_EXPO(t -> t == 0 ? 0 : Math.pow(2, 10 * t - 10)),
    EASE_OUT_EXPO(t -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t)),
    EASE_IN_OUT_EXPO(EasingCurve::inOutExpo),
    EASE_IN_BACK(t -> Const.BACK_C3 * t * t * t - Const.BACK_C1 * t * t),
    EASE_OUT_BACK(t -> 1 + Const.BACK_C3 * Math.pow(t - 1, 3) + Const.BACK_C1 * Math.pow(t - 1, 2)),
    EASE_IN_OUT_BACK(EasingCurve::inOutBack),
    EASE_OUT_ELASTIC(EasingCurve::outElastic),
    EASE_OUT_BOUNCE(EasingCurve::outBounce);


    private final DoubleUnaryOperator function;

    EasingCurve(DoubleUnaryOperator function) {
        this.function = function;
    }

    public float apply(float t) {
        return (float) function.applyAsDouble(t);
    }

    public static Optional<EasingCurve> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(curve -> curve.name().equals(normalized)).findFirst();
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(curve -> curve.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static double inOutExpo(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
    }

    private static double inOutBack(double t) {
        if (t < 0.5) {
            return (Math.pow(2 * t, 2) * ((Const.BACK_C2 + 1) * 2 * t - Const.BACK_C2)) / 2;
        }
        return (Math.pow(2 * t - 2, 2) * ((Const.BACK_C2 + 1) * (t * 2 - 2) + Const.BACK_C2) + 2) / 2;
    }

    private static double outElastic(double t) {
        if (t == 0 || t == 1) {
            return t;
        }
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * Const.ELASTIC_C4) + 1;
    }

    private static double outBounce(double t) {
        if (t < 1 / Const.BOUNCE_D1) {
            return Const.BOUNCE_N1 * t * t;
        }
        if (t < 2 / Const.BOUNCE_D1) {
            double u = t - 1.5 / Const.BOUNCE_D1;
            return Const.BOUNCE_N1 * u * u + 0.75;
        }
        if (t < 2.5 / Const.BOUNCE_D1) {
            double u = t - 2.25 / Const.BOUNCE_D1;
            return Const.BOUNCE_N1 * u * u + 0.9375;
        }
        double u = t - 2.625 / Const.BOUNCE_D1;
        return Const.BOUNCE_N1 * u * u + 0.984375;
    }

    private static final class Const {
        private static final double BACK_C1 = 1.70158;
        private static final double BACK_C2 = BACK_C1 * 1.525;
        private static final double BACK_C3 = BACK_C1 + 1;
        private static final double ELASTIC_C4 = (2 * Math.PI) / 3;
        private static final double BOUNCE_N1 = 7.5625;
        private static final double BOUNCE_D1 = 2.75;
    }
}
