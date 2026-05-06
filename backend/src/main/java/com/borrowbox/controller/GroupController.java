package com.borrowbox.controller;

import com.borrowbox.dto.GroupCreateRequest;
import com.borrowbox.entity.Group;
import com.borrowbox.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<List<Group>> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroupById(@PathVariable Long id) {
        return ResponseEntity.ok(groupService.getGroupById(id));
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@Valid @RequestBody GroupCreateRequest request) {
        Group createdGroup = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Group> updateGroup(@PathVariable Long id, @Valid @RequestBody GroupCreateRequest request) {
        return ResponseEntity.ok(groupService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/users/{userId}")
    public ResponseEntity<Group> addUserToGroup(@PathVariable Long groupId, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.addUserToGroup(groupId, userId));
    }

    @DeleteMapping("/{groupId}/users/{userId}")
    public ResponseEntity<Group> removeUserFromGroup(@PathVariable Long groupId, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.removeUserFromGroup(groupId, userId));
    }
}
