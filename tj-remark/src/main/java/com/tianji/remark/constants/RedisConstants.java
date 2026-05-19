package com.tianji.remark.constants;

/**
 * Redis Key 常量定义
 * <p>
 * Redis 作为热数据层，存储近期的点赞记录和点赞总数统计。
 * 冷数据（低频访问/下架课程）会被淘汰，完整数据落地在 MySQL。
 * </p>
 *
 * <h3>Key 设计说明</h3>
 * <pre>
 * ┌──────────────────────────────────────┬──────────────────┬─────────────────────────────────┐
 * │ Key 格式                              │ 类型             │ 说明                            │
 * ├──────────────────────────────────────┼──────────────────┼─────────────────────────────────┤
 * │ likes:biz:{bizId}                    │ Hash             │ 业务点赞用户集合                  │
 * │                                      │                  │ field=userId, value=bizType:ts   │
 * │ likes:times:type:{bizType}           │ ZSet             │ 业务类型点赞总数统计              │
 * │                                      │                  │ member=bizId, score=likedTimes   │
 * │ likes:dirty:bizIds                   │ Set              │ 待持久化到MySQL的业务ID（脏标记）  │
 * │ likes:empty:biz:{bizId}             │ String           │ 空数据标记（防缓存穿透），带TTL     │
 * └──────────────────────────────────────┴──────────────────┴─────────────────────────────────┘
 * </pre>
 *
 * @author kaii
 */
public interface RedisConstants {

    /** 业务点赞用户Hash的Key前缀，后缀是业务id。Hash结构: field=userId, value=bizType:时间戳 */
    String LIKE_BIZ_KEY_PREFIX = "likes:biz:";

    /** 业务点赞数统计ZSet的Key前缀，后缀是业务类型。ZSet结构: member=bizId, score=点赞总数 */
    String LIKES_TIMES_KEY_PREFIX = "likes:times:type:";

    /**
     * 待持久化业务ID集合（脏数据标记）
     * <p>
     * 每次用户点赞/取消点赞时，将业务ID加入此Set。
     * 定时任务从此Set取出待处理的业务ID，将Redis中的点赞记录同步到MySQL后移除。
     * </p>
     */
    String LIKE_BIZ_DIRTY_KEY = "likes:dirty:bizIds";

    /**
     * 空数据标记Key前缀，后缀是业务id
     * <p>
     * 当 Redis 和 MySQL 中都不存在某业务的点赞记录时，写入此标记并设置 TTL（默认30分钟）。
     * 下次查询时先检查此标记，若存在则直接返回"无点赞"，避免缓存穿透打到 MySQL。
     * </p>
     */
    String LIKE_BIZ_EMPTY_KEY_PREFIX = "likes:empty:biz:";

    /** 空数据标记的过期时间（分钟） */
    long EMPTY_MARK_TTL_MINUTES = 30;
}
