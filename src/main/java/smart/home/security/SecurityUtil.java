package smart.home.security;

import smart.home.entity.User;
import smart.home.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    private final UserRepository userRepository;
    private static UserRepository staticUserRepository;

    public SecurityUtil(UserRepository userRepository) {
        this.userRepository = userRepository;
        SecurityUtil.staticUserRepository = userRepository;
    }

    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            User cachedUser = (User) principal;
            return staticUserRepository.findById(cachedUser.getId())
                    .orElse(cachedUser);
        }

        return null;
    }
}