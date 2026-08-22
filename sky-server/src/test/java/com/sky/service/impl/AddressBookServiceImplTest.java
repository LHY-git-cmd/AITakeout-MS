package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceImplTest {

    @Mock
    private AddressBookMapper addressBookMapper;

    @InjectMocks
    private AddressBookServiceImpl addressBookService;

    @BeforeEach
    void setCurrentUser() {
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void clearCurrentUser() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldRejectUpdatingAddressOwnedByAnotherUser() {
        AddressBook update = AddressBook.builder().id(99L).detail("伪造修改").build();
        when(addressBookMapper.getByIdAndUserId(99L, 7L)).thenReturn(null);

        assertThrows(AddressBookBusinessException.class, () -> addressBookService.update(update));

        verify(addressBookMapper, never()).update(update);
    }

    @Test
    void shouldConstrainUpdateToCurrentUser() {
        AddressBook existing = AddressBook.builder().id(10L).userId(7L).build();
        AddressBook update = AddressBook.builder().id(10L).detail("新地址").build();
        when(addressBookMapper.getByIdAndUserId(10L, 7L)).thenReturn(existing);

        addressBookService.update(update);

        assertEquals(7L, update.getUserId());
        verify(addressBookMapper).update(update);
    }
}
