package dev.quickclaim.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public record RegionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public RegionBox {
        if (minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("Reversed bounds");
    }

    public static RegionBox between(int ax, int ay, int az, int bx, int by, int bz) {
        return new RegionBox(
                Math.min(ax, bx),
                Math.min(ay, by),
                Math.min(az, bz),
                Math.max(ax, bx),
                Math.max(ay, by),
                Math.max(az, bz));
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(RegionBox b) {
        return minX <= b.maxX
                && maxX >= b.minX
                && minY <= b.maxY
                && maxY >= b.minY
                && minZ <= b.maxZ
                && maxZ >= b.minZ;
    }

    public long area() {
        return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
    }

    public long volume() {
        return Math.multiplyExact(area(), (long) maxY - minY + 1);
    }

    public List<RegionBox> subtract(RegionBox cut) {
        if (!intersects(cut)) return List.of(this);
        var result = new ArrayList<RegionBox>();
        int x1 = Math.max(minX, cut.minX), x2 = Math.min(maxX, cut.maxX);
        int y1 = Math.max(minY, cut.minY), y2 = Math.min(maxY, cut.maxY);
        int z1 = Math.max(minZ, cut.minZ), z2 = Math.min(maxZ, cut.maxZ);
        if (minX < x1) result.add(new RegionBox(minX, minY, minZ, x1 - 1, maxY, maxZ));
        if (x2 < maxX) result.add(new RegionBox(x2 + 1, minY, minZ, maxX, maxY, maxZ));
        if (minY < y1) result.add(new RegionBox(x1, minY, minZ, x2, y1 - 1, maxZ));
        if (y2 < maxY) result.add(new RegionBox(x1, y2 + 1, minZ, x2, maxY, maxZ));
        if (minZ < z1) result.add(new RegionBox(x1, y1, minZ, x2, y2, z1 - 1));
        if (z2 < maxZ) result.add(new RegionBox(x1, y1, z2 + 1, x2, y2, maxZ));
        return result;
    }

    public static long projectedArea(Collection<RegionBox> boxes) {
        var edges = new TreeSet<Long>();
        for (var b : boxes) {
            edges.add((long) b.minX);
            edges.add((long) b.maxX + 1);
        }
        var xs = new ArrayList<>(edges);
        long total = 0;
        for (int i = 1; i < xs.size(); i++) {
            long left = xs.get(i - 1), right = xs.get(i);
            var intervals = new ArrayList<long[]>();
            for (var b : boxes)
                if (b.minX <= left && b.maxX >= left)
                    intervals.add(new long[] {b.minZ, (long) b.maxZ + 1});
            intervals.sort(Comparator.comparingLong(v -> v[0]));
            long length = 0, start = 0, end = 0;
            boolean first = true;
            for (var interval : intervals) {
                if (first) {
                    start = interval[0];
                    end = interval[1];
                    first = false;
                } else if (interval[0] <= end) end = Math.max(end, interval[1]);
                else {
                    length += end - start;
                    start = interval[0];
                    end = interval[1];
                }
            }
            if (!first) length += end - start;
            total = Math.addExact(total, Math.multiplyExact(right - left, length));
        }
        return total;
    }
}
