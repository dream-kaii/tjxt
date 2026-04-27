package com.tianji.learning.utils;


import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.models.auth.In;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.DelayQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {
    private final RedisTemplate redisTemplate;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private static volatile boolean begin = true;
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;

    public void handleDelayTask(){
        while (begin){
            try {
                //1.查询到期的延迟任务
                DelayTask<RecordTaskData> take = queue.take();
                RecordTaskData data = take.getData();
                //2.查询Redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null){
                    continue;
                }
                //3.比较数据，moment值
                if (!Objects.equals(data.getMoment(),record.getMoment())){
                    //如果不一样，说明用户还在持久提交播放进度，放弃旧数据
                    continue;
                }
                //4.一致，持久化播放进度数据保存到数据库中
                //4.1更新学习记录的moment值
                record.setFinished(null);
                recordMapper.updateById(record);
                //4.2更新课表最近学习信息
                LearningLesson lesson = new LearningLesson();
                lesson.setId(record.getLessonId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lesson.setLatestSectionId(record.getSectionId());
                lessonService.updateById(lesson);

            } catch (InterruptedException e) {
                log.error("处理延迟任务发生异常", e);
            }

        }

    }

    public void addLearningRecordTask(LearningRecord record){
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    private void writeRecordCache(LearningRecord record) {
        //将学习进度数据保存到redis中
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    private LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            //读取redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if(cacheData == null){
                return null;
            }
            //2.数据检查和转换
            return JsonUtils.toBean(cacheData.toString(),LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常",e);
            return null;
        }


    }


    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    //创建一个内部类  用于存储到redis的对象数据 用于判断是否存在记录
    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;
        public RecordTaskData(LearningRecord record){
            this.lessonId=record.getLessonId();
            this.sectionId=record.getSectionId();
            this.moment=record.getMoment();
        }
    }

}
