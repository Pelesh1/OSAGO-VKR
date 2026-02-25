package vkr.osago.auth;


import vkr.osago.user.UserStatus;
import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody @Valid RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        UserEntity u = new UserEntity();
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setMiddleName(req.middleName());

        u.setStatus(UserStatus.CLIENT);
        u.setSelfRegistered(true);

        users.save(u);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        UserEntity user = users.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
    }

    public record ResetPasswordRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 128) String newPassword,
            @NotBlank @Size(min = 6, max = 128) String confirmPassword
    ) {}
}
