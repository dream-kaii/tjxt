package com.tianji.learning.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LessonStatusCheckTask {
    private final ILearningLessonService lessonService;
    @Scheduled(cron = "0 * * * * ?") //每分钟执行一次
    public void lessonCheckStatus(){
        //查询未过期的所有课程
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(LearningLesson::getStatus, LessonStatus.EXPIRED);
        //获得所有对象
        List<LearningLesson> list = lessonService.list(wrapper);
        //遍历每个对象比较时间
        LocalDateTime now = LocalDateTime.now();
        for (LearningLesson learningLesson : list) {
            LocalDateTime expireTime = learningLesson.getExpireTime();
            if(expireTime.isBefore(now)){
                //说明时间已经过期了
                learningLesson.setStatus(LessonStatus.EXPIRED);
            }
        }
        //批量修改数据库数据
        lessonService.updateBatchById(list);

    }
}
