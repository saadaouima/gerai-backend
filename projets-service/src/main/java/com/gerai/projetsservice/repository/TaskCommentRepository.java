package com.gerai.projetsservice.repository;

import com.gerai.projetsservice.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    List<TaskComment> findByTask_TaskIdOrderByCreatedAtAsc(Long taskId);
}