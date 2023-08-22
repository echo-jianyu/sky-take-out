package com.sky.service.impl;

import com.fasterxml.jackson.databind.util.BeanUtil;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);  //属性拷贝
        shoppingCart.setUserId(BaseContext.getCurrentId());  //设置当前登录人的userId
        //判断当前加入到购物车中的商品是否已经存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);  //list只有两种情况：null或list.size=1
        //如果已经存在，只需将数量+1
        if (list != null && list.size() > 0) {
            //已存在，获取第一条数据（唯一一条）
            ShoppingCart cart = list.get(0);
            //数量+1
            cart.setNumber(cart.getNumber() + 1);
            //更新该条数据
            shoppingCartMapper.updateNumberById(cart);
        } else {
            //不存在，则需要插入一条购物车数据
            Long dishId = shoppingCartDTO.getDishId();
            //判断本次添加到购物车的是套餐还是菜品
            if (dishId != null) {
                //是菜品，查询相关信息，记录在ShoppingCart中
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());

            } else {
                //是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            //往shoppingCart对象设置数量和时间
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            //将shoppingCart插入到shopping_cart表中
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        //删除所有和该用户相关的购物车数据
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());

    }
}
