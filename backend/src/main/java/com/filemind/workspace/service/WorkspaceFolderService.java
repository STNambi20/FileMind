package com.filemind.workspace.service;
import com.filemind.collaboration.entity.DocumentPermission;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.exception.ForbiddenOperationException;
import com.filemind.common.exception.ResourceNotFoundException;
import com.filemind.user.entity.User;
import com.filemind.user.repository.UserRepository;
import com.filemind.workspace.entity.*;
import com.filemind.workspace.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor public class WorkspaceFolderService {
 private final FolderRepository folders; private final FolderAccessRepository accesses; private final UserRepository users;
 public List<Map<String,Object>> list(User user) { return folders.findAll().stream().filter(f -> f.getOwner().getId().equals(user.getId()) || f.isPublicFolder() || accesses.findAll().stream().anyMatch(a -> a.getFolder().getId().equals(f.getId()) && a.getUser().getId().equals(user.getId()))).map(f -> view(f,user)).toList(); }
 public Map<String,Object> create(User user,String name,boolean isPublic) { if(name==null||name.isBlank()) throw new BadRequestException("Folder name is required"); WorkspaceFolder f=new WorkspaceFolder(); f.setName(name.trim()); f.setOwner(user); f.setPublicFolder(isPublic); return view(folders.save(f),user); }
 public Map<String,Object> setPublic(User user,String id,boolean isPublic) { WorkspaceFolder f=owned(user,id); f.setPublicFolder(isPublic); return view(folders.save(f),user); }
 public Map<String,Object> share(User actor,String id,String email,String raw) { WorkspaceFolder f=owned(actor,id); User user=users.findByEmailIgnoreCase(email.trim()).orElseThrow(()->new BadRequestException("No registered user exists with that email address")); DocumentPermission p=parse(raw); if(p==DocumentPermission.OWNER) throw new BadRequestException("Use EDITOR or VIEWER for collaborators"); FolderAccess access=accesses.findAll().stream().filter(a->a.getFolder().getId().equals(id)&&a.getUser().getId().equals(user.getId())).findFirst().orElseGet(FolderAccess::new); access.setFolder(f);access.setUser(user);access.setPermission(p);accesses.save(access);return view(f,actor); }
 public void remove(User actor,String folderId,String accessId) { owned(actor,folderId); FolderAccess a=accesses.findById(accessId).filter(x->x.getFolder().getId().equals(folderId)).orElseThrow(()->new ResourceNotFoundException("Collaborator not found"));accesses.delete(a); }
 private WorkspaceFolder owned(User u,String id){WorkspaceFolder f=folders.findById(id).orElseThrow(()->new ResourceNotFoundException("Folder not found"));if(!f.getOwner().getId().equals(u.getId()))throw new ForbiddenOperationException("Only the folder owner can manage access");return f;}
 private DocumentPermission parse(String raw){try{return DocumentPermission.valueOf(raw.trim().toUpperCase());}catch(Exception e){throw new BadRequestException("Permission must be EDITOR or VIEWER");}}
 private Map<String,Object> view(WorkspaceFolder f,User u){var members=accesses.findAll().stream().filter(a->a.getFolder().getId().equals(f.getId())).map(a->Map.<String,Object>of("id",a.getId(),"email",a.getUser().getEmail(),"fullName",a.getUser().getFullName(),"permission",a.getPermission().name())).toList();return Map.of("id",f.getId(),"name",f.getName(),"public",f.isPublicFolder(),"ownerEmail",f.getOwner().getEmail(),"canManage",f.getOwner().getId().equals(u.getId()),"members",members);}
}
