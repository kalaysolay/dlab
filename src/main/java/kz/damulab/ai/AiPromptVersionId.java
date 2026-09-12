package kz.damulab.ai;

import java.io.Serializable;
import java.util.Objects;

/** Составной ключ неизменяемой версии промпта. */
public class AiPromptVersionId implements Serializable {

    private AiPromptCode promptCode;
    private int versionNo;

    public AiPromptVersionId() {
    }

    public AiPromptVersionId(AiPromptCode promptCode, int versionNo) {
        this.promptCode = promptCode;
        this.versionNo = versionNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AiPromptVersionId that)) {
            return false;
        }
        return versionNo == that.versionNo && promptCode == that.promptCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptCode, versionNo);
    }
}
