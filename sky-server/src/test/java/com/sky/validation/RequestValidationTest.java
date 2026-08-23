package com.sky.validation;

import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.ShoppingCartDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectBlankEmployeeCredentials() {
        EmployeeLoginDTO request = new EmployeeLoginDTO();
        assertEquals(2, validator.validate(request).size());
    }

    @Test
    void shouldRequireExactlyOneShoppingCartTarget() {
        ShoppingCartDTO empty = new ShoppingCartDTO();
        ShoppingCartDTO both = new ShoppingCartDTO();
        both.setDishId(1L);
        both.setSetmealId(2L);
        ShoppingCartDTO dish = new ShoppingCartDTO();
        dish.setDishId(1L);

        assertEquals(1, validator.validate(empty).size());
        assertEquals(1, validator.validate(both).size());
        assertEquals(0, validator.validate(dish).size());
    }
}
