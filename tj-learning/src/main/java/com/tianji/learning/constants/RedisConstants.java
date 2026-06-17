package com.tianji.learning.constants;

public interface RedisConstants {

    /*
    * 签到记录的key的前缀：sign:uid:110:202301
    * */

    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";
    /*
    * 积分排行榜的key的前缀 ：boards:20260524
    * */
    String POINTS_BOARD_KEY_PREFIX = "boards:";
}
