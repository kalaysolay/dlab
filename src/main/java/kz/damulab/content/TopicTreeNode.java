package kz.damulab.content;

import java.util.List;

public record TopicTreeNode(
        Long id,
        String code,
        String titleRu,
        String titleKk,
        List<TopicTreeNode> children
) {
}
