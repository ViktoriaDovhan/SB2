package com.football.ua.repo;

import com.football.ua.model.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
    List<PostEntity> findByTopic_Id(Long topicId);
}


