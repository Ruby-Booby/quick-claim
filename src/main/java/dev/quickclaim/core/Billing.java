package dev.quickclaim.core;

public final class Billing {

    public static final long DAY = 86_400_000L;

    public static final long GRACE = 7 * DAY;

    private Billing() {}

    public static int cost(int base, int members) {
        return Math.addExact(base, Math.multiplyExact(Math.max(0, members / 5 - 1), 50));
    }

    public static long periodsDue(long now, long due, int intervalDays) {
        if (intervalDays < 1) throw new IllegalArgumentException("Interval must be positive");
        return now < due ? 0 : (now - due) / (intervalDays * DAY) + 1;
    }

    public static boolean canExpandAt(long now, long due) {
        return now < due + GRACE;
    }

    public static boolean protectedAt(long now, long due) {
        return now < due + GRACE;
    }

    public record Account(long due, long nextCharge, long owed) {

        public Account {
            if (owed < 0 || nextCharge < due) throw new IllegalArgumentException("Invalid account");
        }

        public Account accrue(long now, int days, int price) {
            long periods = periodsDue(now, nextCharge, days);
            if (periods == 0) return this;
            return new Account(
                    due,
                    Math.addExact(nextCharge, Math.multiplyExact(periods, days * DAY)),
                    Math.addExact(owed, Math.multiplyExact(periods, price)));
        }

        public Account pay(long amount) {
            if (amount < 0 || amount > owed) throw new IllegalArgumentException("Invalid payment");
            long remainder = owed - amount;
            return new Account(remainder == 0 ? nextCharge : due, nextCharge, remainder);
        }
    }
}
