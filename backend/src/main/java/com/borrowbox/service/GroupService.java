package com.borrowbox.service;

import com.borrowbox.dto.GroupCreateRequest;
import com.borrowbox.entity.Group;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.GroupRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public Group createGroup(GroupCreateRequest request) {
        Group group = new Group(request.name(), request.description());
        assignMembers(group, request.memberIds());
        return groupRepository.save(group);
    }

    public Group getGroupById(Long id) {
        return groupRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));
    }

    public Group updateGroup(Long id, GroupCreateRequest request) {
        Group existingGroup = groupRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));

        existingGroup.setName(request.name());
        existingGroup.setDescription(request.description());
        replaceMembers(existingGroup, request.memberIds());

        return groupRepository.save(existingGroup);
    }

    public void deleteGroup(Long id) {
        Group existingGroup = groupRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));
        existingGroup.getUsers().forEach(user -> user.getGroups().remove(existingGroup));
        groupRepository.delete(existingGroup);
    }

    public Group addUserToGroup(Long groupId, Long userId) {
        Group group = getGroupById(groupId);
        User user = getUserById(userId);
        group.addUser(user);
        return groupRepository.save(group);
    }

    public Group removeUserFromGroup(Long groupId, Long userId) {
        Group group = getGroupById(groupId);
        User user = getUserById(userId);
        group.removeUser(user);
        return groupRepository.save(group);
    }

    private void assignMembers(Group group, Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }

        Set<User> users = resolveUsers(memberIds);
        users.forEach(group::addUser);
    }

    private void replaceMembers(Group group, Set<Long> memberIds) {
        group.getUsers().forEach(user -> user.getGroups().remove(group));
        group.getUsers().clear();
        assignMembers(group, memberIds);
    }

    private Set<User> resolveUsers(Set<Long> userIds) {
        Set<User> users = new HashSet<>(userRepository.findAllById(Objects.requireNonNull(userIds)));
        if (users.size() != userIds.size()) {
            throw new ResourceNotFoundException("One or more users not found for the group request");
        }
        return users;
    }

    private User getUserById(Long userId) {
        return userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
