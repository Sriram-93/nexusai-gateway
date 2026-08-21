package com.llm.nexusai_gateway.Security;

import com.llm.nexusai_gateway.Tenant.TenantConfig;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Transactional
public class AuthController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRegistry tenantRegistry;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.llm.nexusai_gateway.Provider.ProviderBootstrapService providerBootstrapService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          OrganizationRepository organizationRepository, TenantRegistry tenantRegistry,
                          JwtUtil jwtUtil, com.llm.nexusai_gateway.Provider.ProviderBootstrapService providerBootstrapService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationRepository = organizationRepository;
        this.tenantRegistry = tenantRegistry;
        this.jwtUtil = jwtUtil;
        this.providerBootstrapService = providerBootstrapService;
    }

    public record SignupRequest(String tier, String organizationName, String email, String password) {}

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
        }

        // 1. Create Organization (SOLO or ADMINISTRATION)
        String orgName = request.tier().equalsIgnoreCase("SOLO") ? "Personal Workspace" : request.organizationName();
        Organization org = new Organization(orgName, request.tier().toUpperCase());
        organizationRepository.save(org);

        // 2. Create User (Owner of the Organization)
        // SOLO tier = role SOLO, ADMINISTRATION tier = role ORG_ADMIN
        String userRole = request.tier().equalsIgnoreCase("SOLO") ? "SOLO" : "ORG_ADMIN";
        User user = new User(
            request.email(),
            passwordEncoder.encode(request.password()),
            userRole,
            org
        );
        userRepository.save(user);

        // 3. Create Default Tenant Workspace for this Organization
        String tenantId = (request.tier().equalsIgnoreCase("SOLO") ? "user" : orgName.toLowerCase().replace(" ", "-")) 
                           + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        TenantConfig config = new TenantConfig();
        // Setup initial default values
        TenantConfig newTenant = new TenantConfig(
            tenantId,
            orgName + " Default",
            org.getId(),
            request.tier().equalsIgnoreCase("SOLO") ? 10.00 : 100.00,
            null, null, null,
            request.tier().equalsIgnoreCase("SOLO") ? 60 : 500,
            true, true, null
        );
        
        // NO API KEY GENERATED YET. STRICT REQUIREMENT: API key is generated later.
        
        tenantRegistry.register(newTenant);
        
        providerBootstrapService.seedProvidersForTenant(tenantId);

        // 4. Generate JWT Session Token
        String token = jwtUtil.generateToken(user.getEmail(), tenantId, user.getRole());

        return ResponseEntity.ok(Map.of(
            "message", "Signup successful",
            "token", token,
            "tenantId", tenantId,
            "role", user.getRole(),
            "orgName", orgName,
            "tier", request.tier().toUpperCase()
        ));
    }

    public record LoginRequest(String email, String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Optional<User> optUser = userRepository.findByEmail(request.email());
        if (optUser.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        User user = optUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        Organization org = user.getOrganization();
        if (org == null) {
            return ResponseEntity.status(500).body(Map.of("error", "User organization not found"));
        }

        Optional<TenantConfig> optTenant = tenantRegistry.getByOrganizationId(org.getId());
        if (optTenant.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of("error", "Tenant workspace not found for organization"));
        }

        String tenantId = optTenant.get().getTenantId();
        String token = jwtUtil.generateToken(user.getEmail(), tenantId, user.getRole());

        return ResponseEntity.ok(Map.of(
            "message", "Login successful",
            "token", token,
            "tenantId", tenantId,
            "role", user.getRole(),
            "orgName", org.getName(),
            "tier", org.getType(),
            "userId", user.getId()
        ));
    }
}
