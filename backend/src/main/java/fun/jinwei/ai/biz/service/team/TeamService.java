package fun.jinwei.ai.biz.service.team;

import fun.jinwei.ai.biz.config.DataContextHolder;
import fun.jinwei.ai.biz.entity.*;
import fun.jinwei.ai.biz.repository.*;
import fun.jinwei.ai.biz.security.TeamAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final TeamAccess teamAccess;

    public List<Team> listUserTeams(Long userId) {
        List<Team> owned = teamRepository.findByOwnerId(userId);
        List<TeamMember> memberships = teamMemberRepository.findByUserId(userId);
        List<Team> memberTeams = memberships.stream()
                .map(TeamMember::getTeam)
                .filter(t -> !owned.contains(t))
                .toList();
        owned.addAll(memberTeams);
        return owned;
    }

    /** 团队详情：仅团队成员或管理员 */
    public Team getTeam(Long teamId, Long userId, boolean admin) {
        Team team = requireTeam(teamId);
        if (!admin && !teamAccess.isMember(teamId, userId)) {
            throw new AccessDeniedException("你不是该团队成员");
        }
        return team;
    }

    public Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("团队不存在"));
    }

    @Transactional
    public Team createTeam(Long userId, String name, String description) {
        User user = userRepository.getReferenceById(userId);
        Team team = Team.builder().name(name).description(description).owner(user).build();
        team = teamRepository.save(team);
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(user).role("owner").build());
        log.info("团队创建成功: {} (owner={})", name, userId);
        return team;
    }

    @Transactional
    public Team updateTeam(Long teamId, Long userId, boolean admin, String name, String description) {
        checkManager(teamId, userId, admin);
        Team team = requireTeam(teamId);
        if (name != null) team.setName(name);
        if (description != null) team.setDescription(description);
        return teamRepository.save(team);
    }

    @Transactional
    public void deleteTeam(Long teamId, Long userId, boolean admin) {
        checkOwner(teamId, userId, admin);
        teamRepository.deleteById(teamId);
        log.info("团队已删除: {}", teamId);
    }

    /** 成员列表：仅团队成员或管理员 */
    public List<TeamMember> listMembers(Long teamId, Long userId, boolean admin) {
        if (!admin && !teamAccess.isMember(teamId, userId)) {
            throw new AccessDeniedException("你不是该团队成员");
        }
        return teamMemberRepository.findByTeamId(teamId);
    }

    @Transactional
    public TeamMember addMember(Long teamId, Long userId, boolean admin, Long memberUserId, String role) {
        checkManager(teamId, userId, admin);
        if (memberUserId == null) {
            throw new IllegalArgumentException("缺少成员用户 ID");
        }
        User member = userRepository.getReferenceById(memberUserId);
        Team team = requireTeam(teamId);
        TeamMember tm = TeamMember.builder().team(team).user(member)
                .role(role != null ? role : "member").build();
        return teamMemberRepository.save(tm);
    }

    @Transactional
    public void removeMember(Long teamId, Long userId, boolean admin, Long memberUserId) {
        checkManager(teamId, userId, admin);
        Team team = requireTeam(teamId);
        if (team.getOwner().getId().equals(memberUserId)) {
            throw new IllegalArgumentException("不能移除团队负责人");
        }
        teamMemberRepository.deleteByTeamIdAndUserId(teamId, memberUserId);
    }

    @Transactional
    public TeamMember updateMemberRole(Long teamId, Long userId, boolean admin,
                                       Long memberUserId, String role) {
        checkManager(teamId, userId, admin);
        Team team = requireTeam(teamId);
        if (team.getOwner().getId().equals(memberUserId)) {
            throw new IllegalArgumentException("不能修改团队负责人的角色");
        }
        TeamMember tm = teamMemberRepository.findByTeamIdAndUserId(teamId, memberUserId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在"));
        tm.setRole(role);
        return teamMemberRepository.save(tm);
    }

    /** 仅团队负责人或系统管理员 */
    private void checkOwner(Long teamId, Long userId, boolean admin) {
        Team team = requireTeam(teamId);
        if (!admin && !team.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("仅团队负责人可执行此操作");
        }
    }

    /** 团队负责人 / 团队管理员 / 系统管理员 */
    private void checkManager(Long teamId, Long userId, boolean admin) {
        requireTeam(teamId);
        if (admin) {
            return;
        }
        if (teamAccess.isTeamManager(teamId, userId)) {
            return;
        }
        throw new AccessDeniedException("仅团队负责人或团队管理员可执行此操作");
    }
}
