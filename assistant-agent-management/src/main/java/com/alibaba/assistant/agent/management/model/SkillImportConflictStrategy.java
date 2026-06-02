package com.alibaba.assistant.agent.management.model;

/**
 * 同名 skill 导入时的冲突处理策略。
 *
 * <ul>
 *     <li>{@link #REPLACE}：复用已存在经验的 ID，原地更新内容、references、assets、关联工具等</li>
 *     <li>{@link #KEEP_BOTH}：忽略已存在经验，生成全新 ID 写入</li>
 * </ul>
 *
 * <p>当未指定策略（参数为 {@code null}）时，导入流程在检测到同名 REACT 经验后将不落库，
 * 而是把已存在经验信息回填到 {@code SkillPackageImportResult.conflict} 中，由调用方
 * （管理后台前端）提示用户选择处理方式。
 */
public enum SkillImportConflictStrategy {

    REPLACE,

    KEEP_BOTH
}
