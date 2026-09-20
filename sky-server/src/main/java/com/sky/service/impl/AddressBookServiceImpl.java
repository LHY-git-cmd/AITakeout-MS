package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import com.sky.service.order.DeliveryRangeService;
import com.sky.vo.AddressValidationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 地址簿服务：所有 ID 操作均按当前用户隔离，并在地址变化时执行配送预检。 */
@Service
@RequiredArgsConstructor
public class AddressBookServiceImpl implements AddressBookService {
    private final AddressBookMapper addressBookMapper;
    private final DeliveryRangeService deliveryRangeService;

    @Override public List<AddressBook> list(AddressBook addressBook) { return addressBookMapper.list(addressBook); }

    @Override
    public AddressBook save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        applyValidation(addressBook);
        addressBookMapper.insert(addressBook);
        return addressBook;
    }

    @Override public AddressBook getById(Long id) { return getOwnedAddress(id); }

    @Override
    public AddressBook update(AddressBook addressBook) {
        getOwnedAddress(addressBook.getId());
        addressBook.setUserId(BaseContext.getCurrentId());
        applyValidation(addressBook);
        addressBookMapper.update(addressBook);
        return addressBook;
    }

    @Override
    @Transactional
    public void setDefault(AddressBook addressBook) {
        getOwnedAddress(addressBook.getId());
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.updateIsDefaultByUserId(addressBook);
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    @Override public void deleteById(Long id) {
        getOwnedAddress(id);
        addressBookMapper.deleteByIdAndUserId(id, BaseContext.getCurrentId());
    }

    @Override
    public AddressValidationVO validate(Long id) {
        AddressBook address = getOwnedAddress(id);
        DeliveryRangeService.ValidationResult result = applyValidation(address);
        address.setUserId(BaseContext.getCurrentId());
        addressBookMapper.update(address);
        return toView(address, result.feeCent());
    }

    private DeliveryRangeService.ValidationResult applyValidation(AddressBook address) {
        address.setGeocodeStatus("PENDING");
        DeliveryRangeService.ValidationResult result = deliveryRangeService.validate(address);
        address.setMapProvider(result.provider());
        address.setValidatedAt(LocalDateTime.now());
        address.setValidationMessage(result.message());
        address.setDeliveryRuleVersion(result.ruleVersion());
        address.setDeliverable(result.success() && result.deliverable());
        if (result.success()) {
            address.setGeocodeStatus(result.deliverable() ? "VALID" : "OUT_OF_RANGE");
            address.setLatitude(result.coordinate().latitude());
            address.setLongitude(result.coordinate().longitude());
            address.setDistanceMeters(result.distanceMeters());
        } else {
            address.setGeocodeStatus("MAP_TIMEOUT".equals(result.errorCode()) ? "TEMPORARY_FAILURE" : "INVALID");
        }
        return result;
    }

    private AddressValidationVO toView(AddressBook address, long feeCent) {
        return AddressValidationVO.builder().addressId(address.getId()).status(address.getGeocodeStatus())
                .deliverable(address.getDeliverable()).distanceMeters(address.getDistanceMeters())
                .deliveryFeeCent(feeCent).mapProvider(address.getMapProvider())
                .ruleVersion(address.getDeliveryRuleVersion()).message(address.getValidationMessage())
                .validatedAt(address.getValidatedAt()).build();
    }

    private AddressBook getOwnedAddress(Long id) {
        if (id == null) throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        AddressBook address = addressBookMapper.getByIdAndUserId(id, BaseContext.getCurrentId());
        if (address == null) throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        return address;
    }
}
