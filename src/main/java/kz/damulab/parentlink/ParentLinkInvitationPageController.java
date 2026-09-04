package kz.damulab.parentlink;

import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Убирает секрет из адресной строки после первого GET и держит его только в серверной сессии.
 * GET ничего не подтверждает: связь создаётся исключительно защищённым CSRF POST ученика.
 */
@Controller
public class ParentLinkInvitationPageController {

    public static final String SESSION_TOKEN_ATTRIBUTE = "parentLinkInvitationToken";
    private static final String CONFIRM_PATH = "/parent-link-invitations/confirm";

    private final ParentLinkInvitationService invitationService;

    public ParentLinkInvitationPageController(ParentLinkInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping(CONFIRM_PATH)
    String invitation(
            @RequestParam(value = "token", required = false) String token,
            Principal principal,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        noSecretCaching(response);
        if (token != null) {
            request.getSession(true).setAttribute(SESSION_TOKEN_ATTRIBUTE, token);
            return "redirect:" + CONFIRM_PATH;
        }
        HttpSession session = request.getSession(false);
        String sessionToken = session == null ? null : (String) session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        ParentLinkInvitationViewState state = invitationService.inspect(
                sessionToken,
                principal == null ? null : principal.getName()
        );
        model.addAttribute("state", state);
        return "parentlink/confirm-invitation";
    }

    @PostMapping("/student/parent-link-invitations/confirm")
    String confirm(
            Principal principal,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        HttpSession session = request.getSession(false);
        String token = session == null ? null : (String) session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        try {
            invitationService.confirm(principal.getName(), token);
            if (session != null) {
                session.removeAttribute(SESSION_TOKEN_ATTRIBUTE);
            }
            redirectAttributes.addAttribute("confirmed", "true");
        } catch (ParentLinkException ex) {
            redirectAttributes.addAttribute(
                    "invitation_student_mismatch".equals(ex.getMessage()) ? "wrongAccount" : "invalid",
                    "true"
            );
        }
        return "redirect:" + CONFIRM_PATH;
    }

    private void noSecretCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
