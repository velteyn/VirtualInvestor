package com.virtualinvestor.edge.api;

import com.virtualinvestor.edge.game.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gates the backyard (player-accounts 3.1): every {@code /api/admin/*} call except
 * the login itself must carry a valid admin token in {@code X-Admin-Token}.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminService admins;

    public AdminAuthInterceptor(AdminService admins) {
        this.admins = admins;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (admins.isAdmin(request.getHeader(ADMIN_TOKEN_HEADER))) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return false;
    }
}
