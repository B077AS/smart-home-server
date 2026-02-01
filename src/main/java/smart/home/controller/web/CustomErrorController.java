package smart.home.controller.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

@Controller
public class CustomErrorController implements ErrorController {

    @Value("${error.redirect}")
    private String redirectUrl;

    @RequestMapping("/error")
    public void handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == 404) {
                response.sendRedirect(redirectUrl);
                return;
            }

            if (statusCode == 403) {
                String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
                if (requestUri != null && requestUri.startsWith("/admin")) {
                    response.sendRedirect(redirectUrl);
                    return;
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Something went wrong\"}");
    }
}