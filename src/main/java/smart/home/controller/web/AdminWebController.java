package smart.home.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import smart.home.entity.GarageDoorStateLog;
import smart.home.entity.GarageLog;
import smart.home.entity.PlugLog;
import smart.home.entity.User;
import smart.home.repository.GarageDoorStateLogRepository;
import smart.home.repository.GarageLogRepository;
import smart.home.repository.PlugLogRepository;
import smart.home.repository.UserRepository;
import smart.home.service.EmailService;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminWebController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final GarageLogRepository garageLogRepository;
    private final PlugLogRepository plugLogRepository;
    private final GarageDoorStateLogRepository garageDoorStateLogRepository;
    private final EmailService emailService;

    @GetMapping("/login")
    public String login() {
        return "admin-login";
    }

    @PostMapping("/login")
    public String handleLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            Authentication auth = authenticationManager.authenticate(authToken);

            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            User user = (User) auth.getPrincipal();
            if (user.getRole() == User.Role.SUPER_ADMIN) {
                String ipAddress = getClientIpAddress(request);
                emailService.sendSuperAdminLoginNotification(username, ipAddress);
            }

            return "redirect:/admin/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Invalid username or password");
            return "redirect:/admin/login";
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "UNKNOWN";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "Admin";
        model.addAttribute("username", username);

        List<User> allUsers = userRepository.findAll();
        User superAdmin = allUsers.stream()
                .filter(u -> u.getRole() == User.Role.SUPER_ADMIN)
                .findFirst()
                .orElse(null);
        List<User> regularUsers = allUsers.stream()
                .filter(u -> u.getRole() != User.Role.SUPER_ADMIN)
                .toList();

        model.addAttribute("superAdmin", superAdmin);
        model.addAttribute("regularUsers", regularUsers);

        List<GarageLog> garageLogs = garageLogRepository.findTop50ByOrderByTimestampDesc();
        model.addAttribute("garageLogs", garageLogs);

        List<PlugLog> plugLogs = plugLogRepository.findTop50ByOrderByTimestampDesc();
        model.addAttribute("plugLogs", plugLogs);

        List<GarageDoorStateLog> garageDoorStateLogs = garageDoorStateLogRepository.findTop50ByOrderByTimestampDesc();
        model.addAttribute("garageDoorStateLogs", garageDoorStateLogs);

        long totalUsers = regularUsers.size(); // Don't count super admin
        long totalGarageAccess = garageLogRepository.count();
        long totalPlugActivity = plugLogRepository.count();

        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        long garageAccess24h = garageLogRepository.countByTimestampAfter(last24Hours);
        long plugActivity24h = plugLogRepository.countByTimestampAfter(last24Hours);
        long garageDoorStateChanges24h = garageDoorStateLogRepository.countByTimestampAfter(last24Hours);

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalGarageAccess", totalGarageAccess);
        model.addAttribute("garageAccess24h", garageAccess24h);
        model.addAttribute("totalPlugActivity", totalPlugActivity);
        model.addAttribute("plugActivity24h", plugActivity24h);
        model.addAttribute("garageDoorStateChanges24h", garageDoorStateChanges24h);

        return "admin-dashboard";
    }

    @PostMapping("/logout")
    public String handleLogout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        redirectAttributes.addFlashAttribute("success", "You have been logged out successfully");
        return "redirect:/admin/login";
    }

    @PostMapping("/users/{id}/revoke")
    public String revokeUser(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getRole() == User.Role.SUPER_ADMIN) {
                redirectAttributes.addFlashAttribute("error",
                        "Cannot revoke Super Admin credentials");
                return "redirect:/admin/dashboard";
            }

            userRepository.delete(user);

            redirectAttributes.addFlashAttribute("success",
                    "Credentials revoked for user: " + user.getUsername());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to revoke credentials: " + e.getMessage());
        }

        return "redirect:/admin/dashboard";
    }
}