package com.football.ua.service;

import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.model.entity.UserEntity;
import com.football.ua.repo.PostRepository;
import com.football.ua.repo.TopicRepository;
import com.football.ua.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthorizationService {

    private final TopicRepository topicRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public AuthorizationService(TopicRepository topicRepository, 
                               PostRepository postRepository,
                               UserRepository userRepository) {
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    public boolean hasModeratorOrEditorRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_MODERATOR")) ||
               authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EDITOR"));
    }

    public boolean isModeratorOrHigher(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_MODERATOR"));
    }

    public boolean isEditor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EDITOR"));
    }

    public boolean isTopicOwner(Long topicId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Optional<TopicEntity> topic = topicRepository.findById(topicId);
        return topic.isPresent() && topic.get().getAuthor().equals(authentication.getName());
    }

    public boolean isPostOwner(Long postId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Optional<PostEntity> post = postRepository.findById(postId);
        return post.isPresent() && post.get().getAuthor().equals(authentication.getName());
    }

    public boolean canDeleteTopic(Long topicId, Authentication authentication) {
        return isTopicOwner(topicId, authentication) || isModeratorOrHigher(authentication);
    }

    public boolean canDeletePost(Long postId, Authentication authentication) {
        return isPostOwner(postId, authentication) || isModeratorOrHigher(authentication);
    }

    public boolean canEditPost(Long postId, Authentication authentication) {
        return isPostOwner(postId, authentication);
    }

    public boolean canEditTopic(Long topicId, Authentication authentication) {
        return isTopicOwner(topicId, authentication);
    }

    public UserEntity.Role getUserRole(String username) {
        return userRepository.findByUsername(username)
                .map(UserEntity::getRole)
                .orElse(null);
    }

    public boolean isUserEnabled(String username) {
        return userRepository.findByUsername(username)
                .map(UserEntity::getEnabled)
                .orElse(false);
    }
}

