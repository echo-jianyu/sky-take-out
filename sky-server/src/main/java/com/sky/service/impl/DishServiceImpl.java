package com.sky.service.impl;

import com.github.pagehelper.Constant;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    @Override
    @Transactional  //开始事务，保证多表操作的原子性
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        //向菜品表插入1条数据
        dishMapper.insert(dish);
        //获取插入数据时所生成的主键id
        Long dishId = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null || flavors.size() > 0) {
            //遍历集合，设置dishId
            flavors.forEach(dishFlavor -> {
                dishFlavor.setDishId(dishId);
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }

    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        //开启分页
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        //查询数据库获取菜品，封装为Page<DishVO>
        Page<DishVO> dishVOPage = dishMapper.pageQuery(dishPageQueryDTO);
        //封装返回数据
        return new PageResult(dishVOPage.getTotal(), dishVOPage.getResult());
    }

    /**
     * 菜品批量删除
     *
     * @param ids
     */
    @Override
    @Transactional  //多表操作，需要添加事务控制
    public void deleteBatch(List<Long> ids) {
        //ids为dish表的多个id，可能为空、1、n个

        //判断当前菜品是否能够删除--是否存在起售中的商品
        ids.forEach(dishId -> {
            //查询当前id对应的菜品是否是起售状态
            Dish dish = dishMapper.getById(dishId);
            if (dish.getStatus() == StatusConstant.ENABLE) {
                //起售状态，抛出异常，删除失败
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        });
        //判断当前菜品是否存在于套餐表中--是否被套餐表关联
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && setmealIds.size() > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //删除菜品表中的菜品数据
        ids.forEach(dishId -> {
            dishMapper.deleteById(dishId);
            //将对应的菜品口味删除
            dishFlavorMapper.deleteByDishId(dishId);
        });

    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //根据id查询dish
        Dish dish = dishMapper.getById(id);
        //根据dishId查询dish_flavor表
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        //封装为DishVO对象返回
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        //设置菜品口味
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 更新菜品
     * @param dishDTO
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        //根据id更新dish表中对应的数据
        dishMapper.update(dish);

        //直接删除菜品对应的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //重新往dish_flavor表插入口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        flavors.forEach(dishFlavor -> {
            dishFlavor.setDishId(dishDTO.getId());
        });
        dishFlavorMapper.insertBatch(flavors);
    }

    /**
     * 根据分类id获取菜品
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> getByCategoryId(Long categoryId) {
        //创建Dish对象，使用对象属性查询dish表
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);  //指定分类id
        dish.setStatus(StatusConstant.ENABLE);  //指定状态为启用

        List<Dish> dishList = dishMapper.list(dish);
        return dishList;
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    /**
     * 启售停售菜品
     * @param id
     * @param status
     */
    @Override
    public void startOrStop(Long id, Integer status) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.update(dish);
    }
}
