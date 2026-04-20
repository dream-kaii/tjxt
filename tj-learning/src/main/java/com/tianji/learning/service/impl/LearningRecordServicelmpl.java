package com.tianji.learning.service.impl;

import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


@Service
@RequiredArgsConstructor
public class LearningRecordServicelmpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient CourseClient;
    /*
    * 实现查看课程的学习进度
    * */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        Long userId = UserContext.getUser();
        //根据课程id和用户id查询课表课程
        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId,courseId);
        //查询学习计划
        List<LearningRecord> list = lambdaQuery()
                .eq(LearningRecord::getLessonId,lesson.getId())
                .list();
        //封装结果
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        learningLessonDTO.setRecords(BeanUtils.copyList(list, LearningRecordDTO.class));
        return learningLessonDTO;
    }

    /*
    * 提交学习记录
    * */
    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        //获取登录用户
        Long userId = UserContext.getUser();
        boolean finished = false;
        if(dto.getSectionType() == SectionType.VIDEO){
            //说明提交的为视频类型
            finished = handleVideoRecord(userId,dto);
        }
        else {
            finished = handleExamRecord(userId,dto);
        }
        //处理课表数据
        handleLearningLessonsChanges(dto,finished);

    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO dto, boolean finished) {
        //查询课表
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if(lesson == null){
            throw new BizIllegalException("课程不存在!无法更新数据");
        }
        //判断是否有新的小节完成的
        boolean allLearned = false;
        if(finished){
            //如果有新完成的小节 则需要查询课程信息
            CourseFullInfoDTO course = CourseClient.getCourseInfoById(dto.getLessonId(), false, false);
            if(course == null){
                throw new BizIllegalException("课程不存在无法更新数据");
            }
            //比较课程是否已经全部完成了
            allLearned = lesson.getLatestSectionId()+1 > course.getSectionNum();
            //更新课表
            boolean update = lessonService
                    .lambdaUpdate()
                    .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                    .set(!finished, LearningLesson::getLatestSectionId, dto.getSectionId())
                    .set(!finished, LearningLesson::getLatestLearnTime, dto.getCommitTime())
                    .setSql(!finished, "learned_sections = learned_sections + 1")
                    .eq(LearningLesson::getId, lesson.getId())
                    .update();
            if(!update){
                throw new DbRuntimeException("学习记录更新失败！");
            }

        }


    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        // 1.转换DTO为PO
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        // 2.填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        // 3.写入数据库
        boolean success = save(record);
        if (!success) {
            throw new DbException("新增考试记录失败！");
        }
        return true;

    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        //查询旧的学习记录
        LearningRecord old = queryOldRecord(dto.getLessonId(), dto.getSectionId());
        if(old==null){
            //添加第一次学习记录
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            //填充数据
            record.setUserId(userId);
            //写入数据库
            boolean save = save(record);
            if(!save){
                throw new DbRuntimeException("新增学习记录失败！");
            }
            return false;
        }
        //如果存在 则进行更新
        //判断是否是第一次完成
        boolean finished = !old.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        // 4.2.更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime,dto.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败！");
        }
        return finished ;

    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        return lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
    }
}
