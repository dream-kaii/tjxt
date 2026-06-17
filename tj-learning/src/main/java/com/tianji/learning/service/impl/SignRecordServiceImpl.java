package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.po.SignRecord;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.SignRecordMapper;
import com.tianji.learning.mq.message.SignMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.service.ISignRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * <p>
 * 签到记录表 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl extends ServiceImpl<SignRecordMapper, SignRecord> implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    /**
     * 实现用户签到功能
     * <p>
     * 使用 Redis Bitmap 记录每日签到状态，key 格式为 {@code sign:uid:{userId}:{yyyyMM}}。
     * 每月第一天自动重置（新月份使用新的 key），签到积分无上限。
     * </p>
     *
     * <h3>奖励规则</h3>
     * <ul>
     *   <li>连续签到  7 天：奖励 10 分</li>
     *   <li>连续签到 14 天：奖励 20 分</li>
     *   <li>连续签到 28 天：奖励 40 分</li>
     * </ul>
     *
     * @return 签到结果，包含连续签到天数、固定签到得分(1分)和奖励积分
     * @throws BizIllegalException 当日已签到时抛出
     */
    @Override
    public SignResultVO addSignRecords() {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 获取当前日期信息
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int monthValue = now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();
        // 3. 构建 Redis Bitmap 的 key：sign:uid:{userId}:{yyyyMM}
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + ":" + year + String.format("%02d", monthValue);
        // 4. 检查今日是否已签到（Bitmap 偏移量 = 日期 - 1）
        int offset = dayOfMonth - 1;
        Boolean signed = redisTemplate.opsForValue().getBit(key, offset);
        if (Boolean.TRUE.equals(signed)) {
            throw new BizIllegalException("今日已签到");
        }
        // 5. 设置今日签到标记
        redisTemplate.opsForValue().setBit(key, offset, true);
        // 6. 从今天向前统计连续签到天数（当月内）
        int consecutiveDays = 0;
        for (int i = offset; i >= 0; i--) {
            Boolean bit = redisTemplate.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(bit)) {
                consecutiveDays++;
            } else {
                break;
            }
        }
        // 7. 根据连续签到天数计算奖励积分
        int rewardPoints = calcRewardPoints(consecutiveDays);
        // 8. 保存签到记录到 MySQL
        SignRecord signRecord = new SignRecord()
                .setUserId(userId)
                .setYear(Year.of(year))
                .setMonth(monthValue)
                .setDate(now)
                .setIsBackup(false);
        this.save(signRecord);
        // 9. 保存积分明细记录（固定签到分 + 奖励积分）
        int totalPoints = 1 + rewardPoints;
        /*PointsRecord pointsRecord = new PointsRecord()
                .setUserId(userId)
                .setType(PointsRecordType.SIGN)
                .setPoints(totalPoints)
                .setCreateTime(LocalDateTime.now());
        pointsRecordService.save(pointsRecord);*/
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignMessage.of(userId,totalPoints)
        );
        // 10. 组装并返回签到结果
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(consecutiveDays);
        vo.setSignPoints(1);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }


    /*
    * 查询用户本月到今天的签到情况
    * */
    @Override
    public int[] querySignRecords() {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 获取当前日期信息
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int monthValue = now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();
        // 3. 构建 Redis Bitmap 的 key：sign:uid:{userId}:{yyyyMM}
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + ":" + year + String.format("%02d", monthValue);
        //获取今天在本月的第几位
        int offset = dayOfMonth - 1;
        //
        int[] result = new int[offset];
        for (int i = 0; i <= offset; i++) {
            Boolean bit = redisTemplate.opsForValue().getBit(key, i);
            if(bit){
                //true = 1
                result [i] = 1;
            }
            else {
                result [i] = 0;
            }

        }
        return result;
    }

    /**
     * 根据连续签到天数计算奖励积分
     *
     * @param consecutiveDays 连续签到天数
     * @return 奖励积分，不满7天返回0
     */
    private int calcRewardPoints(int consecutiveDays) {
        if (consecutiveDays == 28) {
            return 40;
        } else if (consecutiveDays == 14) {
            return 20;
        } else if (consecutiveDays == 7) {
            return 10;
        }
        return 0;
    }
}
