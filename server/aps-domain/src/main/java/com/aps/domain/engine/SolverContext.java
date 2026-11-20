package com.aps.domain.engine;

/** 求解上下文骨架（技术方案 §4.1）：快照/取消标志/进度由后续迭代填充。 */
public class SolverContext {

    private Long runId;
    private String mode;
    private volatile boolean cancelFlag;
    private int progress;

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isCancelFlag() {
        return cancelFlag;
    }

    public void setCancelFlag(boolean cancelFlag) {
        this.cancelFlag = cancelFlag;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }
}
