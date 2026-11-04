package dev.quickclaim.core;

public final class Heading {
    private Heading() {}

    public static String toward(double dx, double dz, float yaw) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
        angle = (angle % 360 + 540) % 360 - 180;
        if (Math.abs(angle) < 25) return "Ahead";
        if (Math.abs(angle) > 155) return "Behind";
        return angle > 0 ? "Right" : "Left";
    }
}
