package vn.com.d2s.smr.dto.ad.permission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PermissionEffect {
    INHERIT("Inherit"),
    ALLOW("Allow"),
    DENY("Deny");

    private final String wireValue;

    PermissionEffect(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static PermissionEffect fromWireValue(String value) {
        for (PermissionEffect effect : values()) {
            if (effect.wireValue.equalsIgnoreCase(value) || effect.name().equalsIgnoreCase(value)) {
                return effect;
            }
        }
        throw new IllegalArgumentException("Hiệu lực quyền không hợp lệ: " + value);
    }
}
