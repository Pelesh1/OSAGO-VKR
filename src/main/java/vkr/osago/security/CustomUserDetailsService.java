package vkr.osago.security;

import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserEntity user = users.findByEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getStatus().name())
                .build();
    }
}