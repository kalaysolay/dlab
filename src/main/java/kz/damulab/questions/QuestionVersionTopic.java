package kz.damulab.questions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import kz.damulab.content.Topic;

@Entity
@Table(
        name = "question_version_topics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"question_version_id", "topic_id"})
)
public class QuestionVersionTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion questionVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryTopic;

    protected QuestionVersionTopic() {
    }

    public QuestionVersionTopic(QuestionVersion questionVersion, Topic topic, boolean primaryTopic) {
        this.questionVersion = questionVersion;
        this.topic = topic;
        this.primaryTopic = primaryTopic;
    }

    public Long getId() {
        return id;
    }

    public QuestionVersion getQuestionVersion() {
        return questionVersion;
    }

    public Topic getTopic() {
        return topic;
    }

    public boolean isPrimaryTopic() {
        return primaryTopic;
    }
}
