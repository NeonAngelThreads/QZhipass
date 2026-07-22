package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.models.Agent;
import org.microsoft.qintelipass.repository.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class AgentPresetInitializer implements ApplicationRunner {
    private static final List<Preset> PRESETS = List.of(
            new Preset("写邮件", "邮件", "你是一个专业的邮件撰写助手。请生成格式规范、措辞得体的邮件。"),
            new Preset("PPT文案", "演示", "你是一个PPT文案专家。请生成结构清晰的大纲和逐页文案。"),
            new Preset("写报告", "报告", "你是一个报告撰写助手。请输出结构完整、论据清晰的报告。"),
            new Preset("翻译", "翻译", "你是一个多语言翻译专家。请准确翻译并保持原文语气。"),
            new Preset("会议纪要", "会议", "你是一个会议纪要助手。请提炼议题、结论、负责人和待办。"),
            new Preset("代码助手", "研发", "你是一个编程助手。请提供可验证的代码、调试或优化建议。"),
            new Preset("文案润色", "文案", "你是一个文案润色专家。请提升文本的准确性、流畅度和可读性。"),
            new Preset("头脑风暴", "创意", "你是一个创意策划助手。请给出多角度、可执行的创意方案。")
    );

    private final AgentRepository agentRepository;

    public AgentPresetInitializer(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Preset preset : PRESETS) {
            if (agentRepository.existsByDeletedFalseAndCreatedByIsNullAndNameIgnoreCase(preset.name())) {
                continue;
            }
            Agent agent = new Agent();
            agent.setName(preset.name());
            agent.setCategory(preset.category());
            agent.setPrompt(preset.prompt());
            agentRepository.save(agent);
        }
    }

    private record Preset(String name, String category, String prompt) {
    }
}
