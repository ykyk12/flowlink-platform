package com.flowlink.common;

import java.util.UUID;

/** id 生成：统一 UUID（36 位，无连字符变体见 shortId）。 */
public final class Ids {

    private Ids() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** 用于 traceId / Key 片段：32 位无连字符。 */
    public static String shortId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
