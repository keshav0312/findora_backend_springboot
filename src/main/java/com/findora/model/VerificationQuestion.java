package com.findora.model;

/** A question the reporter asks to verify ownership; `answer` is only ever shown to the report owner. */
public class VerificationQuestion {
    private String question;
    private String answer;

    public VerificationQuestion() {}

    public VerificationQuestion(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
