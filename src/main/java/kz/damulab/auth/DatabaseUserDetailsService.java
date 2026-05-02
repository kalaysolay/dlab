package kz.damulab.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public DatabaseUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        String[] roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .toArray(String[]::new);
        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(roles)
                .disabled(!user.isEnabled())
                .build();
    }
}
