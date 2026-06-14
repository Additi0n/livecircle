package com.codex.livecircle.uploader;

final class Member {
    final String memberId;
    final String name;
    final double lat;
    final double lon;
    final float accuracy;
    final long updatedAt;

    Member(String memberId, String name, double lat, double lon, float accuracy, long updatedAt) {
        this.memberId = memberId;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.accuracy = accuracy;
        this.updatedAt = updatedAt;
    }
}
