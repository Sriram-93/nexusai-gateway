package com.llm.nexusai_gateway.Security;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // "SOLO" or "ADMINISTRATION"

    public Organization() {}

    public Organization(String name, String type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
