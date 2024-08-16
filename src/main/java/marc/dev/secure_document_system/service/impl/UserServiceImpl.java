package marc.dev.secure_document_system.service.impl;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import marc.dev.secure_document_system.cache.CacheStore;
import marc.dev.secure_document_system.domain.RequestContext;
import marc.dev.secure_document_system.dto.User;
import marc.dev.secure_document_system.entity.ConfirmationEntity;
import marc.dev.secure_document_system.entity.CredentialEntity;
import marc.dev.secure_document_system.entity.RoleEntity;
import marc.dev.secure_document_system.entity.UserEntity;
import marc.dev.secure_document_system.enumeration.Authority;
import marc.dev.secure_document_system.enumeration.LoginType;
import marc.dev.secure_document_system.event.UserEvent;
import marc.dev.secure_document_system.exception.ApiException;
import marc.dev.secure_document_system.repository.ConfirmationRepository;
import marc.dev.secure_document_system.repository.CredentialRepository;
import marc.dev.secure_document_system.repository.RoleRepository;
import marc.dev.secure_document_system.repository.UserRepository;
import marc.dev.secure_document_system.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.time.LocalDateTime.now;
import static marc.dev.secure_document_system.constant.Constants.*;
import static marc.dev.secure_document_system.enumeration.EventType.REGISTRATION;
import static marc.dev.secure_document_system.enumeration.EventType.RESETPASSWORD;
import static marc.dev.secure_document_system.utils.UserUtils.createUserEntity;
import static marc.dev.secure_document_system.utils.UserUtils.fromUserEntity;
import static marc.dev.secure_document_system.validation.UserValidation.verifyAccountStatus;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Service
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CredentialRepository credentialRepository;
    private final ConfirmationRepository confirmationRepository;
    private final BCryptPasswordEncoder encoder;
    private final CacheStore<String, Integer> userCache;
    private final ApplicationEventPublisher publisher;

    @Override
    public void createUser(String firstName, String lastName, String email, String password) {
        var user = userRepository.findByEmailIgnoreCase(email);
        if(user.isPresent()){
            throw new ApiException("Email already exists. Use a different email and try again");

        }
        var userEntity = userRepository.save(createNewUser(firstName, lastName, email));
        var credentialEntity = new CredentialEntity(userEntity, encoder.encode(password));
        credentialRepository.save(credentialEntity);
        var confirmationEntity = new ConfirmationEntity(userEntity);
        confirmationRepository.save(confirmationEntity);
        publisher.publishEvent(new UserEvent(userEntity, REGISTRATION, Map.of("key", confirmationEntity.getToken())));
    }

    @Override
    public RoleEntity getRoleName(String name) {
        var role = roleRepository.findByNameIgnoreCase(name);
        return role.orElseThrow(() -> new ApiException("Role not found"));
    }

    @Override
    public void verifyAccount(String key) {
        var confirmationEntity = getUserConfirmation(key);
        var userEntity = getUserEntityByEmail(confirmationEntity.getUserEntity().getEmail());
        userEntity.setEnabled(true);
        userRepository.save(userEntity);
        confirmationRepository.delete(confirmationEntity);
    }

    @Override
    public void updateLoginAttempt(String email, LoginType loginType) {
        var userEntity = getUserEntityByEmail(email);
        RequestContext.setUserId(userEntity.getId());
        switch (loginType) {
            case LOGIN_ATTEMPT -> {
                if (userCache.get(userEntity.getEmail()) == null) {
                    userEntity.setLoginAttempts(0);
                    userEntity.setAccountNonLocked(true);
                }
                userEntity.setLoginAttempts(userEntity.getLoginAttempts() + 1);
                userCache.put(userEntity.getEmail(), userEntity.getLoginAttempts());
                if (userCache.get(userEntity.getEmail()) > 5) {
                    userEntity.setAccountNonLocked(false);
                }
            }
            case LOGIN_SUCCESS -> {
                userEntity.setAccountNonLocked(true);
                userEntity.setLoginAttempts(0);
                userEntity.setLastLogin(now());
                userCache.evict(userEntity.getEmail());
            }
        }
        userRepository.save(userEntity);
    }

    @Override
    public User getUserByUserId(String userId) {
        var userEntity = userRepository.findUserByUserId(userId).orElseThrow(() -> new ApiException("User not found"));
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public User getUserByEmail(String email) {
        UserEntity userEntity = getUserEntityByEmail(email);

        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public CredentialEntity getUserCredentialById(Long userId) {
        var credentialById = credentialRepository.getCredentialByUserEntityId(userId);

        return credentialById.orElseThrow(() -> new ApiException("Unable to find user credential"));
    }

    @Override
    public User setUpMfa(Long id) {
        var userEntity = getUserEntityById(id);
        var codeSecret = qrCodeSecret.get();
        userEntity.setQrCodeImageUri(qrCodeImageUri.apply(userEntity.getEmail(), codeSecret));
        userEntity.setQrCodeSecret(codeSecret);
        userEntity.setMfa(true);
        userRepository.save(userEntity);
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public User cancelMfa(Long id) {
        var userEntity = getUserEntityById(id);
        userEntity.setMfa(false);
        userEntity.setQrCodeSecret(EMPTY);
        userEntity.setQrCodeImageUri(EMPTY);
        userRepository.save(userEntity);
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public User verifyQrCode(String userId, String qrCode) {
        var userEntity = getUserEntityByUserId(userId);
        verifyCode(qrCode, userEntity.getQrCodeSecret());
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public void resetPassword(String email) {
        var user = getUserEntityByEmail(email);
        var confirmation = getUserConfirmation(user);

        if(confirmation != null) {
            publisher.publishEvent(new UserEvent(user, RESETPASSWORD, Map.of("key", confirmation.getToken())));
        }else {
            var confirmationEntity = new ConfirmationEntity(user);
            confirmationRepository.save(confirmationEntity);
            publisher.publishEvent(new UserEvent(user, RESETPASSWORD, Map.of("key", confirmationEntity.getToken())));
        }

    }

    @Override
    public User verifyPasswordKey(String key) {
        var confirmationEntity = getUserConfirmation(key);
        if(confirmationEntity == null) {
            throw new ApiException("Unable to find token");
        }    
        var userEntity = getUserEntityByEmail(confirmationEntity.getUserEntity().getEmail());
        if(userEntity == null) {
            throw new ApiException("Incorrect Token");
        }
        verifyAccountStatus(userEntity);
        confirmationRepository.delete(confirmationEntity);
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public void updatePassword(String userId, String newPassword, String confirmNewPassword) {
        if(!confirmNewPassword.equals(newPassword)){throw new ApiException("Password don't match. Please try again");}
        var user = getUserByUserId(userId);
        var credential = getUserCredentialById(user.getId());
        credential.setPassword(encoder.encode(newPassword));
        credentialRepository.save(credential);
    }

    @Override
    public void updatePassword(String userId, String currentPassword, String newPassword, String confirmNewPassword) {
        if(!confirmNewPassword.equals(newPassword)){throw new ApiException("Password don't match. Please try again");}
        var user = getUserEntityByUserId(userId);
        verifyAccountStatus(user);

        var credential = getUserCredentialById(user.getId());
        if(!encoder.matches(currentPassword, credential.getPassword())){throw new ApiException("Existing passwords is incorrect. Please try again");}
        credential.setPassword(encoder.encode(newPassword));
        credentialRepository.save(credential);
    }

    @Override
    public User updateUser(String userId, String firstName, String lastName, String email, String phone, String bio) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setFirstName(firstName);
        userEntity.setLastName(lastName);
        userEntity.setEmail(email);
        userEntity.setPhone(phone);
        userEntity.setBio(bio);

        userRepository.save(userEntity);

        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    @Override
    public void updateRole(String userId, String role) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setRole(getRoleName(role));
        userRepository.save(userEntity);

    }

    @Override
    public void toggleAccountExpired(String userId) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setAccountNonExpired(!userEntity.isAccountNonExpired());
        userRepository.save(userEntity);
    }

    @Override
    public void toggleAccountLocked(String userId) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setAccountNonLocked(!userEntity.isAccountNonLocked());
        var credential = getUserCredentialById(userEntity.getId());
        credential.setUpdatedAt(LocalDateTime.of(1996, 7, 12,11,11));

//        if(credential.getUpdatedAt().plusDays(NINETY_DAYS).isAfter(now())){
//            credential.setUpdatedAt(now());
//        }else {
//            credential.setUpdatedAt(LocalDateTime.of(1996, 7, 12,11,11));
//        }

        userRepository.save(userEntity);

    }

    @Override
    public void toggleAccountEnabled(String userId) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setEnabled(!userEntity.isEnabled());

        userRepository.save(userEntity);

    }

    @Override
    public void toggleCredentialExpired(String userId) {
        var userEntity = getUserEntityByUserId(userId);
        userEntity.setAccountNonLocked(!userEntity.isAccountNonLocked());

        userRepository.save(userEntity);

    }

    @Override
    public String uploadPhoto(String userId, MultipartFile file) {
        var userEntity = getUserEntityByUserId(userId);
        var photoUrl = photoFunction.apply(userId, file);
        userEntity.setImageUrl(photoUrl + "?timestamp=" + System.currentTimeMillis());
        userRepository.save(userEntity);
        return photoUrl;
    }

    @Override
    public User getUserById(Long id) {
        var userEntity = userRepository.findById(id).orElseThrow(()-> new ApiException("User not found"));
        return fromUserEntity(userEntity, userEntity.getRole(), getUserCredentialById(userEntity.getId()));
    }

    public  final BiFunction<String, MultipartFile, String> photoFunction =(userId, file)->{
        var filename = userId + ".png";

        try {
            var fileStorageLocation = Paths.get(FILE_STORAGE).toAbsolutePath().normalize();
            if(!Files.exists(fileStorageLocation)){Files.createDirectories(fileStorageLocation);}
            Files.copy(file.getInputStream(), fileStorageLocation.resolve(filename), REPLACE_EXISTING);
            return ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/user/image/" + filename).toUriString();
        }catch(Exception exception){
            throw new ApiException("unable to save image");
        }
    };



    private boolean verifyCode(String qrCode, String qrCodeSecret) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

        if(codeVerifier.isValidCode(qrCodeSecret, qrCode)){
            return true;
        }else {
            throw new ApiException("Invalid  QR Code. PLease try again.");
        }
    }

    public static BiFunction<String,String, QrData> qrDataFunction = (email, qrCodeSecret)-> new QrData.Builder()
            .issuer(MARC_DEV_LLC)
            .label(email)
            .secret(qrCodeSecret)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();

    public static Supplier<String> qrCodeSecret = () -> new DefaultSecretGenerator().generate();


    public static BiFunction<String, String, String> qrCodeImageUri = (email, qrCodeSecret) ->{
        var data = qrDataFunction.apply(email, qrCodeSecret);
        var generator = new ZxingPngQrGenerator();
        byte[] imageData;

        try{
            imageData = generator.generate(data);
        }catch(Exception exception){
            throw new ApiException("Unable to create Qr code URI");
        }
        return getDataUriForImage(imageData, generator.getImageMimeType());
    };

    private UserEntity getUserEntityByUserId(String userId) {
        var userByUserId = userRepository.findUserByUserId(userId);
        return userByUserId.orElseThrow(() -> new ApiException("User not found"));
    }

    private UserEntity getUserEntityById(Long id) {
        var userById = userRepository.findById(id);
        return userById.orElseThrow(() -> new ApiException("User not found"));
    }

    private UserEntity getUserEntityByEmail(String email) {
        var userByEmail = userRepository.findByEmailIgnoreCase(email);
        return userByEmail.orElseThrow(() -> new ApiException("User not found"));
    }

    private ConfirmationEntity getUserConfirmation(String key) {
        return confirmationRepository.findByToken(key).orElseThrow(() -> new ApiException("Confirmation key not found"));
    }
    private ConfirmationEntity getUserConfirmation(UserEntity user) {
        return confirmationRepository.findByUserEntity(user).orElse(null);
    }
    private UserEntity createNewUser(String firstName, String lastName, String email) {
        var role = getRoleName(Authority.USER.name());
        return createUserEntity(firstName, lastName, email, role);
    }
}