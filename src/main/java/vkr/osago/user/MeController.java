package vkr.osago.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UserDetails principal) {
        var u = users.findByEmail(principal.getUsername())
                .orElseThrow();

        String fio = u.getLastName() + " " + u.getFirstName() +
                (u.getMiddleName() != null && !u.getMiddleName().isBlank()
                        ? " " + u.getMiddleName()
                        : "");

        String shortFio = u.getLastName() + " " +
                u.getFirstName().charAt(0) + "." +
                (u.getMiddleName() != null && !u.getMiddleName().isBlank()
                        ? u.getMiddleName().charAt(0) + "."
                        : "");

        return new MeResponse(u.getId(), u.getEmail(), fio, shortFio, u.getStatus().name());
    }

    public record MeResponse(Long id, String email, String fio, String shortFio, String status) {}
}
