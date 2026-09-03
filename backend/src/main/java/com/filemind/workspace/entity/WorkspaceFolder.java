package com.filemind.workspace.entity;

import com.filemind.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class WorkspaceFolder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private String name;
    @ManyToOne(optional = false) private User owner;
    @Column(nullable = false) private boolean publicFolder;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
