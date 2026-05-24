package com.example.forklift_erp.entity;

public interface CollaborativeResource {
    Long getId();

    Long getVersion();

    String getLastModifiedBy();

    void setLastModifiedBy(String lastModifiedBy);

    String getLastModifiedRole();

    void setLastModifiedRole(String lastModifiedRole);

    Integer getLastModifiedPriority();

    void setLastModifiedPriority(Integer lastModifiedPriority);
}
