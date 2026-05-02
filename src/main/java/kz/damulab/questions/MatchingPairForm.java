package kz.damulab.questions;

public class MatchingPairForm {

    private String leftRu;
    private String leftKk;
    private String rightRu;
    private String rightKk;

    public MatchingPairForm() {
    }

    public MatchingPairForm(String leftRu, String leftKk, String rightRu, String rightKk) {
        this.leftRu = leftRu;
        this.leftKk = leftKk;
        this.rightRu = rightRu;
        this.rightKk = rightKk;
    }

    public String getLeftRu() {
        return leftRu;
    }

    public void setLeftRu(String leftRu) {
        this.leftRu = leftRu;
    }

    public String getLeftKk() {
        return leftKk;
    }

    public void setLeftKk(String leftKk) {
        this.leftKk = leftKk;
    }

    public String getRightRu() {
        return rightRu;
    }

    public void setRightRu(String rightRu) {
        this.rightRu = rightRu;
    }

    public String getRightKk() {
        return rightKk;
    }

    public void setRightKk(String rightKk) {
        this.rightKk = rightKk;
    }
}
