package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课表 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-04-12
 */
@SuppressWarnings("ALL")
@RequiredArgsConstructor
@Slf4j
@Service
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    /*
    * 添加课程到用户中
    * */
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量新增
        saveBatch(list);
    }

    /*
    * 分页查询我的课表中的课程 存在排序
    * */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.根据用户ID进行分页查询 select *　from learning_lesson where user_id=#{userId} by latest_learn_time limit 0,5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3.查询课程信息 并进行封装
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        //4.将课程转换为vo对象进行返回
        for (LearningLesson lesson : records) {
            //4.1拷贝基础属性到vo
            LearningLessonVO learningLessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
            //4.2补充课程信息到vo
            CourseSimpleInfoDTO courseSimpleInfoDTO = cMap.get(lesson.getCourseId());
            learningLessonVO.setCourseName(courseSimpleInfoDTO.getName());
            learningLessonVO.setCourseCoverUrl(courseSimpleInfoDTO.getCoverUrl());
            learningLessonVO.setSections(courseSimpleInfoDTO.getSectionNum());
            list.add(learningLessonVO);
        }

        return PageDTO.of(page,list);

    }


    /*
    * 将分页课程结果封装为 <courseId，course> 形式
    * */
    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //1.通过stream流获取课程ID
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //根据课程ID查询课程卡片详情信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if(CollUtils.isEmpty(cInfoList)){
            //课程不存在 无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList
                .stream()
                .collect(Collectors
                        .toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }
}
