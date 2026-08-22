package com.filemind.settings.entity;

import com.filemind.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SummaryPreference summaryPreference = SummaryPreference.DETAILED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RewritePreference rewritePreference = RewritePreference.PROFESSIONAL;

    @Column(nullable = false)
    private boolean autoGenerateTagsOnUpload = true;

    @Column(nullable = false)
    private boolean openLatestVersionAutomatically = false;

    @Column(nullable = false)
    private boolean wrapTextForTextFiles = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DefaultOpenMode defaultOpenMode = DefaultOpenMode.PREVIEW;

    @Column(nullable = false)
    private boolean autoRetryFailedProcessing = false;

    @Column(nullable = false)
    private boolean keepFailedUploadsVisible = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UploadCompletionBehavior uploadCompletionBehavior = UploadCompletionBehavior.STAY_IN_LIST;

    @Column(nullable = false)
    private boolean notifyUploadCompleted = true;

    @Column(nullable = false)
    private boolean notifyProcessingFailed = true;

    @Column(nullable = false)
    private boolean notifyVersionCreated = true;

    @Column
    private Integer defaultShareExpiryDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShareVisibility defaultShareVisibility = ShareVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LayoutDensity layoutDensity = LayoutDensity.COMFORTABLE;

    @Column(nullable = false)
    private boolean reducedMotion = false;

    @Column(nullable = false)
    private boolean highContrastMode = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
