package fun.jinwei.ai.biz.controller;

import fun.jinwei.ai.biz.dto.request.TeamMemberRequest;
import fun.jinwei.ai.biz.dto.request.TeamRequest;
import fun.jinwei.ai.biz.dto.response.ApiResponse;
import fun.jinwei.ai.biz.entity.Team;
import fun.jinwei.ai.biz.entity.TeamMember;
import fun.jinwei.ai.biz.security.AccessGuard;
import fun.jinwei.ai.biz.service.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队接口。
 *
 * <p>查看团队与成员需要成员身份；改团队、管成员需要团队负责人/团队管理员身份。</p>
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /** 我参与或负责的团队 */
    @GetMapping
    public ApiResponse<List<Team>> listTeams(Authentication auth) {
        return ApiResponse.success(teamService.listUserTeams(getUserId(auth)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Team> getTeam(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(teamService.getTeam(id, getUserId(auth), isAdmin(auth)));
    }

    @PostMapping
    public ApiResponse<Team> createTeam(Authentication auth, @RequestBody TeamRequest req) {
        return ApiResponse.success(teamService.createTeam(getUserId(auth), req.getName(), req.getDescription()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Team> updateTeam(@PathVariable Long id, Authentication auth,
                                        @RequestBody TeamRequest req) {
        return ApiResponse.success(teamService.updateTeam(id, getUserId(auth), isAdmin(auth),
                req.getName(), req.getDescription()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTeam(@PathVariable Long id, Authentication auth) {
        teamService.deleteTeam(id, getUserId(auth), isAdmin(auth));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMember>> listMembers(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(teamService.listMembers(id, getUserId(auth), isAdmin(auth)));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<TeamMember> addMember(@PathVariable Long id, Authentication auth,
                                             @RequestBody TeamMemberRequest req) {
        return ApiResponse.success(teamService.addMember(id, getUserId(auth), isAdmin(auth),
                req.getUserId(), req.getRole()));
    }

    @PutMapping("/{id}/members/{memberUserId}")
    public ApiResponse<TeamMember> updateMemberRole(@PathVariable Long id, @PathVariable Long memberUserId,
                                                    Authentication auth, @RequestBody TeamMemberRequest req) {
        return ApiResponse.success(teamService.updateMemberRole(id, getUserId(auth), isAdmin(auth),
                memberUserId, req.getRole()));
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long memberUserId,
                                          Authentication auth) {
        teamService.removeMember(id, getUserId(auth), isAdmin(auth), memberUserId);
        return ApiResponse.success();
    }

    private Long getUserId(Authentication auth) {
        return AccessGuard.userId(auth);
    }

    private boolean isAdmin(Authentication auth) {
        return AccessGuard.isAdmin(auth);
    }
}
