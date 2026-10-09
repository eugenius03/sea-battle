package com.chnu.seabattle.dto.move;

public interface ShootingStatsProjection {
    long getShotsFired();

    long getHits();

    long getMisses();
}
