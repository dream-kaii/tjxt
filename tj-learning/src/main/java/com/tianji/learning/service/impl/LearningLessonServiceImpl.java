package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;
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
    * 查询用户最近的学习课程
    * */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //获取用户id
        Long userId = UserContext.getUser();
        //根据用户id查询最近一次课程学习
        // select *　from learning_lesson where user_id=userId and status = 1 orderby latest_time limit 1;
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson == null){
            //课程为空
            return null;
        }
        //将对象转换为vo类
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        //补充vo类信息
        CourseFullInfoDTO infoById = courseClient.getCourseInfoById(lesson.getId(), false, false);
        if(infoById == null){
            throw new BadRequestException("课程不存在！");
        }
        vo.setSections(infoById.getSectionNum());
        vo.setCourseCoverUrl(infoById.getCoverUrl());
        vo.setCourseName(infoById.getName());
        //统计用户课程总数
        Integer count = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(count);
        //查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    /*
    * 根据课程id查询课程状态以及课程信息
    * */
    @Override
    public LearningLessonVO queryMyLessonStatus(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson == null){
            //说明当前用户中不存在该课程
            return null;
        }
        //转换为vo类
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        return vo;
    }

    /*
    * 根据课程id删除用户的课程
    * */
    @Override
    public void deleteMyLesson(List courseIds) {
        //获取用户id
        Long userId = UserContext.getUser();
        // select * from learning_lesson where user_id = #{userId} and course_id = #{courseId}
        QueryWrapper<LearningLesson> wrapper=new QueryWrapper<>();
        wrapper.eq("user_id",userId);
        wrapper.eq("courser_id",courseIds);
        this.remove(wrapper);
    }

    /*
    * 根据课程id和用户查询课程是否过期
    * */
    @Override
    public Long isLessonValid(Long courseId) {
        //获取用户id
        Long userId = UserContext.getUser();
        //select * from learning_lesson where user_id=#{userId} and course_id=#{courseId}
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();
        if(lesson == null){
            //没有该课程
            return null;
        }
        //存在该课程 查询课程状态是否过期
        if(lesson.getStatus() == LessonStatus.EXPIRED){
            //说明该课程已经过期
            return null;
        }
        //返回课表id
        return lesson.getId();
    }

     /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        //select count(*) from learning_lesson where course_id=courseId;
        Integer count = lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();
        return count;
    }

    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        return lambdaQuery().eq(LearningLesson::getUserId,userId).eq(LearningLesson::getCourseId,courseId).one();
    }


    /*
    * 创建用户当前课程的学习计划
    * */
    @Override
    public void createLearningPlan(LearningPlanDTO dto) {
        //查询用户id
        Long userId = UserContext.getUser();
        //查询课程
        LearningLesson lesson = queryByUserAndCourseId(userId, dto.getCourseId());
        if(lesson == null){
            throw new BizIllegalException("课程不存在！");
        }
        //修改课程计划属性
        LearningLesson le = new LearningLesson();
        le.setId(lesson.getId());
        le.setWeekFreq(dto.getFreq());
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN){
            le.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        boolean b = updateById(le);
        if(!b){
            throw new BizIllegalException("课程计划状态修改失败！");
        }

    }

    /**
     * 分页查询我的学习计划
     *
     * @param query
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        // 获取当前登录用户id
        Long userId = UserContext.getUser();

        // todo 查询积分

        // 查询本周学习计划总数据 learning_lesson userId status in(0,1) plan_status=1 查询sum(week_freq)
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        // 查询结果是单行单列 能用getMap
        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }

        // 查询本周 已学习的计划总数据 learning_record 条件 userId finished_time在本周之间 finished为true
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        Integer weekFinishedPlanNum = recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));

        // 查询课表数据 learning_lesson 条件 userId status in(0,1) plan_status =1 分页
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }
        // 远程调用课程服务 获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程不存在");
        }
        // 将cinfo list转map  <课程id CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> cInfosMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        // 查询学习记录表 本周 当前用户下 每一门课程 已学习的小节数量
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        // 借userId保存count(*)的值
        rWrapper.select("lesson_id as LessonId", "count(*) as userId");
        rWrapper.eq("user_id", userId);
        rWrapper.eq("finished", true);
        rWrapper.between("finish_time", weekBeginTime, weekEndTime);
        rWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = recordMapper.selectList(rWrapper);
        // map 中的key是lessonId value是当前用户对该课程下已学习的小节数量
        Map<Long, Long> courseWeekFinishNumMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));

        // 封装vo返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);
        List<LearningPlanVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyProperties(record, LearningPlanVO.class);
            CourseSimpleInfoDTO infoDTO = cInfosMap.get(record.getCourseId());
            if (infoDTO != null) {
                planVO.setCourseName(infoDTO.getName());// 课程名
                planVO.setSections(infoDTO.getSectionNum());// 课程下的总小节数
            }

            // Long aLong = courseWeekFinishNumMap.get(record.getId());
            // if (aLong != null) {
            //     planVO.setWeekLearnedSections(aLong.intValue());
            // } else {
            //     planVO.setWeekLearnedSections(0);// 本周已学习小节数
            // }

            planVO.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(),0L).intValue());
            voList.add(planVO);
        }
        // vo.setList(voList);
        // vo.setTotal(page.getTotal());
        // vo.setPages(page.getPages());
        // return vo;
        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
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
