package com.llm.nexusai_gateway.Team;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Join table between a User and a Team.
 * A user can belong to multiple teams (e.g., a TEAM_LEAD in one, TEAM_MEMBER in another).
 */
@Entity
@Table(name = "team_memberships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "user_id"}))
public class TeamMembership {

    @Id
    private String id;

    @Column(nullable = false, name = "team_id")
    private String teamId;

    @Column(nullable = false, name = "user_id")
    private String userId;

    /** Email cached for fast display — avoids join to users table. */
    @Column(name = "user_email")
    private String userEmail;

    /** Role within this team: TEAM_LEAD or TEAM_MEMBER */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false, name = "joined_at")
    private Instant joinedAt;

    public TeamMembership() {}

    public TeamMembership(String teamId, String userId, String userEmail, String role) {
        this.id = UUID.randomUUID().toString();
        this.teamId = teamId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public String getId()           { return id; }
    public String getTeamId()       { return teamId; }
    public String getUserId()       { return userId; }
    public String getUserEmail()    { return userEmail; }
    public String getRole()         { return role; }
    public void setRole(String r)   { this.role = r; }
    public Instant getJoinedAt()    { return joinedAt; }
}
