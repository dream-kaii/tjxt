package com.tianji.remark.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * 点赞记录服务实现（Redis + MySQL 混合架构）
 *
 * <h3>架构设计</h3>
 * <pre>
 * ┌─────────────┐     写入      ┌─────────────┐     定时同步     ┌─────────────┐
 * │   客户端     │ ──────────→  │   Redis      │ ──────────→    │   MySQL      │
 * │             │              │  (热数据层)    │                │  (冷数据层)   │
 * └─────────────┘              └─────────────┘                └─────────────┘
 *                                     ↑                            │
 *                                     │       Cache-Aside 回种      │
 *                                     └────────────────────────────┘
 *                                              （查询Miss时）
 * </pre>
 *
 * <h3>核心模式：Cache-Aside（旁路缓存）</h3>
 * <ol>
 *   <li><b>写入</b>：先写 Redis，将 bizId 加入"脏数据集合"，异步同步到 MySQL</li>
 *   <li><b>查询</b>：先查 Redis → Miss 则查 MySQL → 回种 Redis（含空数据标记防穿透）</li>
 *   <li><b>同步</b>：定时任务取出脏 bizId，将 Redis 数据批量 UPSERT/DELETE 到 MySQL</li>
 *   <li><b>淘汰</b>：定时任务清理 Redis 中长期未被访问的数据，控制内存占用</li>
 * </ol>
 *
 * <h3>Redis 数据结构</h3>
 * <pre>
 * likes:biz:{bizId}         → Hash   field=userId   value="bizType:时间戳毫秒"
 * likes:times:type:{bizType} → ZSet   member=bizId   score=总点赞数
 * likes:dirty:bizIds         → Set    存放有变更待同步的业务ID
 * likes:empty:biz:{bizId}   → String 空数据标记（防穿透），TTL=30分钟
 * </pre>
 *
 * @author kaii
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    // =====================================================
    //  点赞 / 取消点赞（入口）
    // =====================================================

    /**
     * 点赞或取消点赞（业务入口）
     * <p>
     * 流程：
     * <ol>
     *   <li>根据参数判断执行点赞还是取消点赞</li>
     *   <li>操作结果写入 Redis Hash（替代原来的 Set，Hash 能同时存储 bizType 和时间戳）</li>
     *   <li>将本次变更的业务 ID 标记为"脏数据"，等待定时任务同步到 MySQL</li>
     *   <li>统计该业务的点赞总数，更新到 ZSet</li>
     * </ol>
     * </p>
     */
    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 1. 基于前端参数，判断执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        // 2. 操作失败则直接结束
        if (!success) {
            return;
        }
        // 3. 操作成功后，统计该业务的点赞总数
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long likedTimes = redisTemplate.opsForHash().size(bizKey);
        if (likedTimes == null) {
            return;
        }
        // 4. 缓存点赞总数到 Redis ZSet（供定时任务读取并发送MQ）
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                likedTimes.doubleValue());
    }

    // =====================================================
    //  点赞操作（Redis Hash 写入）
    // =====================================================

    /**
     * 执行点赞
     * <p>
     * 使用 Redis Hash 存储，Hash 的 key 为 {@code likes:biz:{bizId}}，
     * field = userId，value = "bizType:时间戳毫秒"，便于后续定时任务解析并同步到 MySQL。
     * 操作完成后将 bizId 加入脏标记集合，通知定时任务进行持久化。
     * </p>
     *
     * @return true=点赞成功，false=已经点过赞（重复操作）
     */
    private boolean like(LikeRecordFormDTO recordDTO) {
        Long userId = UserContext.getUser();
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();

        // 使用 HSETNX 原子判断：如果该用户已经点赞，返回false
        // value 存储格式：bizType:时间戳毫秒，供后续 MySQL 同步时解析
        String value = recordDTO.getBizType() + ":" + System.currentTimeMillis();
        Boolean added = redisTemplate.opsForHash().putIfAbsent(bizKey, userId.toString(), value);

        if (Boolean.FALSE.equals(added)) {
            return false; // 已经点过赞了，不重复处理
        }

        // 标记该业务ID为"脏数据"，定时任务会据此同步到MySQL
        redisTemplate.opsForSet().add(RedisConstants.LIKE_BIZ_DIRTY_KEY, recordDTO.getBizId().toString());
        return true;
    }

    // =====================================================
    //  取消点赞操作（Redis Hash 删除）
    // =====================================================

    /**
     * 取消点赞
     * <p>
     * 从 Redis Hash 中移除用户的点赞记录。
     * 操作完成后同样将 bizId 加入脏标记集合，
     * 定时任务同步时会发现该用户已取消，从而从 MySQL 删除对应记录。
     * </p>
     *
     * @return true=取消成功，false=本来就没点赞（重复操作）
     */
    private boolean unlike(LikeRecordFormDTO recordDTO) {
        Long userId = UserContext.getUser();
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();

        // 删除 Hash 中的用户点赞记录
        Long deleted = redisTemplate.opsForHash().delete(bizKey, userId.toString());
        if (deleted == null || deleted == 0) {
            return false; // 本来就没点赞
        }

        // 如果删除后 Hash 为空，把 Hash key 也删掉（释放 Redis 内存）
        Long remaining = redisTemplate.opsForHash().size(bizKey);
        if (remaining != null && remaining == 0) {
            redisTemplate.delete(bizKey);
        }

        // 标记脏数据，通知定时任务同步到MySQL（会删除MySQL中的记录）
        redisTemplate.opsForSet().add(RedisConstants.LIKE_BIZ_DIRTY_KEY, recordDTO.getBizId().toString());
        return true;
    }

    // =====================================================
    //  查询点赞状态（Cache-Aside 模式）
    // =====================================================

    /**
     * 批量查询当前用户对指定业务是否已点赞
     *
     * <h3>Cache-Aside 查询流程</h3>
     * <pre>
     * 用户查询 bizIds=[1, 2, 3, 4]
     *          │
     *    阶段1: Redis pipeline 批量检查 Hash 是否存在
     *          │
     *     ├── 1: Hash存在  → hExists(1, userId) → true  → 加入结果集
     *     ├── 2: Hash存在  → hExists(2, userId) → false → 不加入
     *     ├── 3: Hash不存在 → 检查空标记
     *     │        ├── 空标记存在 → 直接返回false（MySQL也不用查了）
     *     │        └── 空标记不存在 → 进入阶段3（查MySQL）
     *     └── 4: 同上
     *          │
     *    阶段3: MySQL 批量查询 (SELECT ... WHERE user_id=? AND biz_id IN (3,4))
     *          │
     *    阶段4: 回种 Redis
     *          ├── bizId=3 MySQL有记录 → 重建Hash到Redis → 加入结果集
     *          └── bizId=4 MySQL无记录 → 写入空标记 likes:empty:biz:4（防穿透）
     * </pre>
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long userId = UserContext.getUser();

        // ===== 阶段1：Redis pipeline 批量检查哪些 bizId 的 Hash 存在 =====
        List<Object> hashExistResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                src.exists(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId);
            }
            return null;
        });

        // 按 Hash 是否存在将 bizId 分为两类
        List<Long> cacheHits = new ArrayList<>();  // Redis 中已有 Hash 的
        List<Long> cacheMisses = new ArrayList<>(); // Redis 中没有 Hash 的
        for (int i = 0; i < bizIds.size(); i++) {
            if ((boolean) hashExistResults.get(i)) {
                cacheHits.add(bizIds.get(i));
            } else {
                cacheMisses.add(bizIds.get(i));
            }
        }

        Set<Long> likedBizIds = new HashSet<>();

        // ===== 阶段2：对已有 Hash 的 bizId，检查当前用户是否在其中 =====
        if (!cacheHits.isEmpty()) {
            List<Object> hExistsResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection src = (StringRedisConnection) connection;
                for (Long bizId : cacheHits) {
                    src.hExists(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
                }
                return null;
            });
            for (int i = 0; i < cacheHits.size(); i++) {
                if ((boolean) hExistsResults.get(i)) {
                    likedBizIds.add(cacheHits.get(i)); // 用户在 Hash 中 → 已点赞
                }
            }
        }

        // ===== 阶段3：对 Redis Miss 的 bizId，先检查空标记，再决定是否查 MySQL =====
        if (!cacheMisses.isEmpty()) {
            // 3.1 检查空标记（防缓存穿透：如果空标记存在，说明之前已经确认过 MySQL 也没数据）
            List<Object> emptyMarkResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection src = (StringRedisConnection) connection;
                for (Long bizId : cacheMisses) {
                    src.exists(RedisConstants.LIKE_BIZ_EMPTY_KEY_PREFIX + bizId);
                }
                return null;
            });

            List<Long> needDbQuery = new ArrayList<>();
            for (int i = 0; i < cacheMisses.size(); i++) {
                if (!(boolean) emptyMarkResults.get(i)) {
                    // 空标记不存在 → 需要查 MySQL
                    needDbQuery.add(cacheMisses.get(i));
                }
                // 空标记存在 → 证明 MySQL 中也无数据，跳过，不加入结果集
            }

            // 3.2 对确实需要查 MySQL 的 bizId，批量查询
            if (!needDbQuery.isEmpty()) {
                List<LikedRecord> dbRecords = this.lambdaQuery()
                        .in(LikedRecord::getBizId, needDbQuery)
                        .eq(LikedRecord::getUserId, userId)
                        .list();

                // MySQL 中命中的 bizId 集合
                Set<Long> dbHitSet = dbRecords.stream()
                        .map(LikedRecord::getBizId)
                        .collect(Collectors.toSet());

                // MySQL 中也未命中的 bizId 集合（需要设空标记防止下次穿透）
                Set<Long> dbMissSet = new HashSet<>(needDbQuery);
                dbMissSet.removeAll(dbHitSet);

                // 3.3 回种 Redis：MySQL 有记录 → 重建 Hash 到 Redis
                if (!dbRecords.isEmpty()) {
                    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                        StringRedisConnection src = (StringRedisConnection) connection;
                        for (LikedRecord record : dbRecords) {
                            String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + record.getBizId();
                            String value = record.getBizType() + ":" +
                                    record.getCreateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
                            src.hSet(bizKey, record.getUserId().toString(), value);
                        }
                        return null;
                    });
                    likedBizIds.addAll(dbHitSet);
                }

                // 3.4 回种 Redis：MySQL 无记录 → 写入空标记（防缓存穿透）
                if (!dbMissSet.isEmpty()) {
                    long ttlSeconds = RedisConstants.EMPTY_MARK_TTL_MINUTES * 60;
                    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                        StringRedisConnection src = (StringRedisConnection) connection;
                        for (Long bizId : dbMissSet) {
                            src.setEx(
                                    RedisConstants.LIKE_BIZ_EMPTY_KEY_PREFIX + bizId,
                                    ttlSeconds,
                                    "0");
                        }
                        return null;
                    });
                }
            }
        }

        return likedBizIds;
    }

    // =====================================================
    //  点赞总数统计（保持不变）
    // =====================================================

    /**
     * 定时任务：读取 Redis 中缓存的点赞总数，通过 MQ 发送给下游消费
     * <p>
     * 从 ZSet 中 popMin 取出点赞数变更记录，转换为 DTO 列表后发送 MQ消息。
     * 该方法仅负责"点赞总数"的异步通知，不涉及点赞记录本身的持久化。
     * </p>
     */
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1. 读取并移除 Redis 中缓存的点赞总数（ZSet popMin）
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        // 2. 数据转换：TypedTuple → LikedTimesDTO
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        // 3. 发送 MQ 消息给下游服务（如 tj-learning 更新互动回复的点赞数）
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list);
    }

    // =====================================================
    //  Redis 脏数据 → MySQL 持久化（新增核心方法）
    // =====================================================

    /**
     * 将 Redis 中的脏点赞记录同步到 MySQL
     *
     * <h3>同步流程</h3>
     * <pre>
     * 1. 从脏标记集合 likes:dirty:bizIds 中随机取出 maxBizSize 个业务ID
     * 2. 对每个 bizId：
     *    a. HGETALL 获取 Redis Hash 中所有点赞用户及详情
     *    b. 查询 MySQL 中该 bizId 的现有记录
     *    c. 对比差异：
     *       - Redis有、MySQL无 → 批量 INSERT
     *       - MySQL有、Redis无 → 批量 DELETE（用户取消了点赞）
     *    d. 从脏标记集合中移除该 bizId
     * </pre>
     *
     * @param bizType    业务类型（用于日志区分）
     * @param maxBizSize 单次处理的最大业务数量
     */
    @Override
    public void syncDirtyRecordsToMysql(String bizType, int maxBizSize) {
        // 1. 从脏标记集合中取出待处理的业务ID（SPOP 原子操作）
        //    注意：opsForSet().pop() 一次只能弹出一个，循环取出
        List<String> dirtyBizIdStrs = new ArrayList<>();
        for (int i = 0; i < maxBizSize; i++) {
            String bizId = redisTemplate.opsForSet().pop(RedisConstants.LIKE_BIZ_DIRTY_KEY);
            if (bizId == null) {
                break; // 没有更多脏数据了
            }
            dirtyBizIdStrs.add(bizId);
        }

        if (dirtyBizIdStrs.isEmpty()) {
            return;
        }

        log.debug("[点赞同步] 开始处理脏数据，bizType={}, bizIds={}", bizType, dirtyBizIdStrs);

        for (String bizIdStr : dirtyBizIdStrs) {
            try {
                syncSingleBizToMysql(Long.valueOf(bizIdStr));
            } catch (Exception e) {
                log.error("[点赞同步] 同步bizId={}失败，重新放回脏标记集合", bizIdStr, e);
                // 同步失败：放回脏标记集合，下次再试
                redisTemplate.opsForSet().add(RedisConstants.LIKE_BIZ_DIRTY_KEY, bizIdStr);
            }
        }
    }

    /**
     * 将单个业务ID的点赞记录从 Redis 同步到 MySQL
     */
    private void syncSingleBizToMysql(Long bizId) {
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;

        // ===== 步骤A：从 Redis Hash 获取所有点赞用户 =====
        // Hash field = userId(String), value = "bizType:timestampMillis"
        Map<Object, Object> redisEntries = redisTemplate.opsForHash().entries(bizKey);

        // ===== 步骤B：查询 MySQL 中该 bizId 的现有记录 =====
        List<LikedRecord> mysqlRecords = this.lambdaQuery()
                .eq(LikedRecord::getBizId, bizId)
                .list();

        Set<Long> redisUserIds = redisEntries.keySet().stream()
                .map(k -> Long.valueOf(k.toString()))
                .collect(Collectors.toSet());

        Set<Long> mysqlUserIds = mysqlRecords.stream()
                .map(LikedRecord::getUserId)
                .collect(Collectors.toSet());

        // ===== 步骤C：找出需要删除的记录（MySQL有、Redis无 = 用户取消了点赞） =====
        Set<Long> toDelete = new HashSet<>(mysqlUserIds);
        toDelete.removeAll(redisUserIds);
        if (!toDelete.isEmpty()) {
            this.lambdaUpdate()
                    .eq(LikedRecord::getBizId, bizId)
                    .in(LikedRecord::getUserId, toDelete)
                    .remove();
            log.debug("[点赞同步] bizId={} 删除了 {} 条取消点赞记录", bizId, toDelete.size());
        }

        // ===== 步骤D：找出需要插入的记录（Redis有、MySQL无 = 新点赞） =====
        if (!redisEntries.isEmpty()) {
            List<LikedRecord> toUpsert = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : redisEntries.entrySet()) {
                Long userId = Long.valueOf(entry.getKey().toString());
                // 解析 value：格式 "bizType:timestampMillis"
                String value = entry.getValue().toString();
                int colonIdx = value.lastIndexOf(':');
                String recordBizType = colonIdx > 0 ? value.substring(0, colonIdx) : "";
                long timestampMillis = colonIdx > 0 ? Long.parseLong(value.substring(colonIdx + 1)) : System.currentTimeMillis();

                LikedRecord record = new LikedRecord();
                record.setUserId(userId);
                record.setBizId(bizId);
                record.setBizType(recordBizType);
                record.setCreateTime(LocalDateTime.ofEpochSecond(
                        timestampMillis / 1000, (int) (timestampMillis % 1000) * 1_000_000,
                        ZoneOffset.ofHours(8)));
                record.setUpdateTime(LocalDateTime.now());
                toUpsert.add(record);
            }

            // 批量 UPSERT（利用 MySQL ON DUPLICATE KEY UPDATE）
            this.baseMapper.batchUpsert(toUpsert);
            log.debug("[点赞同步] bizId={} 批量upsert了 {} 条记录", bizId, toUpsert.size());
        }
    }
}
