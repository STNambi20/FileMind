package com.filemind.workspace.entity;

import com.filemind.collaboration.entity.DocumentPermission;
import com.filemind.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter @NoArgsConstructor
public class FolderAccess {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private WorkspaceFolder folder;
    @ManyToOne(optional = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentPermission permission;
    @Column(nullable = false) private Instant createdAt;
    @PrePersist void created() { createdAt = Instant.now(); }
}
