package com.filemind.workspace.repository;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import com.filemind.workspace.entity.FolderAccess;
import org.springframework.stereotype.Repository;
@Repository public class FolderAccessRepository extends DynamoRepository<FolderAccess> {
 public FolderAccessRepository(DynamoMetadataStore store) { super(store, FolderAccess.class); }
 @Override protected String id(FolderAccess value) { return value.getId(); }
}
