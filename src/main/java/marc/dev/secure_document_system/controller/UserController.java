package marc.dev.secure_document_system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import marc.dev.secure_document_system.domain.Response;
import marc.dev.secure_document_system.dto.User;
import marc.dev.secure_document_system.dtorequest.*;
import marc.dev.secure_document_system.enumeration.TokenType;
import marc.dev.secure_document_system.handle.ApiLogoutHandler;
import marc.dev.secure_document_system.service.JwtService;
import marc.dev.secure_document_system.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.concurrent.TimeUnit;
import static java.util.Collections.emptyMap;
import static java.util.Map.of;
import static marc.dev.secure_document_system.constant.Constants.FILE_STORAGE;
import static marc.dev.secure_document_system.utils.RequestUtils.getResponse;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/user" })
public class UserController {
    private final UserService userService;
    private final JwtService jwtService;
    private final ApiLogoutHandler apiLogoutHandler;

    @PostMapping("/register")
    public ResponseEntity<Response> saveUser(@RequestBody @Valid UserRequest user, HttpServletRequest request) {
        System.out.println("Entering savePatient method");

        System.out.println(user.getFirstName());
        System.out.println(user.getEmail());
        System.out.println(user.getPassword());
        userService.createUser(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPassword());
        return ResponseEntity.created(getUri()).body(getResponse(request, emptyMap(), "Account created. Check your email to enable your account.", CREATED));
    }

    @GetMapping("/verify/account")
    public ResponseEntity<Response> verifyAccount(@RequestParam("key") String key, HttpServletRequest request) throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
        userService.verifyAccount(key);
        return ResponseEntity.ok().body(getResponse(request, emptyMap(), "Account verified.", OK));
    }

    @GetMapping("/profile")
//    @PreAuthorize("hasAnyAuthority('user:read') or hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> profile(@AuthenticationPrincipal User userPrincipal, HttpServletRequest request) {
        System.out.println("User: " + userPrincipal);
        var user = userService.getUserByUserId(userPrincipal.getUserId());
        return ResponseEntity.ok().body(getResponse(request,  of("user", user), "Profile retrieve.", OK));
    }

    @PatchMapping("/update")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> profile(@AuthenticationPrincipal User userPrincipal, @RequestBody UserRequest userRequest, HttpServletRequest request) {
        var user = userService.updateUser(userPrincipal.getUserId(), userRequest.getFirstName(), userRequest.getLastName(), userRequest.getEmail(), userRequest.getPhone(),userRequest.getBio());
        return ResponseEntity.ok().body(getResponse(request,  of("user", user), "Profile updated successfully", OK));
    }


    @PatchMapping("/updaterole")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> updateRole(@AuthenticationPrincipal User userPrincipal, @RequestBody RoleRequest roleRequest, HttpServletRequest request) {
        userService.updateRole(userPrincipal.getUserId(), roleRequest.getRole());
        return ResponseEntity.ok().body(getResponse(request,   emptyMap(), "Account updated successfully", OK));
    }

    @PatchMapping("/toggle_account_expired")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> toggleAccountExpired(@AuthenticationPrincipal User user, HttpServletRequest request) {
        userService.toggleAccountExpired(user.getUserId());
        return ResponseEntity.ok().body(getResponse(request,   emptyMap(), "Account updated successfully", OK));
    }

    @PatchMapping("/toggle_account_locked")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> toggleAccountLocked(@AuthenticationPrincipal User user, HttpServletRequest request) {
        userService.toggleAccountLocked(user.getUserId());
        return ResponseEntity.ok().body(getResponse(request,   emptyMap(), "Account updated successfully", OK));
    }

    @PatchMapping("/toggle_account_enabled")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> toggleAccountEnabled(@AuthenticationPrincipal User user,HttpServletRequest request) {
        userService.toggleAccountEnabled(user.getUserId());
        return ResponseEntity.ok().body(getResponse(request,   emptyMap(), "Account updated successfully", OK));
    }

    @PatchMapping("/toggle_credential_expired")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> toggleCredentialExpired(@AuthenticationPrincipal User user, HttpServletRequest request) {
        userService.toggleCredentialExpired(user.getUserId());
        return ResponseEntity.ok().body(getResponse(request,   emptyMap(), "Account updated successfully", OK));
    }
    @PatchMapping("/mfa/setup")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'USER','ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> setUpMfa(@AuthenticationPrincipal User userPrincipal, HttpServletRequest request) {
        var user = userService.setUpMfa(userPrincipal.getId());
        return ResponseEntity.ok().body(getResponse(request, of("user", user), "MFA set up successfully", OK));
    }

    @PatchMapping("/mfa/cancel")
    public ResponseEntity<Response> cancelMfa(@AuthenticationPrincipal User userPrincipal, HttpServletRequest request) {
        var user = userService.cancelMfa(userPrincipal.getId());
        return ResponseEntity.ok().body(getResponse(request, of("user", user), "MFA canceled successfully", OK));
    }

    @PostMapping("/verify/qrcode")
    public ResponseEntity<Response> verifyQrCode(@RequestBody QrCodeRequest qrCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        var user = userService.verifyQrCode(qrCodeRequest.getUserId(), qrCodeRequest.getQrCode());
        jwtService.addCookie(response, user, TokenType.ACCESS);
        jwtService.addCookie(response, user, TokenType.REFRESH);
        return ResponseEntity.ok().body(getResponse(request, of("user", user), "Qr code verified", OK));
    }

    // Start - reset password when user is logged in

    @PatchMapping("/update_password")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> updatePassword(@AuthenticationPrincipal User userPrincipal, @RequestBody UpdatePasswordRequest passwordRequest, HttpServletRequest request) {
         userService.updatePassword(userPrincipal.getUserId(),passwordRequest.getPassword(), passwordRequest.getNewPassword(), passwordRequest.getConfirmNewPassword() );
        return ResponseEntity.ok().body(getResponse(request, emptyMap(), "Password updated successfully", OK));
    }
    // End - reset password when user is not logged in



