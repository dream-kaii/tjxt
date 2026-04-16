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

@RequiredArgsConstructor
@Component
public class LessonTaskCheck {

    private final ILearningLessonService lessonService;

    /*
    * 定时检查课程是否过期
    * */
    @Scheduled(cron = "0 * * * * ?")
    public void lessonStatusCheck(){
        //查询未过期课程
        LambdaQueryWrapper<LearningLesson> wr = new LambdaQueryWrapper<>();
        wr.ne(LearningLesson::getStatus, LessonStatus.EXPIRED);
        List<LearningLesson> lessons = lessonService.list(wr);
        for (LearningLesson lesson : lessons) {
            if(lesson.getExpireTime().isBefore(LocalDateTime.now())){
                lesson.setStatus(LessonStatus.EXPIRED);
            }
        }
        lessonService.updateBatchById(lessons);
    }
}
