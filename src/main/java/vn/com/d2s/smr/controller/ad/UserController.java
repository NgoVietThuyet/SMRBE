package vn.com.d2s.smr.controller.ad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.ad.user.UserSearchResponse;
import vn.com.d2s.smr.service.ad.UserService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/User")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/Search")
    public List<UserSearchResponse> search(
            Principal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int take
    ) {
        return userService.search(principal.getName(), q, take);
    }
}