// Start - reset password when user is NOT logged in
    @PostMapping("/resetpassword")
    public ResponseEntity<Response> resetPassword(@RequestBody @Valid EmailRequest emailRequest, HttpServletRequest request) {
        userService.resetPassword(emailRequest.getEmail());

        return ResponseEntity.ok().body(getResponse(request, emptyMap(), "We sent you an email to reset your Password. ", OK));
    }

    @GetMapping("/verify/password")
    public ResponseEntity<Response> verifyPassword(@RequestParam("key") String key, HttpServletRequest request) {
       var user = userService.verifyPasswordKey(key);

        return ResponseEntity.ok().body(getResponse(request, of("user", user), "Enter new Password. ", OK));
    }

    @PostMapping("/resetpassword/reset")
    public ResponseEntity<Response> doResetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest, HttpServletRequest request) {
        userService.updatePassword(resetPasswordRequest.getUserId(), resetPasswordRequest.getNewPassword(), resetPasswordRequest.getConfirmNewPassword());

        return ResponseEntity.ok().body(getResponse(request, emptyMap(), "Password reset successfully ", OK));
    }
    // End - reset password when user is not logged in


    @PatchMapping("/photo")
    @PreAuthorize("hasAnyAuthority('user:update') or hasAnyRole('USER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> uploadPhoto(@AuthenticationPrincipal User userPrincipal, @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        var imageUrl = userService.uploadPhoto(userPrincipal.getUserId(), file);
        return ResponseEntity.ok().body(getResponse(request,of("imageUrl",imageUrl), "Photo updated successfully", OK));
    }
    @GetMapping(path = "/image/{filename}", produces = {IMAGE_PNG_VALUE, IMAGE_JPEG_VALUE})
    public byte[] getImage(@PathVariable String filename) throws IOException {
        return Files.readAllBytes(Paths.get(FILE_STORAGE + filename));
    }

    @PostMapping("/logout")
    public ResponseEntity<Response> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication){
        apiLogoutHandler.logout(request, response, authentication);

        return ResponseEntity.ok().body(getResponse(request, emptyMap(), "You've logged out successfully ", OK));
    }


    protected URI getUri() {
        return URI.create("");
    }
}
