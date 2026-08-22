package com.filemind.collaboration.entity;

public enum DocumentPermission {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canManageAccess() {
        return this == OWNER;
    }
}
