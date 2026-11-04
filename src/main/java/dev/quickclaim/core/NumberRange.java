package dev.quickclaim.core;

import java.util.OptionalInt;

public record NumberRange(int min, int max) {
    public NumberRange {
        if (min >= max) throw new IllegalArgumentException("Range must have at least two values");
    }

    public OptionalInt parse(String text) {
        if (text == null || !text.matches("[0-9]+")) return OptionalInt.empty();
        try {
            int number = Integer.parseInt(text);
            return number >= min && number <= max ? OptionalInt.of(number) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    public double fraction(int number) {
        return (double) (Math.max(min, Math.min(max, number)) - min) / (max - min);
    }

    public int number(double fraction) {
        return min + (int) Math.round(Math.max(0, Math.min(1, fraction)) * (max - min));
    }
}
