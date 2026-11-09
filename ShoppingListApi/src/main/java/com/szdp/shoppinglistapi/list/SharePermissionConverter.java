package com.szdp.shoppinglistapi.list;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class SharePermissionConverter implements AttributeConverter<SharePermission, String> {
    @Override
    public String convertToDatabaseColumn(SharePermission permission) {
        return permission == null ? null : permission.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public SharePermission convertToEntityAttribute(String permission) {
        return permission == null ? null : SharePermission.valueOf(permission.toUpperCase(Locale.ROOT));
    }
}
