package com.tianji.learning.service.impl;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.jni.Local;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {
    private final RedisTemplate redisTemplate;

    /*
    * 新增积分明细
    * */
    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        int realPoints = points;
        LocalDateTime now = LocalDateTime.now();
        int maxPoints = type.getMaxPoints();
        // 1.判断当前方式有没有积分上限
        if(maxPoints > 0){
            // 2.有，则需要判断是否超过上限
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            // 2.1.查询今日已得积分
            int TPoints = queryUserPointsByTypeAndDate(userId,type,begin,end);
            // 2.2.判断是否超过上限
            if(TPoints >= maxPoints){
                // 2.3.超过，直接结束
                return;
            }
            // 2.4.没超过，保存积分记录
            if(TPoints + realPoints > maxPoints){
                realPoints = maxPoints - TPoints;
            }
        }
        // 3.没有，直接保存积分记录
        this.savePointsRecord(userId,realPoints ,type);
        // 4.更新总积分到Redis
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);
    }

    /*
    * 查询用户今日所有类型积分情况
    * */
    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        //获取用户ID
        Long userId = UserContext.getUser();
        //根据用户ID和今日时间查询所有积分记录
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        List<PointsRecord> list = lambdaQuery()
                .eq(PointsRecord::getUserId, userId)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end)
                .list();
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        // 按类型分组，累加每个类型的积分值
        Map<PointsRecordType, Integer> pointsMap = new LinkedHashMap<>();
        for (PointsRecord record : list) {
            PointsRecordType type = record.getType();
            pointsMap.merge(type, record.getPoints(), Integer::sum);
        }
        // 封装到result集合
        List<PointsStatisticsVO> result = new ArrayList<>(pointsMap.size());
        for (Map.Entry<PointsRecordType, Integer> entry : pointsMap.entrySet()) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            PointsRecordType type = entry.getKey();
            vo.setType(type);                 // 积分类型
            vo.setPoints(entry.getValue());   // 该类型今日累计积分
            vo.setMaxPoints(type.getMaxPoints()); // 该类型今日积分上限
            result.add(vo);
        }
        return result;
    }


    /*
    * 查询用户今日当前类型的获取的积分
    * */
    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
        List<PointsRecord> records = this.lambdaQuery()
                .eq(PointsRecord::getUserId, userId)
                .eq(PointsRecord::getType, type)
                .between(begin != null && end != null, PointsRecord::getCreateTime, begin, end)
                .list();
        if(CollUtils.isEmpty(records)){
            //如果集合为空则说明今日还没有获取积分
            return 0;
        }
        int nums = 0;
        for (PointsRecord pointsRecord : records) {
            Integer points = pointsRecord.getPoints();
            nums +=points;
        }
        return nums;
    }


    /*
    * 保存积分明细对象
    * */
    public void savePointsRecord(Long userId,int points,PointsRecordType type){
        PointsRecord pointsRecord = new PointsRecord()
                .setPoints(points)
                .setUserId(userId)
                .setType(type)
                .setCreateTime(LocalDateTime.now());
        this.save(pointsRecord);
    }
}
