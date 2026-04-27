package com.alibaba.assistant.agent.management.spi;

import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.management.model.ExperienceCreateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceListQuery;
import com.alibaba.assistant.agent.management.model.ExperienceUpdateRequest;
import com.alibaba.assistant.agent.management.model.ExperienceVO;
import com.alibaba.assistant.agent.management.model.PageResult;

import java.util.List;
import java.util.Map;

public interface ExperienceManagementService {

    PageResult<ExperienceVO> list(ExperienceListQuery query);

    List<ExperienceVO> search(String keyword, ExperienceType type, int topK);

    ExperienceVO getById(String id);

    String create(ExperienceCreateRequest request);

    void update(String id, ExperienceUpdateRequest request);

    void delete(String id);

    Map<ExperienceType, Long> countByType();

    /**
     * 加载指定经验下某个 asset 的完整条目（包含 content/contentRef）。
     * 用于管理后台按需查看 asset 内容。
     *
     * @param id experience id
     * @param path asset 相对路径
     * @return 匹配的 asset，未找到时返回 null
     */
    com.alibaba.assistant.agent.extension.experience.model.AssetEntry loadAsset(String id, String path);

    /**
     * 重新生成指定经验下所有 reference 的 {@code description}（走 H1 / frontmatter /
     * LLM summarizer / 回退文案优先级）。适用于导入时 summarizer 尚未配置、
     * 事后补齐的场景。
     *
     * @param id experience id
     * @return 更新成功的 reference 数量；id 不存在或没有 reference 时返回 0
     */
    int resummarizeReferences(String id);
}
