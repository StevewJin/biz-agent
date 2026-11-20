package fun.jinwei.ai.biz.dto.request;

import lombok.Data;

@Data
public class TeamMemberRequest {
    private Long userId;
    private String role;
}
