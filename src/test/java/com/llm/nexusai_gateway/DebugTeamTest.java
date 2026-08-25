package com.llm.nexusai_gateway;

import com.llm.nexusai_gateway.Controller.TeamController;
import com.llm.nexusai_gateway.Team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DebugTeamTest {

    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private TeamController teamController;
    
    @Autowired
    private com.llm.nexusai_gateway.Security.JwtUtil jwtUtil;

    @Test
    public void testGetTeam() {
        String teamId = "42f83e5c-5416-4e28-b201-b3c2e80a021b";
        String orgId = "e4a20e95-1981-4ac1-b26f-01954317c539";
        
        System.out.println("TEAM EXISTS IN REPO? " + teamRepository.findByIdAndOrganizationId(teamId, orgId).isPresent());
        
        String token = jwtUtil.generateToken("google@gmail.com", "google-b39b3ec2", "ORG_ADMIN");
        String auth = "Bearer " + token;
        
        System.out.println("TESTING CONTROLLER GET TEAM");
        try {
            Object result = teamController.getTeam(teamId, auth);
            System.out.println("RESULT: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
