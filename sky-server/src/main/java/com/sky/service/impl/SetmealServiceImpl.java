package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;


    /**
     * 新增菜品
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        log.debug("setmeal对象：{}", setmeal);
        //将套餐信息存入套餐表
        setmealMapper.insert(setmeal);
        //组装套餐菜品信息
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //为每个套餐菜品设置套餐id
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmeal.getId());
        });
        //将套餐所包含的菜品信息存入套餐菜品关系表
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        //开启分页
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        //分页查询
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        log.debug("菜品分页查询结果：{}", page.getResult());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 套餐批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        //ids为setmeal表的多个id，可能为空、1、n个

        //判断当前套餐是否能够删除--是否存在起售中的套餐
        ids.forEach(setmealId -> {
            //查询当前id对应的套餐是否是起售状态
            Dish dish = setmealMapper.getById(setmealId);
            if (dish.getStatus() == StatusConstant.ENABLE) {
                //起售状态，抛出异常，删除失败
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        });
        //删除
        ids.forEach(setmealId -> {
            //批量删除套餐菜品表setmeal_dish的数据
            setmealDishMapper.deleteBySetmealId(setmealId);
            //删除套餐
            setmealMapper.deleteById(setmealId);
        });

    }
}
