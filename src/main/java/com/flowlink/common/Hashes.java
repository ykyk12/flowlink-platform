package com.flowlink.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 摘要工具：API Key 只存 SHA-256 摘要，不落明文。 */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 稳定哈希：用于灰度路由（同一 subjectId 永远落在同一分支）。 */
    public static int stableBucket(String value, int buckets) {
        int h = 0;
        for (int i = 0; i < value.length(); i++) {
            h = 31 * h + value.charAt(i);
        }
        return Math.floorMod(h, buckets);
    }
}
