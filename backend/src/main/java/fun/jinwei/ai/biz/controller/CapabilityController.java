package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.entity.Team;
import fun.jinwei.ai.biz.entity.TeamMember;
import fun.jinwei.ai.biz.entity.User;
import fun.jinwei.ai.biz.repository.TeamMemberRepository;
import fun.jinwei.ai.biz.repository.UserRepository;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.security.TeamAccess;
import fun.jinwei.ai.biz.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前账号的能力清单：前端据此收敛菜单、路由与操作按钮。
 *
 * <p>注意：这里只做展示层裁剪，真正的越权拦截始终由各业务接口完成。</p>
 */
@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityController {

    private final TeamService teamService;
    private final TeamAccess teamAccess;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ApiResponse<Map<String, Object>> capabilities(Authentication auth) {
        Long userId = AccessGuard.userId(auth);
        boolean admin = AccessGuard.isAdmin(auth);

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        List<Team> teams = userId != null ? teamService.listUserTeams(userId) : List.of();
        List<Long> managedTeamIds = userId != null ? teamAccess.managedTeamIds(userId) : List.of();

        Map<Long, String> roleByTeam = new LinkedHashMap<>();
        if (userId != null) {
            for (TeamMember member : teamMemberRepository.findByUserId(userId)) {
                roleByTeam.put(member.getTeam().getId(), member.getRole());
            }
        }

        List<Map<String, Object>> teamViews = new ArrayList<>();
        for (Team team : teams) {
            Long ownerId = team.getOwner() != null ? team.getOwner().getId() : null;
            String role = userId != null && userId.equals(ownerId)
                    ? "owner"
                    : roleByTeam.getOrDefault(team.getId(), "member");
            teamViews.add(Map.of(
                    "id", team.getId(),
                    "name", team.getName(),
                    "role", role,
                    "manageable", admin || managedTeamIds.contains(team.getId())));
        }

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("manageProviders", admin);
        permissions.put("manageExtensions", admin);
        permissions.put("manageBuiltinSkills", admin);
        permissions.put("viewAllConversations", admin);
        permissions.put("manageTeams", admin || !managedTeamIds.isEmpty());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("username", user != null ? user.getUsername() : null);
        result.put("admin", admin);
        result.put("managedTeamIds", managedTeamIds);
        result.put("teams", teamViews);
        result.put("permissions", permissions);
        return ApiResponse.success(result);
    }
}
