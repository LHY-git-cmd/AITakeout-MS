package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;

    @Mock
    private SetmealDishMapper setmealDishMapper;

    @Mock
    private SetmealMapper setmealMapper;

    @InjectMocks
    private DishServiceImpl dishService;

    private static final Long DISH_ID = 10L;

    @Test
    void shouldStopDishAndAllRelatedSetmeals() {
        when(setmealDishMapper.getSetmealIdsByDishId(DISH_ID)).thenReturn(List.of(20L, 21L));

        dishService.startOrStop(StatusConstant.DISABLE, DISH_ID);

        ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).updateStatus(dishCaptor.capture());
        assertEquals(DISH_ID, dishCaptor.getValue().getId());
        assertEquals(StatusConstant.DISABLE, dishCaptor.getValue().getStatus());

        ArgumentCaptor<Setmeal> setmealCaptor = ArgumentCaptor.forClass(Setmeal.class);
        verify(setmealMapper, org.mockito.Mockito.times(2)).update(setmealCaptor.capture());
        assertEquals(List.of(20L, 21L),
                setmealCaptor.getAllValues().stream().map(Setmeal::getId).toList());
        setmealCaptor.getAllValues().forEach(
                setmeal -> assertEquals(StatusConstant.DISABLE, setmeal.getStatus()));
    }

    @Test
    void shouldNotChangeSetmealsWhenDishIsStarted() {
        dishService.startOrStop(StatusConstant.ENABLE, DISH_ID);

        verify(dishMapper).updateStatus(org.mockito.ArgumentMatchers.any(Dish.class));
        verify(setmealDishMapper, never()).getSetmealIdsByDishId(DISH_ID);
        verify(setmealMapper, never()).update(org.mockito.ArgumentMatchers.any(Setmeal.class));
    }

    @Test
    void shouldHandleDishWithoutRelatedSetmeals() {
        when(setmealDishMapper.getSetmealIdsByDishId(DISH_ID)).thenReturn(List.of());

        dishService.startOrStop(StatusConstant.DISABLE, DISH_ID);

        verify(dishMapper).updateStatus(org.mockito.ArgumentMatchers.any(Dish.class));
        verify(setmealMapper, never()).update(org.mockito.ArgumentMatchers.any(Setmeal.class));
    }
}
