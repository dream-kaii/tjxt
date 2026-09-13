package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-06-19
 */
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
@Api
public class CouponController {
    private final ICouponService couponService;

    @PostMapping
    @ApiOperation("新增优惠劵")
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO){
        couponService.saveCoupon(couponFormDTO);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券接口")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }


    @PutMapping("{id}/issue")
    @ApiOperation("发放优惠券")
    public void beginIssue (@RequestBody @Valid CouponIssueFormDTO dto){
        couponService.beginIssue(dto);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改优惠券")
    public void updateCoupon(@PathVariable Long id,@RequestBody @Valid CouponFormDTO dto){
        couponService.updateCouponById(id,dto);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除优惠券")
    public void deleteCoupon(@PathVariable Long id){
        couponService.deleteCoupon(id);
    }

    @GetMapping("{id}")
    @ApiOperation("查询优惠券")
    public CouponDetailVO getCoupon(@PathVariable Long id){
        return couponService.getCoupon(id);
    }
}
