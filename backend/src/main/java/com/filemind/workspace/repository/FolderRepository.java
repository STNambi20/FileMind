package com.filemind.workspace.repository;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import com.filemind.workspace.entity.WorkspaceFolder;
import org.springframework.stereotype.Repository;
@Repository public class FolderRepository extends DynamoRepository<WorkspaceFolder> {
 public FolderRepository(DynamoMetadataStore store) { super(store, WorkspaceFolder.class); }
 @Override protected String id(WorkspaceFolder value) { return value.getId(); }
}
