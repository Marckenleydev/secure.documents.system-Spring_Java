package marc.dev.secure_document_system.validation;

import marc.dev.secure_document_system.entity.UserEntity;
import marc.dev.secure_document_system.exception.ApiException;

public class UserValidation {
    public static void verifyAccountStatus(UserEntity userEntity) {
        if(!userEntity.isEnabled()){
            throw  new ApiException("User is disabled");
        }
        if(!userEntity.isAccountNonExpired()){ throw  new ApiException("User is expired");}
        if(!userEntity.isAccountNonLocked()){ throw  new ApiException("User is locked");}
    }
}
